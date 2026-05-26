# Redis Architecture Review — chatappBE
> Deep-dive audit | Date: 2026-05-20 | Reviewer: multi-agent analysis

---

## 1. Infrastructure Configuration

### REDIS-01 — CRITICAL | Single Redis Instance — Complete SPOF

**Affected:** `docker-compose.yml`

```yaml
redis:
  image: redis:7.4.2-alpine
  ports:
    - "6379:6379"
  # No maxmemory, no persistence config, no replica, no Sentinel
```

Redis is a single point of failure for:
- WebSocket session registry (all active connections lost on restart)
- Presence state (all users appear offline instantly)
- Rate limiting counters (reset on restart — burst allowed)
- Notification deduplication (all guards reset — duplicate notifications sent)
- Room sequence counters (gap or reset in message ordering)
- Pub/sub delivery (all in-flight messages lost)

**Fix:** Deploy Redis Sentinel (3 nodes) for HA, or Redis Cluster for horizontal scaling. Minimum for production: 1 primary + 1 replica with Sentinel.

---

### REDIS-02 — CRITICAL | No maxmemory Policy Configured

**Affected:** `docker-compose.yml` — Redis container has no memory limit or `maxmemory` directive.

Without `maxmemory`, Redis will consume all available host memory. When host memory is exhausted:
- The OS OOM killer selects a process to kill (usually Redis itself)
- All Redis state is lost
- All connected services lose their connection and fail

**Fix:**
```yaml
redis:
  command: redis-server --maxmemory 512mb --maxmemory-policy allkeys-lru
```

Select the appropriate eviction policy based on data criticality:
- `allkeys-lru` — evict least recently used keys (acceptable for cache)
- `noeviction` — return errors when memory full (required for session state)

For session/presence data, `noeviction` is required with explicit TTL management.

---

### REDIS-03 — HIGH | No Connection Pooling Configured

**Affected:** All services using `spring-boot-starter-data-redis`

Spring Boot's Lettuce client defaults to a **single shared connection** with connection multiplexing. For services that perform many concurrent Redis operations (realtime-edge, notification-service, presence-service), this means:

- All Redis commands serialize over one TCP connection
- A slow command (SMEMBERS on large set) blocks all other commands
- No backpressure: the Lettuce queue grows unboundedly under load
- Connection reset/timeout affects all concurrent operations

**Fix:** Configure Lettuce connection pool in all services:
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
          max-wait: 100ms
```

---

## 2. Redis Data Model

### Key Pattern Inventory

| Key Pattern | Owner | TTL | Type | Issue |
|-------------|-------|-----|------|-------|
| `presence::user:{userId}:connections` | presence-service | ❌ None | HASH | Memory leak |
| `presence::user:{userId}:online` | presence-service | ❌ None | STRING | Memory leak |
| `presence::user:{userId}:rooms` | presence-service | ❌ None | SET | Memory leak |
| `presence:{userId}` | presence-service | 60s | STRING | Keyspace trigger key |
| `session:registry:{sessionId}` | realtime-edge | ❌ None | HASH | Memory leak |
| `session:user:{userId}:sessions` | realtime-edge | ❌ None | SET | Memory leak |
| `room:seq:{roomId}` | chat-service | ❌ None | STRING | Unbounded growth |
| `notification:dedup:{eventId}` | notification-service | 5min | STRING | In-memory fallback |
| `ticket:{ticketId}` | realtime-edge | 30s | STRING | Full JWT stored |
| `rate_limit:{ip}` | gateway | ~1s | ZSET (token bucket) | Correct |
| `notification:{userId}` | notification-service | N/A | PUB/SUB channel | Fire-and-forget |

---

### REDIS-04 — CRITICAL | Presence Keys Have No TTL — Unbounded Memory Leak

**Affected file:** `presence-service/src/main/java/com/chatweb/presence/state/redis/RedisPresenceEphemeralStateStore.java`

```java
// addConnection()
redisTemplate.opsForHash().put("presence::user:" + userId + ":connections", connectionId, "1");
// No TTL set

