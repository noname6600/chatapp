# Scalability Review — chatappBE
> Deep-dive audit | Date: 2026-05-20 | Reviewer: multi-agent analysis

---

## 1. Horizontal Scaling Matrix

| Service | Stateless? | Can Scale? | Blocker | Recommendation |
|---------|-----------|-----------|---------|---------------|
| gateway-service | Yes | ✅ Yes | None | Multiple instances behind LB |
| auth-service | No (DB) | ✅ Yes | HikariCP pool per instance | Multiple instances, shared PostgreSQL |
| user-service | No (DB) | ✅ Yes | None | Multiple instances |
| chat-service | No (DB, Redis seq) | ✅ Yes | Sequence counter single key | Multiple instances; fix seq counter |
| friendship-service | No (DB) | ✅ Yes | Race on concurrent request | Add optimistic lock |
| notification-service | No (Kafka consumer) | ✅ Yes | DLQ monitoring | Multiple instances with partition assignment |
| presence-service | No (Redis) | ✅ Yes | None | Multiple instances |
| realtime-edge-service | No (WS sessions) | ⚠️ Partial | See SCALE-01 | Requires Redis session registry mode |
| upload-service | Yes | ✅ Yes | None | Multiple instances |

---

## 2. WebSocket Scaling Analysis

### SCALE-01 — CRITICAL | realtime-edge-service Session Affinity Required Without Redis Registry

**Affected file:** `realtime-edge-service/src/main/resources/application.yaml`

```yaml
realtime:
  session-registry:
    mode: ${REALTIME_SESSION_REGISTRY_MODE:redis}
```

The application supports two session registry modes:
- `in-memory`: `InMemoryRealtimeSessionRegistry` — only local sessions visible
- `redis`: `RedisRealtimeSessionRegistry` — all sessions across all instances visible

**Critical:** The Java annotation has `matchIfMissing = true` on the in-memory bean:
```java
@ConditionalOnProperty(name = "...", havingValue = "in-memory", matchIfMissing = true)
```

If `REALTIME_SESSION_REGISTRY_MODE` is not set in the environment, the **in-memory** registry activates even though `application.yaml` defaults to `redis`. This is a dual-default conflict.

**With in-memory registry in multi-instance deployment:**
- Instance A has UserX's WebSocket connection
- Instance B receives a Kafka event for UserX
- Instance B cannot find UserX's session (it's on Instance A)
- Message silently dropped
- UserX misses the real-time event

**Fix:** Remove `matchIfMissing = true` from the in-memory bean. Document that `REALTIME_SESSION_REGISTRY_MODE=redis` is mandatory in multi-instance deployment.

---

### SCALE-02 — HIGH | WebSocket Scalability Matrix

| Concurrent Connections | In-Memory Mode | Redis Mode | Bottleneck |
|----------------------|----------------|------------|-----------|
| 10,000 | ✅ Works (single instance) | ✅ Works | Heap memory (~1 GB) |
| 100,000 | ❌ Single host max | ⚠️ Needs 3-4 instances | Redis SCAN O(N), session ops N+1 |
| 1,000,000 | ❌ Impossible single host | ❌ Redis bottleneck | Session registry ops become O(N), Redis SCAN blocks |

**Memory estimate per WebSocket session:**
- Spring WebSocket session object: ~8 KB
- RealtimeSession (subscriptions, metadata): ~2 KB
- Redis session hash: ~500 bytes
- Total: ~10-11 KB per active session
- 100K sessions ≈ 1.1 GB heap + 50 MB Redis

**Thread consumption per WebSocket session:**
- Tomcat uses async I/O — WebSocket sessions do NOT consume a thread while idle
- However, each `sendMessage()` call acquires the synchronized lock on the session
- 100K sessions with 10 msg/sec each = 1M synchronized ops/sec — will contend heavily

---

### SCALE-03 — HIGH | synchronized(webSocketSession) Causes Head-of-Line Blocking

**Affected file:** `realtime-edge-service/.../delivery/ChatRealtimeDeliveryService.java`

```java
synchronized (webSocketSession) {
    webSocketSession.sendMessage(new TextMessage(payload));
}
```

WebSocket RFC requires messages to be sent in order, but `synchronized` on the session object means:
- All delivery goroutines for UserX's session queue behind the lock
- A slow receiver (slow network, large message) blocks all subsequent deliveries for that user
- For a user in 10 rooms with 100 members each: if that user's connection is slow, 1000 threads wait

**Quantification:** At 10K concurrent sessions with P99 send latency of 50ms, average lock wait time = (50ms × concurrency_factor). With 100 concurrent delivery threads per instance, all 100 threads can be blocked by 100 slow connections.

**Fix:** Use a per-session write queue (LinkedBlockingQueue or Reactor Flux) with back-pressure. Send asynchronously; disconnect the session if the queue exceeds a depth threshold (slow consumer circuit breaker).

---

## 3. Message Fan-out Scalability

