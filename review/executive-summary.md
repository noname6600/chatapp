# Executive Summary — ChatApp Backend Architecture Review
> Deep-dive audit | Date: 2026-05-20 | Reviewer: multi-agent analysis (principal backend engineer level)

---

## System Overview

**ChatApp Backend (chatappBE)** is a Java 21 / Spring Boot 3.5.6 microservices system implementing production-grade realtime chat infrastructure, comparable in intent to Discord or Messenger. The system comprises 9 microservices, 8 common modules, Apache Kafka for event streaming, Redis for session management and pub/sub delivery, PostgreSQL for persistence, and Cloudinary for media storage.

**Architecture style:** Microservices with Spring Cloud Gateway. Mixed internal architecture patterns: flat layering (most services), DDD modules (chat-service), hexagonal/ports-and-adapters (realtime-edge-service).

**Real-time delivery path:** WebSocket (RFC 6455, no SockJS) → realtime-edge-service → Redis pub/sub → per-instance local delivery. Cross-instance message delivery relies entirely on Redis pub/sub fire-and-forget.

---

## Critical Findings Summary

| Severity | Count | Categories |
|----------|-------|-----------|
| CRITICAL | 14 | Credentials, key management, Kafka threading, Redis reliability, DB migrations, gateway auth, container security |
| HIGH | 37 | Auth bypass vectors, WebSocket correctness, Kafka delivery, Redis memory leaks, API auth gaps, scalability blockers |
| MEDIUM | 22 | Privacy violations, rate limiting gaps, naming inconsistencies, dead code, config anti-patterns |
| LOW | 8 | Cosmetic, minor conventions |
| **Total** | **81** | Across all 12 review dimensions |

---

## Top 10 Most Critical Issues

### 1. Live Credentials Committed to Source Control (SEC-01)
Google OAuth client secret, Resend API key, and Cloudinary API credentials are present in `application-local.yaml` files tracked by git. The Cloudinary API secret is also baked into `chat-service/application.yaml` as a fallback default — it is embedded in every built JAR.  
**Required action: Rotate all credentials immediately.**

### 2. PipelineExecutor.runAsync() Breaks @Transactional — Data Integrity (KAFKA-06 / DEBT-17)
`chat-service` executes pipeline steps via `CompletableFuture.runAsync()`, moving execution to a new thread. Spring's `@Transactional` uses ThreadLocal — each step gets an independent transaction. Messages can be persisted without mentions; Kafka events can be published before (or without) the DB transaction committing. This is the root cause of potential phantom events and partial message saves.

### 3. No Transactional Outbox Pattern — Event Loss Under Any Crash (ARCH-01 / KAFKA-05)
Every service that writes to the database and publishes to Kafka has a crash window where the DB commits but the Kafka event is lost. A user registration that completes but never triggers the welcome email is one example. There is no retry, no outbox table, no CDC relay.

### 4. FriendshipKafkaEventConsumer Has No Consumer Group ID (KAFKA-01)
realtime-edge-service's friendship event consumer has no `groupId`. Spring Kafka generates a random UUID per restart. In multi-instance deployment, every instance processes every friendship event (N× fan-out). On restart, events delivered during the downtime window are silently dropped.

### 5. All 9 Containers Run as Root (SEC-22 / PROD-01)
No Dockerfile has a `USER` directive. Container escape = root on host for all 9 services.

### 6. Presence and Session Registry Keys Have No TTL — Memory Leak (REDIS-04 / REDIS-05)
WebSocket session data and user presence data (Redis HASHes, SETs) are never expired. On any abnormal disconnect (network drop, server crash), stale entries accumulate indefinitely. Redis memory grows without bound.

### 7. V1 Flyway Migrations Are Empty Placeholders (DEBT-04 / PROD-05)
`friendship-service` and `user-service` V1 migrations contain only SQL comments. Fresh deployment to any new environment fails at startup. CI environments, staging, and new developer setups are completely broken.

