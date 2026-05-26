# WebSocket Architecture Review — chatappBE
> Deep-dive audit | Date: 2026-05-20 | Service: realtime-edge-service + presence-service

---

## 1. Architecture Overview

The realtime layer uses Spring WebSocket (raw RFC 6455, no SockJS) running on embedded Tomcat. Cross-instance event delivery uses Redis Pub/Sub fan-out. Session metadata is stored in either an `InMemoryRealtimeSessionRegistry` (default/dev) or `RedisRealtimeSessionRegistry` (production). The presence subsystem runs as a separate microservice using Redis TTL keys + keyspace notifications for online/offline detection.

```
Client ──(WSS)──► Nginx ──► Gateway (/ws/** PUBLIC) ──► realtime-edge-service:8090
                                                               │
                    ┌──────────────────────────────────────────┤
                    │  RealtimeWebSocketHandler                 │
                    │  JwtHandshakeInterceptor (ticket-based)   │
                    │  InMemory/RedisSessionRegistry            │
                    │  RealtimeWebSocketSessionStore            │
                    │  ChannelSubscriptionManager               │
                    └──────────────────────────────────────────┘
                           │              │              │
                           ▼              ▼              ▼
                    Redis Pub/Sub     Kafka             HTTP (blocking)
                    (delivery)    (friendship)    (presence, chat, notif, friendship)
```

---

## 2. WebSocket Lifecycle Flow

```
CLIENT               TOMCAT THREAD              REDIS                PRESENCE-SVC

1. GET /realtime?ticket=X
   │── [JwtHandshakeInterceptor.beforeHandshake]
   │       GET ws:ticket:<ticket> ─────────────────────────────►
   │                              ◄──── "<userId>|<fullJWT>" ───
   │       DELETE ws:ticket:<ticket> ──────────────────────────►
   │   ⚠ RACE [WS-01]: ticket consumed before session exists.
   │     If connection drops here, ticket permanently lost.
   │
   │── [afterConnectionEstablished]
   │       RealtimeSession.create(userId, sessionId)
   │       sessionRegistry.register(session) ──────────────────► HSET + 4×SADD (non-atomic) [WS-05]
   │       webSocketSessionStore.register(session)
   │       subscribeSession("notification:<userId>")
   │       subscribeSession("friendship:<userId>")
   │       presenceBridge.onPresenceConnected() ────────────────► POST /ws/connect (blocking 1-3s) [WS-04]
   │       presenceBridge.globalSnapshot() ─────────────────────► GET /presence/global (blocking) [WS-04]
   │       sendGlobalSnapshot(client)
   │
2. MESSAGE LOOP (Tomcat thread)
   │── handleTextMessage
   │       rate check (non-atomic ConcurrentHashMap)
   │       dispatch by type:
   │           auth.token.refresh  ──► store new token WITHOUT validation ⚠ [WS-06]
   │           presence.*          ──► HTTP to presence-svc (blocking, no pool) [WS-04]
   │           chat.*              ──► HTTP to chat-svc (blocking, new TCP/call) [WS-04]
   │           subscribe           ──► ChannelSubscriptionManager.hasRoomAccess
   │                                       ──► Redis cache OR HTTP to chat-svc
   │                               ──► sessionRegistry.addSubscription (non-atomic) [WS-05]
   │
3. INBOUND EVENT (Redis pub/sub listener thread)
   │── RedisEventListener.onMessage(channel, payload)
   │       deliveryService.deliver*()
   │           sessionRegistry.findByChannelOwnedByCurrentInstance
   │               ──► SMEMBERS + N×HGETALL (sequential, no pipelining)
   │           for each session:
   │               synchronized(webSocketSession) { send() }  ⚠ [WS-03] head-of-line blocking
   │               on failure: log.warn, message dropped silently [WS-07]
   │
4. CROSS-INSTANCE HANDOFF (Redis pub/sub handoff channel)
   │── EdgeDeliveryHandoffListener.onMessage
   │       verify targetInstanceId == localInstanceId
   │       resolve sessions ──► synchronized(ws) { send() } [WS-03]
   │       retry with Thread.sleep() on listener thread ⚠ [WS-08]
   │
5. DISCONNECT
   │── afterConnectionClosed
   │       sessionRegistry.unregister(session) ──► multiple Redis ops, non-atomic [WS-05]
   │       webSocketSessionStore.unregister(session)
   │       presenceBridge.onPresenceDisconnected()
   │           ──► sessionRegistry.findByUserId (loads ALL sessions; read race) [WS-09]
   │           if last session: presenceService.offline()
   │   ⚠ webSocketSessionStore NOT cleaned by maintenance job [WS-10]
   │
6. RECONNECT
   │── Same as step 1. No offline message buffer. Messages sent during offline are lost. [WS-07]
   │
7. SESSION CLEANUP (EdgeSessionMaintenanceJob, every 30s)
   │── evictStaleSessions()
   │       SCAN realtime:session:* ──► O(N) on full keyspace [WS-11]
   │   ⚠ webSocketSessionStore NOT cleaned (session leak) [WS-10]
   │── cleanupOrphanIndexes()
   │       SCAN 3 index patterns ──► unbounded, no batch limit
   │
8. TTL EXPIRY (Redis keyspace notification → presence-service)
   │── PresenceKeyExpiredListener.onMessage
   │       match prefix "presence::user:" ──► also matches connections: and rooms: [PRES-01]
   │       presenceService.handleUserOfflineByTTL(userId)
   │           ──► cleanup rooms (N sequential SREM ops, not pipelined)
   │           ──► publish USER_OFFLINE ──► race with step 5 offline [PRES-02]
```