### SCALE-04 — CRITICAL | Message Delivery Fan-out Is O(N×M)

**Flow:** For a room with N members, each having M sessions:

```
Kafka event received by realtime-edge
  → For each of N room subscribers:
      → SMEMBERS session:user:{userId}:sessions  [1 Redis call]
      → For each of M sessions:
          → HGETALL session:registry:{sessionId}  [M Redis calls]
          → sendMessage() [1 WS call]
  → Total Redis calls: N + (N × M)
```

For a room with 1,000 members averaging 1.5 sessions each:
- Redis calls: 1,000 + 1,500 = 2,500 Redis calls per message
- At 100 messages/second in this room: 250,000 Redis calls/second

Redis can handle ~500K ops/sec on modern hardware, but this is for a SINGLE room. With 1,000 concurrent rooms at similar activity:
- 250M Redis calls/second — impossible for a single Redis instance

**Fix:** 
1. Batch-fetch session data using Lua scripts (1 round trip per batch)
2. Maintain a `room:{roomId}:sessions` index in Redis for direct room-to-session mapping
3. For large rooms (>100 members), switch to broadcast via Redis Pub/Sub per-room channel

---

### SCALE-05 — HIGH | Notification Fan-out Is Unbounded

**Affected:** `notification-service/src/main/java/com/chatweb/notification/service/impl/NotificationCommandService.java`

For every message sent to a room with N members:
1. notification-service creates N notification records (one per member)
2. notification-service saves all N records to PostgreSQL in a loop
3. For each of N members, notification-service publishes to Redis pub/sub

For a room with 10,000 members:
- 10,000 DB inserts per message
- 10,000 Redis PUBLISH calls per message
- At 10 messages/second: 100,000 DB inserts/second, 100,000 Redis PUBLISHes/second

No cap, no rate limiting, no batching. This is an architectural limitation of the current design.

**Fix:** For large rooms, do not create per-member notification records. Instead:
1. Store the message as a room-level event
2. Each client fetches unread counts lazily when reconnecting
3. Only create individual notifications for @mentions and DMs

---

### SCALE-06 — HIGH | Presence Fan-out Unbounded Subscriber Lists

**Affected:** presence-service

When a user goes online/offline, all their friends and room-mates must be notified. The notification subscriber list is not bounded.

For a user with 5,000 friends all online:
- 1 presence change → 5,000 WebSocket pushes
- Each push requires Redis pub/sub publish

No fan-out optimization exists (no connection subscription limits, no lazy polling).

**Fix:** Cap friend lists at a reasonable maximum (500). For large social graphs, use poll-based presence instead of push.

---

## 4. Database Scalability

### SCALE-07 — HIGH | Single PostgreSQL Instance — No Read Scaling

All services share a single PostgreSQL instance (via separate databases/schemas). No read replicas configured.

**Write bottlenecks:**
- `chat-service`: every message is an insert (append-only — good)
- `notification-service`: N inserts per message (see SCALE-05)
- `presence-service`: Redis-only (good)

**Read bottlenecks:**
- Message history queries: `SELECT ... WHERE room_id = ? ORDER BY seq DESC LIMIT 50` — needs index on `(room_id, seq)`. Not verified in schema.
- Notification inbox: `SELECT ... WHERE user_id = ? ORDER BY created_at DESC` — needs index.

**Fix:** Add read replicas. Route read queries to replicas using routing DataSource. Add verified indexes for all query patterns.

---

### SCALE-08 — MEDIUM | HikariCP Connection Pool Misconfigured

**Affected:** `notification-service/src/main/resources/application.yaml`

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
  datasource:           # ← DUPLICATE KEY in YAML
    hikari:
      connection-timeout: 30000