### 8. Redis Pub/Sub Fire-and-Forget — No Message Delivery Guarantee (REDIS-07)
The entire cross-instance WebSocket delivery path uses Redis pub/sub with no persistence, acknowledgment, or replay. Any message in flight when a client reconnects or Redis restarts is permanently lost with no indication to the sender.

### 9. auth-service Uses `ddl-auto: update` (PROD-04)
Hibernate auto-DDL in production can silently drop columns on entity refactoring. auth-service — the most critical service — is managed by Hibernate DDL, not Flyway migrations. Any accidental entity change can corrupt the users table.

### 10. WebSocket `/ws/**` Is Fully Public at Gateway (SEC-04)
The gateway's security config places `/ws/**` in `PUBLIC_GENERAL_PATHS`. Any unauthenticated actor can attempt WebSocket upgrade. realtime-edge-service:8090 is also directly exposed to the Docker host via `ports:`, bypassing the gateway entirely.

---

## Complete System Architecture Flow

### Authentication Flow
```
1. Client → POST /api/v1/auth/login
2. gateway: rate limit check (Redis) → JWT not required
3. auth-service: BCrypt verify → issue RSA RS256 JWT (sub=userId, exp=15min)
4. Response: { accessToken, refreshToken }
5. Subsequent requests: Authorization: Bearer <JWT>
6. gateway: JWKS fetch from auth-service → validate signature + expiry
7. gateway: set X-User-Id header → forward to downstream service
```

### WebSocket Lifecycle
```
1. Client → GET /realtime/ticket (auth required)
2. realtime-edge: generate 30-second ticket → store userId|JWT in Redis
3. Client → WS upgrade /ws?ticket={ticket}
4. JwtHandshakeInterceptor: consume ticket → extract userId → store in session attributes
5. RealtimeWebSocketHandler.afterConnectionEstablished()
   → Register session in Redis session registry
   → Mark user online in presence-service (via HTTP)
6. Client sends: { "type": "subscribe", "roomId": "..." }
   → ChannelSubscriptionManager: verify room membership (HTTP to chat-service)
   → Add roomId to session subscriptions
7. Message received from another user:
   → Kafka: chat.message-sent → realtime-edge KafkaConsumer
   → Find all sessions subscribed to roomId
   → Redis session registry lookup (N+1 Redis calls)
   → synchronized(webSocketSession).sendMessage()
8. Client disconnect:
   → afterConnectionClosed()
   → Remove session from Redis registry
   → Signal presence-service offline (if last session)
   → Clean up subscriptions
```

### Realtime Message Send Flow
```
Client → POST /api/v1/chat/messages (gateway authenticated)
  → chat-service MessageCommandService.send()
    → Pipeline (async thread — @Transactional broken):
      1. ValidateMessageStep: sanitize, validate length
      2. CheckBlockedPairStep: Feign → friendship-service [INVERTED LOGIC BUG]
      3. ResolveRoomStep: verify room, membership
      4. GenerateSequenceStep: sets seq=0 [NO-OP]
      5. PersistMessageStep: @Transactional NEW THREAD [independent transaction]
         - messageRepository.save()
         - Redis INCR room:seq:{roomId}
      6. PersistMentionStep: saves @mentions [separate transaction]
      7. PublishMessageSentEventStep: afterCommit hook → Kafka "chat.message-sent"
  
  → notification-service consumes "chat.message-sent":
    - Create notification per room member [O(N) DB inserts]
    - Redis PUBLISH notification:{userId} per member [O(N) publishes]
  
  → realtime-edge consumes "chat.message-sent":
    - For each subscriber: Redis lookup + synchronized WS send
  
  → realtime-edge receives Redis pub/sub:
    - Thread.sleep(50ms) [BLOCKS Redis listener]
    - Deliver to local WebSocket sessions
```

### Kafka Event Flows
```
auth.account-created → notification-service → welcome email (Resend API)
chat.message-sent    → notification-service (notification creation)
                     → realtime-edge (WebSocket delivery)
chat.message-edited  → notification-service + realtime-edge [NO afterCommit guard]
chat.message-deleted → notification-service + realtime-edge [NO afterCommit guard]
friendship.*         → realtime-edge [NO groupId on consumer]
```

