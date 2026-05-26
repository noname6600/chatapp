# Chat Service Review

**Service**: Chat & Messaging (Port 8083)  
**Responsibility**: Rooms, messages, reactions, pins, attachments  
**Tech Stack**: Spring Boot, Spring Data JPA, Postgres, Kafka, Redis pub/sub, WebSocket  

---

## Critical Issues

| Issue | Severity | Location | Fix Scope |
|-------|----------|----------|-----------|
| N+1 queries on Room fetch | HIGH | RoomRepository | SERVICE-ONLY |
| Kafka listener duplicate | BLOCKER | ChatMessageKafkaConsumer | SERVICE-ONLY |
| Message seq not unique | HIGH | ChatMessage entity | SERVICE-ONLY |
| WebSocket lifecycle bug | MEDIUM | ChatWebSocketHandler | SERVICE-ONLY |
| Event publish timing | MEDIUM | ChatMessageService | SERVICE-ONLY |

---

## 🔴 BLOCKER: Duplicate Kafka Listeners

**Problem**: Two `@KafkaListener` on same topic/group → partition race condition → silent event loss

**Location**: `chat-service/src/main/java/com/example/chat/adapter/in/kafka/`

**Look for**:
```java
@KafkaListener(topics = "chat.messages", groupId = "chat-service")
public void onMessageCreated(ChatMessageCreatedEvent event) { }

@KafkaListener(topics = "chat.messages", groupId = "chat-service")  // ❌ DUPLICATE
public void onMessageReceived(ChatMessageCreatedEvent event) { }
```

**Fix**: Rename/consolidate to single listener  
**Risk**: LOW  

---

## 🟠 HIGH: Message Sequence Not Guaranteed Unique

**Issue**: If two messages sent concurrently in same room, seq values may collide

**Location**: `ChatMessage` entity, seq generation in `ChatMessageService`

**Example**:
1. User A sends message → seq=5
2. User B sends message concurrently → also seq=5
3. Pagination breaks (both messages return with seq=5)

**Fix Options**:
- Use database sequence with `@GeneratedValue(strategy = GenerationType.SEQUENCE)`
- Use atomic counter in service layer
- Use Postgres `SERIAL` column with unique constraint

**Scope**: SERVICE-ONLY  
**Risk**: MEDIUM (may require data migration)

---

## 🔴 BLOCKER: Topic Constants Not Defined

Services reference `KafkaTopics.CHAT_MESSAGE_SENT` which doesn't exist in `common-events`

**Fix**: Add to KafkaTopics.java:
```java
public static final String CHAT_MESSAGE_SENT = "chat.message.sent";
public static final String CHAT_MESSAGE_EDITED = "chat.message.edited";
public static final String CHAT_MESSAGE_DELETED = "chat.message.deleted";
```

**Scope**: COMMON-REQUIRED  
**Risk**: LOW

---

## 🟠 MEDIUM: WebSocket Lifecycle Bug

**Similar to presence-service** (see 07-presence-service-review.md)

**Issue**: Users may remain "connected" in session registry after disconnect

**Location**: `ChatWebSocketHandler.afterConnectionClosed()`

**Fix**: Ensure cleanup order: unregister from rooms → remove from session registry

---

## 🟠 MEDIUM: Event Publish Not After Commit

**Issue**: Message Kafka event published before transaction commit

**Scenario**:
1. Save message to DB (in transaction)
2. Publish event to Kafka
3. Transaction rolls back
4. Kafka event already sent → consumer reads non-existent message

**Location**: `ChatMessageService.sendMessage()` method

**Fix**: Use TransactionSynchronization or `@TransactionalEventListener`

---

## Verification

```bash
./gradlew.bat :chat-service:compileJava --no-daemon
./gradlew.bat :chat-service:test --no-daemon

# Test message flow
docker-compose up chat-service -d
curl -X POST http://localhost:8083/api/chat/rooms \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name": "test-room"}'
```

---

**Priority**: Fix BLOCKER issues first (duplicate listeners, topic constants)  
**Estimated Effort**: 4-6 hours  

**Next**: Read 07-presence-service-review.md