---

## 3. Critical Issues

### WS-01 — HIGH | Ticket Consumed Before Handshake Completes
**Affected file:** `JwtHandshakeInterceptor.java`

The ticket is deleted from Redis immediately after reading, before the WebSocket upgrade completes. If the connection is rejected (OS reset, load balancer timeout, SSL error), the ticket is permanently consumed but no session exists. The client has no way to recover without obtaining a new ticket.

**Fix:** Delay the conceptual consumption to `afterConnectionEstablished`. Keep the atomic delete in the interceptor for replay protection but signal a specific error code to the client on handshake failure directing them to re-issue the ticket.

---

### WS-02 — HIGH | `RealtimeSession.subscriptions` Is Not Thread-Safe
**Affected file:** `realtime-edge-service/.../connection/RealtimeSession.java`

`@Data` generates an unsynchronized `getSubscriptions()` that returns the raw `HashSet<String>`. While `subscribe()`, `unsubscribe()`, and `isSubscribedTo()` are `synchronized`, callers that iterate `getSubscriptions()` (e.g., in `InMemoryRealtimeSessionRegistry.unregister`) race against concurrent mutations and throw `ConcurrentModificationException`.

**Fix:** Replace `HashSet` with `ConcurrentHashMap.newKeySet()`. Remove `synchronized` from individual methods. Suppress Lombok's getter for `subscriptions` (`@lombok.AccessLevel.NONE`); expose only the safe accessor methods.

---

### WS-03 — HIGH | `synchronized(webSocketSession)` Causes Head-of-Line Blocking
**Affected files:** `ChatRealtimeDeliveryService.java`, `NotificationRealtimeDeliveryService.java`, `PresenceRealtimeDeliveryService.java`, `FriendshipRealtimeDeliveryService.java`

All delivery services:
```java
synchronized (webSocketSession) {
    webSocketSession.sendMessage(new TextMessage(json));
}
```
One slow client (TCP backpressure, large window) holds the JVM monitor. The Redis pub/sub listener thread is blocked while waiting for the lock. All other delivery attempts for other sessions in the same event batch are serialized behind the slow client.

**Scalability impact:** At 10k+ connections this collapses throughput. A single mobile client on a slow 3G connection can stall the delivery thread for seconds.

**Fix:** Use `ConcurrentWebSocketSessionDecorator` with a bounded send buffer and a `DISCONNECT` overflow strategy. Remove all manual `synchronized` blocks.

---

### WS-04 — HIGH | Blocking HTTP Calls on Tomcat Thread During Handshake
**Affected files:** `EdgePresenceLifecycleBridge.java`, `PresenceDomainClient.java`

`afterConnectionEstablished` makes 2 sequential blocking HTTP calls (POST `/ws/connect` + GET `/presence/global`) on the same Tomcat thread that accepted the WebSocket upgrade. With default 200 Tomcat threads and a 2-second timeout per call: max burst connect rate ≈ **33 connections/second**. Any presence-service latency spike saturates the thread pool and rejects new WebSocket connections.

**Fix:** Execute both calls on a dedicated bounded `CompletableFuture` executor. Send the global snapshot as a follow-up message after the client receives the `CONNECTED` frame.

---

### WS-05 — HIGH | Redis Session Registry Operations Are Not Atomic
**Affected file:** `realtime-edge-service/.../connection/RedisRealtimeSessionRegistry.java`

`register()` issues 6+ sequential Redis commands (HSET, EXPIRE, 4×SADD). A crash mid-sequence leaves orphan index entries. `unregister()` has the same fragility: session hash may be deleted while index set members pointing to it remain forever.

**Fix:** Wrap each logical operation (register, unregister, addSubscription, removeSubscription) in a Redis Lua script or `MULTI/EXEC` transaction.

---

### WS-06 — HIGH | New JWT Accepted in `handleTokenRefresh` Without Validation
**Affected file:** `RealtimeWebSocketHandler.java`

