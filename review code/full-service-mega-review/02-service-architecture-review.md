# Service Architecture Review

**Focus**: Overall microservices design, service boundaries, responsibility ownership, common module usage  
**Scope**: All 9 services + 10 common modules  

---

## High-Level Architecture

### Service Topology

```
┌─────────────────┐
│  Nginx (Proxy)  │
│  (frontend/UI)  │
└────────┬────────┘
         │
┌────────▼────────────────────────────────────────────┐
│      API Gateway Service (Port 8080)                │
│      - Route definitions to all services             │
│      - JWT resource server (validates tokens)        │
│      - Rate limiting (if configured)                 │
└────────┬────────┬────────┬────────┬────────┬────────┘
         │        │        │        │        │
    ┌────▼─┐  ┌────▼─┐  ┌──▼───┐  ┌──▼───┐  ┌──▼───┐
    │Auth  │  │User  │  │Chat  │  │Upload│  │Other │
    │8081  │  │8082  │  │8083  │  │8084  │  │8085+ │
    └────┬─┘  └──┬───┘  └──┬───┘  └──┬───┘  └──┬───┘
         │       │         │         │         │
         └───────┼─────────┼─────────┼─────────┘
                 │         │         │
    ┌────────────▼─────────▼─────────▼────────────┐
    │  Postgres (Shared Multi-Tenant Schema)       │
    │  - auth: users, refresh_tokens               │
    │  - user: profiles, avatars                   │
    │  - chat: rooms, messages, reactions          │
    │  - presence: (deprecated, moved to Redis)    │
    │  - friendship: friend_requests, friends      │
    │  - notification: notifications               │
    └─────────────────────────────────────────────┘
         
    ┌────────────────────────────────────────────┐
    │  Kafka (Event Streaming)                    │
    │  - Cross-service async events               │
    │  - Durable event log                        │
    │  - Consumer groups per service              │
    └─────────────────────────────────────────────┘

    ┌────────────────────────────────────────────┐
    │  Redis (Ephemeral + Cache)                  │
    │  - Presence state (online/offline)          │
    │  - Cache (user profiles, room lists)        │
    │  - Pub/Sub (realtime ephemeral events)      │
    └─────────────────────────────────────────────┘
```

---

## Service Responsibilities

### ✅ Well-Defined Boundaries

| Service | Primary Responsibility | Ports | Auth |
|---------|------------------------|-------|------|
| **gateway** | Request routing, JWT validation | 8080 | OAuth2 ResourceServer |
| **auth** | User registration, login, JWT issue | 8081 | Self-service endpoints |
| **user** | User profiles, avatars, search | 8082 | OAuth2 + internal |
| **chat** | Rooms, messages, attachments, reactions | 8083 | OAuth2 + internal |
| **presence** | Online status, typing indicators | 8085 | OAuth2 + Redis pub/sub |
| **friendship** | Friend requests, friend list | 8086 | OAuth2 + Kafka events |
| **notification** | Notifications, read state | 8087 | OAuth2 + WebSocket |
| **upload** | File upload, media storage | 8088 | OAuth2 + Cloudinary |
| **realtime-edge** | Unified WebSocket endpoint | 8089 | JWT handshake auth |

---

## 🟡 MEDIUM: WebSocket Ownership Fragmentation

### Problem
**Multiple services own WebSocket handlers** → Code duplication, inconsistent patterns

### Current State
- **presence-service**: `PresenceWebSocketHandler` → presence updates, typing
- **notification-service**: `NotificationWebSocketHandler` → notifications
- **friendship-service**: `FriendshipWebSocketHandler` → friendship events (likely dead code)
- **chat-service**: WebSocket handler for messages (if present)
- **realtime-edge-service**: Unified WebSocket endpoint (NEW, intended consolidation)

### Issue
Each service:
1. Defines its own WebSocket configuration
2. Manages its own session registry
3. Has its own subscription/authorization logic
4. Does not coordinate multi-instance behavior
5. Duplicates heartbeat/disconnect cleanup

### Evidence
- `presence-service/src/main/java/com/example/presence/controller/PresenceWebSocketHandler.java`
- `notification-service/src/main/java/com/example/notification/controller/NotificationWebSocketHandler.java`
- `friendship-service/src/main/java/com/example/friendship/controller/FriendshipWebSocketHandler.java` (verify if active)
- `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/WebSocketHandler.java` (new consolidated endpoint)

### Classification
- **Severity**: MEDIUM (architectural duplication, not a bug)
- **Scope**: FUTURE-ARCHITECTURE-CLEANUP (realtime-edge consolidation underway)
- **Action**: DO-NOT-FIX-NOW (transition to realtime-edge is already planned)
- **Migration Path**: Leave existing service endpoints, gradually route new clients to realtime-edge

### Why Not Fix Now
- Realtime-edge is new and likely still stabilizing
- Existing connections work; no emergency
- Transition should be gradual (feature flag, 5% → 100%)
- Requires careful migration plan to avoid dropping active users

---

## 🟡 MEDIUM: Event Publishing Timing Consistency

### Problem
**Inconsistent publish-after-commit patterns** across services

### Current State
- **chat-service**: Some use `TransactionSynchronization` (correct)
- **notification-service**: Some use `@Transactional` then `publish()` (may publish before commit)
- **friendship-service**: Documented as correct in phase 6 restore, verify in code
- **auth-service**: Token issued before event published (acceptable for auth events)