// addRoom()
redisTemplate.opsForSet().add("presence::user:" + userId + ":rooms", roomId);
// No TTL set
```

The TTL-based detection works via `presence:{userId}` (60s TTL) triggering keyspace notification. But the data keys (`connections`, `online`, `rooms`) have no TTL. If:
- A user disconnects abnormally (network drop, server crash)
- The `afterConnectionClosed()` hook does not fire
- The keyspace expiry fires and calls `removeUser()`

...but `removeUser()` itself fails (Redis error, service restart), the presence sub-keys remain forever. Over time, Redis accumulates stale presence data for every user who ever connected abnormally.

**Quantification:** 100K users × 3 keys per user = 300K stale keys. At ~200 bytes per key: ~60 MB of stale presence data with no cleanup mechanism.

**Fix:** Set TTL on presence data keys to `connection_timeout + buffer` (e.g., 90 seconds). Refresh TTL on each heartbeat/activity.

---

### REDIS-05 — CRITICAL | Session Registry Keys Have No TTL — Memory Leak

**Affected file:** `realtime-edge-service/.../connection/RedisRealtimeSessionRegistry.java`

```java
redisTemplate.opsForHash().put("session:registry:" + sessionId, "userId", userId.toString());
redisTemplate.opsForSet().add("session:user:" + userId + ":sessions", sessionId);
// No TTL on either key
```

Session data is only removed when `removeSession()` is explicitly called (in `afterConnectionClosed()`). If the realtime-edge service crashes:
- WebSocket connections close without `afterConnectionClosed()` firing on the crashed instance
- `session:registry:{sessionId}` and `session:user:{userId}:sessions` remain in Redis forever
- The maintenance job does clean zombie sessions from the `WebSocketSessionStore` but the Redis-backed `RealtimeSessionRegistry` is not cleaned

**Fix:** Set TTL on session registry keys to the maximum WebSocket idle timeout (e.g., 120 seconds). Refresh TTL on each received frame.

---

### REDIS-06 — HIGH | Full JWT Token Stored in Redis Ticket

**Affected file:** `realtime-edge-service/.../adapter/in/websocket/JwtHandshakeInterceptor.java`

```java
// Ticket value stored in Redis:
String ticketValue = userId + "|" + accessToken;  // Full JWT concatenated
redisTemplate.opsForValue().set("ticket:" + ticket, ticketValue, 30, TimeUnit.SECONDS);
```

Storing the full JWT in Redis means:
- Any actor with Redis read access (DBA, backup operator, read replica access) can extract valid JWTs
- Redis memory dumps contain live session tokens
- JWT rotation (key rotation) does not invalidate Redis-cached tokens

The ticket mechanism exists to avoid passing JWTs as URL query parameters (correct concern) but the fix introduces a different vulnerability.

**Fix:** Store only `userId` in the ticket. Require the client to send the JWT in the `Authorization: Bearer` header during the WebSocket upgrade request. Validate the JWT at upgrade time using the existing `JwtDecoder`.

---

## 3. Pub/Sub Reliability

### REDIS-07 — CRITICAL | Redis Pub/Sub Is Fire-and-Forget — No Delivery Guarantee

**Affected:** All cross-instance WebSocket message delivery via `notification:{userId}` channel

Redis Pub/Sub has no persistence, no acknowledgment, and no consumer groups:
- If realtime-edge is not subscribed to the channel at the moment of PUBLISH, the message is **lost permanently**
- If Redis restarts while a message is in-flight, it is **lost permanently**
- If the realtime-edge subscriber is slow (busy with synchronized WS sends), Redis buffers up to `client-output-buffer-limit pubsub` then **disconnects the subscriber**
- No replay is possible — there is no offset tracking

**Quantification of risk:**
- Client reconnect window: 0-5 seconds
- During this window, any pub/sub message is lost
- Users see message gaps with no indication of missed messages

**Fix:** Replace Redis Pub/Sub with a persistent delivery mechanism:
1. **Short-term:** Use Redis Streams (`XADD`/`XREAD`/`XACK`) with consumer groups — provides ack-based delivery and message history for replay
2. **Long-term:** Rely on Kafka for cross-instance delivery (realtime-edge as Kafka consumer per instance); Redis only for local routing

---

### REDIS-08 — HIGH | Thread.sleep() on Redis Pub/Sub Listener Thread

**Affected file:** `realtime-edge-service/.../dispatch/EdgeDeliveryHandoffPublisher.java`

```java
@Override
public void onMessage(Message message, byte[] pattern) {
    // ...
    Thread.sleep(50);  // Artificial delay on Lettuce IO thread
    deliver(payload);
}
```

The Redis pub/sub listener runs on Lettuce's IO thread. Calling `Thread.sleep()` on this thread:
1. Blocks ALL Redis pub/sub message delivery for 50ms per message
2. Causes pub/sub message backlog to grow
3. Under load (1000 msg/sec), this thread is sleeping for `1000 × 50ms = 50 seconds` worth of blocking per second — effectively freezing the listener

**Fix:** Remove `Thread.sleep()`. If a delay is needed for ordering, use an async queue with a short delay via `ScheduledExecutorService`.

---

### REDIS-09 — HIGH | Keyspace Notification Prefix Collision in Presence Service

**Affected file:** `presence-service/.../redis/PresenceKeyExpiredListener.java`

The presence TTL trigger key has prefix `presence:` (single colon). The presence data keys have prefix `presence::` (double colon). The keyspace notification listener pattern is:

```java
// Listens for: __keyevent@0__:expired events where key starts with "presence:"
```

The data keys (`presence::user:{userId}:connections`) also match this prefix pattern. When a data key expires (if TTL were added), it would trigger the `PresenceKeyExpiredListener`, which would attempt to parse the key as a user ID — causing NPE or incorrect offline signaling.

Current state: data keys have no TTL, so no false triggers. But if REDIS-04 is fixed (adding TTL to data keys), this becomes an active bug.

**Fix:** Use a non-overlapping prefix for the TTL trigger key, e.g., `presence:heartbeat:{userId}` vs `presence:data:{userId}:*`.

---

## 4. SCAN and Performance

### REDIS-10 — HIGH | SCAN O(N) Operations Block Redis

**Affected file:** `realtime-edge-service/.../connection/RedisRealtimeSessionRegistry.java`

```java
// getActiveSessionCount():
Set<String> keys = redisTemplate.keys("session:registry:*");
return keys.size();
```

`KEYS *` is a blocking O(N) operation on all Redis keys. With 100K active sessions, this scans 100K+ keys and blocks Redis for potentially hundreds of milliseconds, stalling ALL other Redis commands on the same instance.

Even if replaced with `SCAN`, iterating 100K keys requires multiple round trips and is still O(N) total.

**Fix:** Maintain a separate counter key `session:count` that is atomically incremented/decremented with INCR/DECR on session add/remove.

---

### REDIS-11 — HIGH | N+1 Redis Calls Per Message Delivery

**Affected:** `realtime-edge-service/.../delivery/ChatRealtimeDeliveryService.java`

For a room with N active subscribers:
1. `SMEMBERS session:user:{userId}:sessions` — per user (1 Redis call)
2. For each sessionId: `HGETALL session:registry:{sessionId}` — (N Redis calls)
3. For each session, find the local WebSocket session
4. `session.sendMessage()` — local, no Redis

For a room with 100 members: 100 + (100 × avg_sessions_per_user) Redis calls per message.

**Fix:** Use a Lua script to batch-fetch all session data in a single round trip, or maintain a room→sessions index that returns all session IDs in one `SMEMBERS` call.

---

### REDIS-12 — MEDIUM | getOnlineUsers Loads Full Set Into Memory

**Affected file:** `presence-service/src/main/java/com/chatweb/presence/state/redis/RedisPresenceEphemeralStateStore.java`

```java
public Set<String> getOnlineUsers() {
    return redisTemplate.opsForSet().members("presence:online:all");
    // Returns ALL online users — could be millions
}
```

This is called by `GET /presence/global` (SEC-20 from security review). In a large deployment, this could return millions of user IDs, consuming gigabytes of memory in the response serialization.

**Fix:** Paginate using `SSCAN`. Restrict endpoint to return only friends/room-mates.

---

## 5. Non-Atomic Multi-Step Operations

### REDIS-13 — HIGH | Session Registration Is Not Atomic

**Affected file:** `realtime-edge-service/.../connection/RedisRealtimeSessionRegistry.java`

Session registration writes two separate keys:
```java
redisTemplate.opsForHash().put("session:registry:" + sessionId, ...);  // Step 1
redisTemplate.opsForSet().add("session:user:" + userId + ":sessions", sessionId);  // Step 2
```

If the service crashes between step 1 and step 2:
- The session is in the registry but not in the user's session set
- The user's sessions can never be found by user-based lookup
- The orphaned registry key leaks forever

**Fix:** Use Redis `MULTI`/`EXEC` (transaction) or a Lua script to perform both writes atomically.

---

### REDIS-14 — HIGH | Sequence Counter Race on First Message

**Affected file:** `chat-service/.../domain/service/impl/RedisRoomSequenceService.java`

```java
public long nextSeq(UUID roomId) {
    String key = "room:seq:" + roomId;
    Long seq = redisTemplate.opsForValue().increment(key);
    // ...
}
```

`INCR` is atomic. But if two concurrent first messages arrive before the key exists:
- Both `INCR` calls succeed: first returns 1, second returns 2 — correct behavior
- However, if Redis evicts the key (under memory pressure with `allkeys-lru`), the counter resets to 0
- Sequence numbers start over — clients receiving sequence 1 after sequence 10,000 may incorrectly reconstruct message history

**Fix:** Set TTL far in the future (never expire) or use `noeviction` policy for sequence counter keys. Consider persisting sequence in DB as authoritative source.

---

## 6. MessageCacheService Dead Code

### REDIS-15 — MEDIUM | MessageCacheService Is Not a Spring Bean

**Affected file:** `chat-service/src/main/java/com/chatweb/chat/infrastructure/cache/MessageCacheService.java`

```java
// Missing @Service or @Component annotation
public class MessageCacheService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    // ...
}
```

Without a Spring stereotype annotation, this class:
- Is never instantiated by Spring
- Is never injected anywhere
- The `@Autowired` fields are never populated
- All caching logic is dead code

**Fix:** Either add `@Service` and inject it into the appropriate repository, or delete the class entirely.

---

## 7. Deduplication Guard Gaps

### REDIS-16 — HIGH | NotificationEventDedupeGuard Null EventId Bypasses Dedup

**Affected file:** `notification-service/.../infrastructure/kafka/NotificationEventDedupeGuard.java`

```java
public boolean isDuplicate(String eventId) {
    if (eventId == null) return false;  // null bypasses dedup entirely
    // ...
}
```

If any Kafka event arrives with a null `eventId` (malformed event, schema evolution, replay), deduplication is bypassed and the notification is sent multiple times. For registration emails, this means multiple verification emails sent to the same user.

**Fix:** Treat null eventId as a deduplication failure — either reject the message (send to DLQ) or assign a synthetic ID based on event content hash.

---

### REDIS-17 — MEDIUM | In-Memory Dedup Loses State on Restart

**Affected:** `notification-service/.../infrastructure/kafka/NotificationEventDedupeGuard.java`

The deduplication guard uses a 5-minute window in Redis. On restart, Redis state is preserved. However, the in-memory `ConcurrentHashMap` fallback (used when Redis is unavailable) resets on restart — allowing duplicates during Redis reconnect windows.

If notification-service restarts and Redis is temporarily unreachable, the in-memory guard is used. Once Redis reconnects, the Redis guard resumes — but any events deduplicated in-memory were not recorded in Redis, and vice versa.

**Fix:** Fail closed: if Redis is unavailable, refuse to process events rather than falling back to unreliable in-memory dedup. Alert and let the consumer lag accumulate until Redis recovers.

---

## 8. Summary Table

| ID | Severity | Category | One-line description |
|----|----------|----------|---------------------|
| REDIS-01 | CRITICAL | Infra | Single Redis instance — complete SPOF |
| REDIS-04 | CRITICAL | Memory | Presence keys have no TTL — unbounded memory leak |
| REDIS-05 | CRITICAL | Memory | Session registry keys have no TTL — memory leak on crash |
| REDIS-07 | CRITICAL | Reliability | Redis pub/sub fire-and-forget — no delivery guarantee |
| REDIS-02 | CRITICAL | Infra | No maxmemory policy — OOM crash risk |
| REDIS-06 | HIGH | Security | Full JWT stored in Redis ticket |
| REDIS-08 | HIGH | Performance | Thread.sleep() on Redis pub/sub listener thread |
| REDIS-09 | HIGH | Correctness | Keyspace notification prefix collision |
| REDIS-10 | HIGH | Performance | KEYS * O(N) scan blocks Redis |
| REDIS-11 | HIGH | Performance | N+1 Redis calls per message delivery |
| REDIS-13 | HIGH | Correctness | Session registration not atomic |
| REDIS-14 | HIGH | Correctness | Sequence counter race on first message |
| REDIS-16 | HIGH | Correctness | Null eventId bypasses deduplication |
| REDIS-03 | HIGH | Performance | No connection pooling — single shared connection |
| REDIS-12 | MEDIUM | Scalability | getOnlineUsers loads full set into memory |
| REDIS-15 | MEDIUM | Dead code | MessageCacheService not a Spring bean |
| REDIS-17 | MEDIUM | Reliability | In-memory dedup loses state on restart |