```java
String newToken = root.path("accessToken").asText(null);
session.getAttributes().put("accessToken", newToken);
```
Any string is accepted and subsequently forwarded to chat-service, presence-service, and other downstream services as `Authorization: Bearer <string>`.

**Fix:** Validate with the `JwtDecoder` bean. Verify the `sub` claim equals the existing `userId` in the session attributes.

---

### WS-07 — MEDIUM | No Offline Message Buffer — Messages Lost During Reconnect
**Affected files:** All delivery services

When `webSocketSession == null || !webSocketSession.isOpen()`, messages are silently dropped with `log.warn`. No buffer, no retry, no delivery confirmation.

**Fix:** On delivery failure, append to a Redis list `realtime:pending:<userId>` with a configurable TTL (e.g., 5 minutes). On successful connect, drain and deliver pending messages before processing new ones.

---

### WS-08 — MEDIUM | `Thread.sleep()` on Redis Pub/Sub Listener Thread in Retry Loop
**Affected file:** `EdgeDeliveryHandoffPublisher.java`

The retry loop calls `Thread.sleep(backoffMs)` (up to 350ms total) on the `RedisMessageListenerContainer` subscription thread. During a Redis blip — the exact scenario where this code runs — all pub/sub message processing is blocked for the sleep duration.

**Fix:** Move retry logic to a separate executor (`@Async` method with a dedicated thread pool). The listener thread must never sleep.

---

### WS-09 — MEDIUM | Presence Disconnect Race — Double `USER_OFFLINE` Event
**Affected files:** `EdgePresenceLifecycleBridge.java`, `PresenceService.java`

On concurrent tab close, both sessions read `hasOtherPresenceSessions = false` (non-atomic check-then-act). Both call the offline path, publishing two `USER_OFFLINE` Redis pub/sub events. All clients subscribed to the user's presence receive a duplicate offline notification and may flicker.

**Fix:** Own the connection count atomically inside presence-service using a Lua script that decrements and checks in one operation. Remove the edge-service pre-check entirely.

---

### WS-10 — HIGH | `RealtimeWebSocketSessionStore` Not Cleaned by Maintenance Job — Memory Leak
**Affected files:** `RealtimeWebSocketSessionStore.java`, `EdgeSessionMaintenanceJob.java`

`evictStaleSessions()` removes sessions from `IRealtimeSessionRegistry` but never calls `webSocketSessionStore.unregister()`. On abnormal disconnect, the `WebSocketSession` object (holding native TCP socket resources) lives in a `ConcurrentMap` indefinitely.

**Fix:** After `evictStaleSessions()`, call `webSocketSessionStore.unregister(sessionId)` for each evicted session ID.

---

### WS-11 — HIGH | `evictStaleSessions` Uses O(N) Redis SCAN
**Affected file:** `RedisRealtimeSessionRegistry.java`

`SCAN realtime:session:*` iterates the full keyspace every 30 seconds from every edge instance. At 1M sessions with N instances, this creates N concurrent O(M) scans against Redis.

**Fix:** Maintain a ZSET `realtime:instance:expiry:<instanceId>` scored by expiry epoch. Use `ZRANGEBYSCORE 0 <now>` to find expired sessions in O(log N + M) where M is the count of expired entries.

---

### WS-12 — HIGH | No Server-Initiated Native WebSocket Ping (Dead Connection Accumulation)
**Affected:** `WebSocketConfig.java`

No RFC 6455 ping frames are sent. Dead TCP connections (client process killed, NAT timeout, mobile background kill) accumulate until the 90-second session lease expires and the maintenance job runs. Dead sessions consume thread pool capacity and inflate presence counts.

**Fix:** Schedule a `PingMessage` sender every 25 seconds. Close sessions that don't respond with a pong within 10 seconds.

---

### WS-13 — MEDIUM | No WebSocket Frame Size Limit — OOM DoS
**Affected file:** `WebSocketConfig.java`

No `setMaxTextMessageBufferSize`. A single malicious client can send a 1GB frame, buffering it entirely in heap.

**Fix:**
```java
registry.addHandler(handler, "/ws")
        .setMaxTextMessageBufferSize(65536); // 64KB
```
Also configure Nginx client body limits for the WebSocket upgrade path.

---

### WS-14 — MEDIUM | Subscription Count Per Session Has No Upper Bound
**Affected:** `RealtimeWebSocketHandler.java`, `ChannelSubscriptionManager.java`

A user in 1,000 rooms who subscribes from 5 tabs creates 5,000 channel index entries in Redis. No max subscription count is enforced.

**Fix:** Add a configurable `maxSubscriptionsPerSession` (e.g., 200). Return a `SUBSCRIBE_DENIED` error when exceeded.

---

