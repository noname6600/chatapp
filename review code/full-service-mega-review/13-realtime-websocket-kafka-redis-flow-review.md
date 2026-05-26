# Realtime, WebSocket, Kafka, Redis Flow Review

**Focus**: Event flow through all asynchronous channels  

---

## WebSocket Client → Server → Broadcast

### Scenario 1: Message Sent (Chat)

**Flow**:
1. Client sends message via WebSocket
2. Chat service processes, saves to DB
3. Publishes CHAT_MESSAGE_SENT to Kafka
4. Notification service consumes → pushes via WebSocket
5. Chat service also broadcasts to room participants

**Architecture Issues**:
- ❓ Do chat-service AND notification-service both push to client?
- ❓ Is ordering guaranteed?

**Expected**: Single push per message (no duplicates)

**Fix**: Chat service broadcasts to room; notification service sends to individual user (if different UX)

**Scope**: SERVICE-ONLY (verify in code)

---

### Scenario 2: User Goes Online (Presence)

**Flow**:
1. WebSocket connect on presence-service
2. User registered in Redis with TTL
3. Presence service publishes USER_ONLINE event to Redis pub/sub
4. All connected clients subscribed to room → receive update
5. Presence service also publishes USER_ONLINE to Kafka
6. Offline subscribers eventually notified (email, app push)

**Consistency**: ✅ Correct (ephemeral + durable separation)

---

### Scenario 3: Typing Indicator

**Flow**:
1. Client sends `{type: "typing"}` via WebSocket
2. Presence service caches typing state in Redis
3. Broadcasts to room via Redis pub/sub
4. TTL expires (5 sec) → other clients show "typing ended"

**Verification**:
- Does typing indicator have TTL? (Should auto-expire)
- Is it deduplicated? (Don't flood with same typing event)

**Fix if missing**: Add dedup window

---

## Kafka Event Propagation

### Chat Message Sent → Multiple Consumers

**Producers**:
- chat-service publishes CHAT_MESSAGE_SENT

**Consumers** (should be):
1. **notification-service**: Create notification for recipient
2. **presence-service** (optional): Update last activity timestamp
3. **realtime-edge-service** (optional): Broadcast if client connected

**Risk**: Duplicate listeners in single service → partition race

**Verification**: Each service has ONE listener per event type, or consolidated listener

---

### Event Ordering

**Issue**: Kafka partitions by key → if key is `roomId`, all messages in room are ordered ✅

**Verification**: Ensure Kafka producer uses correct partition key
```java
// ✅ Good
kafkaTemplate.send(topic, roomId, chatMessage);

// ❌ Bad (default partition, no ordering)
kafkaTemplate.send(topic, chatMessage);
```

---

## Redis Pub/Sub (Ephemeral Events)

### Who Publishes to Redis?
- **presence-service**: Online/offline, typing, room updates
- **chat-service**: Message edits/deletions (bonus, not required)
- **notification-service** (optional): Real-time notification updates

### Who Subscribes?
- **Realtime-edge** or individual service WebSocket handlers
- **All instances** of each service (multi-instance broadcast)

### Potential Issues
- ❓ Are subscriptions lost if service instance restarts?
- ❓ Is there a catchup mechanism?

**Expected**: Redis pub/sub for real-time only (no persistence), combined with Kafka for durability

---

## Multi-Instance Consistency

### Scenario: Two Chat Service Instances

**Instance-1**: Receives message, publishes Kafka event  
**Instance-2**: Reads from Kafka, creates notification cache

**Risk**: Cache inconsistency if both write

**Fix**: Use Redis for distributed cache (both instances talk to same Redis)

---

## Event Envelope Structure

### Kafka EventMetadata

**Should contain**:
- `eventId` (UUID, for deduplication)
- `eventType` (string, for routing)
- `timestamp` (when event occurred)
- `correlationId` (for tracing)
- `traceId` (for distributed tracing)
- `userId` (if applicable)

**Verification**: Check `common-events/EventMetadata.java`

**Issue if Missing**: 
- Deduplication impossible (can't track processed events)
- Tracing broken (can't correlate requests end-to-end)
- Timing issues (can't sort by actual occurrence time)

---

## Error Handling in Event Consumers

### Kafka Listener Fails
```java
@KafkaListener(...)
public void handleEvent(Event event) throws Exception {
    try {
        process(event);
    } catch (RecoverableException e) {
        // Retry logic (Kafka retry topic)
    } catch (NonRecoverableException e) {
        // Send to DLT (Dead Letter Topic)
    }
}
```

**Verification**: Each listener has try-catch + error handling

**Missing**: DefaultErrorHandler not configured (no DLT)

---

## Idempotency & Deduplication

### Kafka Listener Idempotency

**Problem**: If listener processes event twice (replay), side effects duplicate

**Solution**: Store processed eventIds in Redis or DB
```java
@KafkaListener(...)
public void handleEvent(Event event) {
    String eventId = event.getMetadata().getEventId();
    
    // Check if already processed
    if (redisTemplate.hasKey("processed:" + eventId)) {
        return;  // Already handled
    }
    
    process(event);
    
    // Mark as processed (TTL = event retention)
    redisTemplate.setEx("processed:" + eventId, "1", 7, TimeUnit.DAYS);
}
```

**Verification**: Idempotency check present in listeners

---

## Event Payload Size & Information Leakage

### Issue: Event Payload Too Large

**Example**:
```json
{
  "messageId": "...",
  "text": "Hello",
  "attachments": [...],  // Full file URLs
  "sender": {
    "id": "...",
    "username": "...",
    "email": "...",
    "profilePhoto": "...",  // Full user data
    "settings": {...}        // Leaks user settings
  }
}
```

**Risk**: 
1. Large payload = slower throughput
2. Leaks internal data structures
3. Harder to evolve (breaking changes)

**Fix**: Minimal payload
```json
{
  "messageId": "...",
  "roomId": "...",
  "senderId": "...",
  "text": "Hello",
  "timestamp": "..."
}
```

**Consumer can fetch full details** if needed (REST call or cache lookup)

**Verification**: Event payloads are lean, not full entity copies

---

## Message Delivery Guarantees

| System | Guarantee | Behavior |
|--------|-----------|----------|
| Kafka | At-least-once | May deliver duplicate |
| Redis pub/sub | At-most-once | May lose if no subscribers |
| WebSocket | At-least-once | Client may reconnect |

**Expected**: Producers account for their channel's guarantee

---

## Summary

| Issue | Severity | Scope | Action |
|-------|----------|-------|--------|
| Duplicate WebSocket broadcasts | MEDIUM | SERVICE-ONLY | Audit services |
| Missing partition keys | MEDIUM | SERVICE-ONLY | Verify Kafka producers |
| Event dedup not implemented | HIGH | SERVICE-ONLY | Add idempotency check |
| No error handler on Kafka | MEDIUM | COMMON-OPTIONAL | Configure DLT |
| Stale Redis cache | MEDIUM | SERVICE-ONLY | Add invalidation |
| Event payloads too large | LOW | SERVICE-ONLY | Trim to minimal info |

---

**Next**: Read 14-security-review.md