### Classification
- **Severity**: MEDIUM (eventual consistency at risk)
- **Scope**: SERVICE-ONLY per service (or COMMON-OPTIONAL if abstracted)
- **Action**: Audit each service's event publishing; ensure consistency
- **Pattern**: Use `TransactionSynchronization.STATUS_COMMITTED` or Spring's `@TransactionalEventListener`

### Affected Classes (Examples)
- `chat-service/src/main/java/com/example/chat/service/impl/ChatMessageService.java`
- `notification-service/src/main/java/com/example/notification/service/impl/NotificationCommandService.java`
- `friendship-service/src/main/java/com/example/friendship/service/impl/FriendCommandService.java`

---

## 🟡 MEDIUM: Common Module Compliance Gaps

### Issue
**Services not consistently using common modules** → Code duplication

### CORS Configuration
- **auth-service**: `SecurityConfig` duplicates CORS setup
- **user-service**: `SecurityConfig` duplicates CORS setup
- **chat-service**: May duplicate
- **presence-service**: May duplicate
- **notification-service**: May duplicate

**Should use**: `com.example.common.web.cors.CorsProperties` (appears to be used, verify)

### JWT Validation
- **Local JwtDecoder** in multiple services (by design for offline validation)
- **common-security/JwtDecoder** exists but may not be used
- **Inconsistency**: Some services use local, some use common

**Recommendation**: Document which services use local vs. common JwtDecoder; ensure consistency

### Kafka Production
- **Each service has own** `KafkaProducerAdapterConfig`
- **Should use**: Common producer from `common-kafka`

**Fix**: Consolidate to `common-kafka` if not already unified

---

## 🔴 BLOCKER: Kafka Topic Constants Undefined

### Problem
**24+ references to `KafkaTopics` constants that don't exist**

### Location
- `common-events/src/main/java/com/example/common/events/KafkaTopics.java`

### Missing Constants (Examples)
```
ACCOUNT_CREATED
ACCOUNT_PASSWORD_RESET
CHAT_MESSAGE_SENT
CHAT_MESSAGE_EDITED
CHAT_MESSAGE_DELETED
CHAT_REACTION_ADDED
CHAT_REACTION_REMOVED
FRIEND_REQUEST_SENT
FRIEND_REQUEST_ACCEPTED
FRIEND_REQUEST_DECLINED
USER_ONLINE
USER_OFFLINE
...
```

### Evidence
Services reference these constants in:
- `chat-service/src/main/java/com/example/chat/adapter/out/kafka/*`
- `presence-service/src/main/java/com/example/presence/adapter/out/kafka/*`
- `notification-service/src/main/java/com/example/notification/adapter/in/kafka/*`
- `friendship-service/src/main/java/com/example/friendship/adapter/out/kafka/*`

### Error When Running
```
java.lang.NoSuchFieldError: CHAT_MESSAGE_SENT
  at com.example.chat.adapter.out.kafka.ChatMessageKafkaPublisher.publish()
```

### Fix
Add all missing constants to `KafkaTopics.java`:
```java
@UtilityClass
public class KafkaTopics {
    // User events
    public static final String ACCOUNT_CREATED = "user.account.created";
    public static final String ACCOUNT_PASSWORD_RESET = "user.password.reset";
    
    // Chat events
    public static final String CHAT_MESSAGE_SENT = "chat.message.sent";
    public static final String CHAT_MESSAGE_EDITED = "chat.message.edited";
    // ... etc
}
```

### Classification
- **Severity**: BLOCKER (NoSuchFieldError at runtime)
- **Scope**: COMMON-REQUIRED
- **Risk**: LOW (simple constant definitions)
- **Verification**: `./gradlew.bat :chat-service:test` (should not throw NoSuchFieldError)

---

## ✅ GOOD: Cross-Service REST Client Usage

**Services use Feign clients** for synchronous calls:
- `common-feign` module provides base configuration
- Example: `chat-service` calls `user-service` to verify room membership

**Improvement Opportunity** (DO-NOT-FIX-NOW):
- No circuit breaker configuration (single `@FeignClient` calls)
- No explicit retry policy

---

## ✅ GOOD: Async Event-Driven Primary Pattern

- **Kafka** used for durable cross-service events (correct)
- **Redis pub/sub** used for ephemeral realtime events (correct)
- **WebSocket** used for client realtime updates (correct)

**Separation is clear** and follows event-driven principles.

---

## ✅ GOOD: Database Schema Isolation

- **Shared Postgres instance** with **separate schemas per service**
- No direct schema access between services (good for independ deployments)
- All cross-service queries happen via REST/Kafka/Redis

---

## Architecture Recommendations Summary

| Issue | Severity | Scope | Action |
|-------|----------|-------|--------|
| WebSocket fragmentation | MEDIUM | FUTURE-ARCHITECTURE | Realtime-edge consolidation (already planned) |
| Event publishing timing | MEDIUM | SERVICE-ONLY | Audit & standardize per service |
| Common module compliance | LOW | COMMON-OPTIONAL | Document & consolidate non-critical paths |
| Kafka topic constants | BLOCKER | COMMON-REQUIRED | Add missing constants immediately |
| JWT decoder conflicts | BLOCKER | SERVICE-ONLY | Coordinate bean definitions per service |
| Cloudinary bean missing | BLOCKER | SERVICE-ONLY | Add config to upload-service |

---

## Next Steps

1. **Read 01-build-and-runtime-gates.md** for bean wiring details
2. **Read 03-11**: Individual service reviews for specific issues
3. **Read 14-security-review.md** for security-specific architecture concerns
4. **Read 21-final-fix-plan.md** for execution order