### WS-15 — MEDIUM | `ChannelSubscriptionManager` Caches Negative Access Results for 60s
**Affected file:** `ChannelSubscriptionManager.java`

A `false` access result (HTTP error from chat-service, user not yet in room) is cached for 60 seconds. On transient chat-service error, all users are locked out from room channel subscription for 60 seconds.

**Fix:** Cache only positive (`true`) results. On HTTP errors (as opposed to 403/404), do not cache — retry on next subscribe attempt.

---

### WS-16 — MEDIUM | Friendship Events Double-Delivered via Dual Kafka Topic Subscription
**Affected file:** `FriendshipKafkaEventConsumer.java`

The consumer subscribes to both `TOPIC_FRIENDSHIP_REQUEST_EVENTS` and `TOPIC_FRIENDSHIP_EVENTS`. For `friend.request.accepted`, both topics emit the event. Each party receives duplicate `WS_FRIEND_REQUEST_ACCEPTED` WebSocket messages.

**Fix:** Audit which topics emit each event type. Route each event type through exactly one topic + one consumer path. Add event-ID deduplication in the delivery service.

---

## 4. Presence System Issues

### PRES-01 — HIGH | Key Prefix Collision in `PresenceKeyExpiredListener`
**Affected:** `PresenceKeyExpiredListener.java`, `RedisPresenceEphemeralStateStore.java`

The listener fires on all keys matching `presence::user:*`. This also matches:
- `presence::user:connections:<userId>` (connection counter)
- `presence::user:rooms:<userId>` (room membership set)

If either of these keys ever expires (Redis memory eviction), the listener attempts to parse `connections:<uuid>` as a UUID, producing a parsing error or a misfire of the offline path.

**Fix:** Use a non-overlapping prefix for the TTL state key: `presence::state:user:<userId>`. Update `PresenceKeyExpiredListener` accordingly.

---

### PRES-02 — HIGH | Presence Connection Count Key Has No TTL — Zombie Online Users
**Affected:** `RedisPresenceEphemeralStateStore.java`

`presence::user:connections:<userId>` is set/decremented with no expiry. If a service instance crashes before decrementing, the counter is permanently inflated. The user appears online indefinitely with no recovery mechanism.

**Fix:** Set TTL = `USER_TTL × 3` on the counter key. Refresh TTL on every heartbeat. A periodic reconciliation job should cross-validate the counter against the TTL state key.

---

### PRES-03 — MEDIUM | `fail-on-missing=false` for Keyspace Notification — Silent Degradation
**Affected:** `presence-service/src/main/resources/application.yaml`

If Redis is not configured with `notify-keyspace-events Ex`, the presence service starts silently without TTL-based offline detection working. Users never go offline via TTL expiry; they accumulate as zombie online users.

**Fix:** Change `fail-on-missing` default to `true` in production profiles.

---

## 5. Scalability Matrix

| Issue | 10k Connections | 100k Connections | 1M Connections |
|-------|----------------|-----------------|----------------|
| WS-03 synchronized send | Occasional blocking | Head-of-line collapse | Throughput → 0 |
| WS-04 blocking handshake HTTP | 33 conn/s max | Burst rejected | N/A |
| WS-05 non-atomic Redis ops | Occasional orphans | Frequent inconsistency | High corruption |
| WS-10 session store memory leak | Slow growth | Noticeable leak | OOM risk |
| WS-11 SCAN eviction | Acceptable | 200-500ms spike/run | Redis CPU saturation |
| WS-12 no native ping | ~5% dead sessions | ~15% dead sessions | Inaccurate presence |
| WS-13 no frame size limit | DoS trivial | DoS trivial | DoS trivial |
| PRES-02 no counter TTL | Occasional zombies | Growing inaccuracy | Unreliable presence |

---

## 6. Recommended Remediation Order

**Sprint 1 (Must fix before any production load):**
- WS-06 (validate JWT in token refresh)
- WS-13 (frame size limit)
- WS-02 (thread-safe session subscriptions)

**Sprint 2 (Fix before scaling to 1k concurrent users):**
- WS-03 (ConcurrentWebSocketSessionDecorator)
- WS-04 (async presence connect)
- WS-05 (atomic Redis session registry ops)
- WS-10 (session store cleanup in maintenance job)
- WS-07 (offline message buffer)

**Sprint 3 (Fix before scaling to 10k users):**
- WS-11 (ZSET expiry index)
- WS-12 (native ping/pong)
- WS-09 (atomic presence disconnect)
- PRES-01 (key prefix fix)
- PRES-02 (connection counter TTL)

**Sprint 4 (Operational hardening):**
- WS-08 (retry executor, remove sleep on listener thread)
- WS-14 (max subscriptions per session)
- WS-15 (cache only positive access results)
- PRES-03 (fail-on-missing=true)