```

YAML does not allow duplicate keys at the same level. Most YAML parsers use the last value, discarding the first. This means `maximum-pool-size: 20` is silently ignored — notification-service uses the HikariCP default of 10 connections.

At 10K concurrent WebSocket connections sending notifications, 10 DB connections are insufficient. Each of the 10 connections processes one notification creation at a time; throughput is capped at 10 DB ops/second × connection_duration.

**Fix:** Merge the duplicate keys into a single `spring.datasource.hikari` block.

---

## 5. Kafka Scalability

### SCALE-09 — HIGH | Single Kafka Partition Per Topic — No Horizontal Consumer Scaling

With `auto.create.topics.enable=true` and default settings, Kafka creates topics with 1 partition. A single-partition topic can only be consumed by 1 consumer instance at a time.

**Impact on notification-service:**
- Single partition on `chat.message-sent` → 1 notification-service instance processes all messages
- At 1,000 messages/second with 100 members per room → 100,000 notification creations/second on 1 instance
- Consumer lag accumulates; notification delivery is delayed

**Fix:** Pre-create topics with appropriate partition counts:
```
chat.message-sent: 12 partitions (keyed by roomId → all messages in a room ordered)
auth.account-created: 4 partitions (keyed by userId)
friendship.*: 8 partitions
```

---

### SCALE-10 — HIGH | Redis Sequence Counter Is a Single Contention Point

**Affected:** `chat-service/.../domain/service/impl/RedisRoomSequenceService.java`

```java
Long seq = redisTemplate.opsForValue().increment("room:seq:" + roomId);
```

Redis `INCR` is atomic but single-threaded per key. All message sends for a room serialize through this single Redis command:
- Maximum throughput for a single room: limited by Redis INCR latency (~0.1ms) = 10,000 messages/second per room
- This is adequate for most rooms but becomes a bottleneck for high-volume public channels

More critically: with `REALTIME_SESSION_REGISTRY_MODE=redis`, ALL session operations also go through the same single Redis instance, competing with sequence counters, pub/sub, presence state, and rate limiting.

**Fix:** Shard Redis by use case (separate Redis cluster for sessions, separate for pub/sub, separate for rate limiting). Or batch sequence allocation (allocate 100 IDs at a time, cache locally).

---

## 6. Memory and Resource Limits

### SCALE-11 — HIGH | No JVM Memory Flags in Dockerfiles

All 9 Dockerfiles run Spring Boot services without JVM memory configuration:
```dockerfile
CMD ["java", "-jar", "app.jar"]
# Missing: -Xmx, -XX:MaxRAMPercentage, -XX:+UseContainerSupport
```

Without `UseContainerSupport`, the JVM uses the **host** machine's memory for sizing, not the container's. This means:
- JVM allocates 25% of host memory as heap (e.g., 4 GB on a 16 GB host)
- Container memory limit (if set) is breached → OOM kill
- All containers fight for host memory with no isolation

**Fix:**
```dockerfile
CMD ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

---

### SCALE-12 — MEDIUM | No WebSocket Frame Size Limit — OOM DoS

**Affected:** `realtime-edge-service/src/main/java/com/chatweb/realtime/config/WebSocketConfig.java`

No `setMaxTextMessageBufferSize` configured. A malicious or buggy client can send a 100 MB message frame; the entire frame is buffered in heap before processing.

At 1,000 concurrent connections each sending 1 MB frames: 1 GB heap allocation, likely triggering GC stalls and OOM.

**Fix:**
```java
registry.addHandler(handler, "/ws")
        .setMaxTextMessageBufferSize(64 * 1024)  // 64 KB
        .setMaxBinaryMessageBufferSize(64 * 1024);
```

---

## 7. Rate Limiting Coverage

### SCALE-13 — MEDIUM | Rate Limiting Only at Gateway IP Level

Current rate limiting:
```yaml
gateway.ratelimit:
  default-replenish-rate: 20   # 20 req/sec per IP
  default-burst-capacity: 40   # burst to 40
  auth-replenish-rate: 5       # 5 req/sec for auth endpoints
```

**Gaps:**
- WebSocket connections are not rate limited after upgrade (unlimited frames per second)
- No per-user rate limiting (only per-IP) — shared IPs bypass effective limiting
- Auth endpoints have stricter rate limiting but no per-account lockout (SEC-11)
- WebSocket message rate not limited — a single connection can flood the system

**Fix:**
- Add WebSocket frame rate limiting in `RealtimeWebSocketHandler` per session
- Add per-user rate limiting at gateway using JWT `sub` claim as key
- Add per-account login attempt counter in Redis

---

## 8. Scalability Roadmap

### Phase 1: Fix Immediate Blockers (1-2 weeks)
1. Fix `REALTIME_SESSION_REGISTRY_MODE` conditional to ensure Redis registry in multi-instance
2. Set `maxmemory` and connection pool on Redis
3. Set JVM memory flags in all Dockerfiles
4. Fix YAML duplicate key in notification-service HikariCP config
5. Add WebSocket frame size limits

### Phase 2: Remove O(N) Bottlenecks (3-4 weeks)
1. Replace `KEYS *` with a session count INCR counter
2. Batch Redis session fetches via Lua script or pipeline
3. Add room→sessions index for direct delivery
4. Pre-create Kafka topics with appropriate partition counts

### Phase 3: Architectural Scaling (1-3 months)
1. Implement fan-out optimization for large rooms (>100 members)
2. Add Redis cluster / sharding by use case
3. Add PostgreSQL read replicas
4. Implement Transactional Outbox pattern
5. Replace synchronized WS send with async queue + back-pressure

### Capacity Targets

| Metric | Current (estimated) | Phase 1 | Phase 2 | Phase 3 |
|--------|---------------------|---------|---------|---------|
| Concurrent WebSocket | 10K (single instance) | 10K (stable) | 100K (3 instances) | 1M+ (10+ instances) |
| Messages/sec | ~100 | ~500 | ~5,000 | ~50,000 |
| Rooms | ~10K | ~10K | ~100K | ~1M |
| Notification delivery P99 | unknown | <500ms | <200ms | <100ms |