### Redis Data Flows
```
Session registry:  session:registry:{sessionId} HASH → session metadata
User sessions:     session:user:{userId}:sessions SET → sessionIds
Presence TTL:      presence:{userId} STRING, 60s TTL → keyspace notification trigger
Presence data:     presence::user:{userId}:* HASH/SET → online state [NO TTL]
Room sequences:    room:seq:{roomId} STRING → INCR counter [NO TTL]
Rate limiting:     rate_limit:{ip} ZSET → token bucket
Pub/sub:           notification:{userId} CHANNEL → message delivery
Tickets:           ticket:{uuid} STRING, 30s TTL → userId|fullJWT [SECURITY]
Dedup:             notification:dedup:{eventId} STRING, 5min TTL → seen guard
```

---

## Overall Scores

| Dimension | Score | Rationale |
|-----------|-------|-----------|
| **Architecture** | **5.5/10** | Good service decomposition. Critical gaps: no outbox, broken pipeline threading, sync calls on critical path, circular auth/user dependency. |
| **Scalability** | **4/10** | Redis pub/sub and session registry both have O(N) bottlenecks. Fan-out unbounded. Single Redis/Kafka/PostgreSQL. N+1 Redis calls per delivery. WebSocket send synchronized. |
| **Maintainability** | **5/10** | chat-service DDD structure is exemplary. Other services use flat layering inconsistently. 81 issues require attention. No tests — every refactor carries regression risk. |
| **Production Readiness** | **3/10** | Live credentials in source. Containers run as root. No observability stack. No graceful shutdown. No backup strategy. Placeholder Flyway migrations. Single-host single-point-of-failure for all data stores. |
| **WebSocket Architecture** | **4.5/10** | Conceptually correct (Redis-backed session registry for multi-instance). Implementation has critical flaws: no TTL on session data, fire-and-forget pub/sub, synchronized send, no frame size limit, no native ping, token stored in Redis, no reconnect protocol. |
| **Microservice Boundary Quality** | **6/10** | Service responsibilities are well-defined. Leakage: auth creates user profiles, chat calls friendship synchronously on critical path, notification tightly coupled to all domains. |

---

## Immediate Actions Required (Before Any Traffic)

1. **Rotate all credentials** — Google OAuth, Resend, Cloudinary (SEC-01)
2. **Fix CheckBlockedPairStep inverted logic** — ALL DMs are currently blocked, ALL blocked users can message (ARCH-03)
3. **Write V1 Flyway migrations** — friendship-service and user-service fail on fresh deploy (DEBT-04)
4. **Add groupId to FriendshipKafkaEventConsumer** — drops all friendship events on restart (KAFKA-01)
5. **Remove live secrets from application.yaml defaults** — Cloudinary secret is in every JAR (SEC-01)
6. **Add USER to all Dockerfiles** — containers running as root (PROD-01)
7. **Set Redis maxmemory policy** — OOM crash risk (REDIS-02)
8. **Set JVM memory flags in Dockerfiles** — container OOM kill loop (PROD-03)

---

## Recommended Fix Order (8-Week Plan)

| Week | Priority | Work |
|------|----------|------|
| 1 | P0 | Credential rotation, inverted block logic fix, V1 migrations, groupId fix |
| 2 | P0 | Remove @Transactional from pipeline async threads (run pipeline sync), add afterCommit guards to edit/delete/react publish steps |
| 3 | P1 | TTL on Redis presence and session keys, remove full JWT from Redis tickets |
| 4 | P1 | Add USER to all Dockerfiles, JVM memory flags, Redis maxmemory, fix HikariCP YAML |
| 5 | P1 | Prometheus metrics on all services, structured JSON logging, disable DEBUG in prod |
| 6 | P1 | Feign circuit breakers, Feign timeouts, graceful shutdown config |
| 7 | P2 | Transactional outbox (auth + chat-service), jjwt upgrade, Flyway for all services |
| 8 | P2 | JWT issuer validation at gateway, WebSocket frame size limit, CORS hardening |
