# Architecture Review — chatappBE
> Deep-dive audit | Date: 2026-05-20 | Reviewer: multi-agent analysis

---

## 1. High-Level Architecture Map

```
                              ┌─────────────────────────────────────────────────┐
                              │                  External Clients                │
                              │          Browser / Mobile / Desktop App          │
                              └──────────────────┬──────────────────────────────┘
                                                 │ HTTPS (REST + WebSocket)
                                                 ▼
                              ┌──────────────────────────────────┐
                              │         gateway-service          │
                              │  Spring Cloud Gateway 2025.0.1   │
                              │  Port 8080 (HTTP + WS upgrade)   │
                              │  • JWT validation (JWKS)         │
                              │  • Rate limiting (Redis)         │
                              │  • Circuit breakers (R4j)        │
                              └──┬─────┬──────┬──────┬──────┬───┘
                                 │     │      │      │      │
              ┌──────────────────┘     │      │      │      └────────────────────┐
              ▼                        ▼      ▼      ▼                           ▼
   ┌──────────────────┐  ┌─────────────────┐ │  ┌──────────────┐  ┌─────────────────────┐
   │  auth-service    │  │  user-service   │ │  │chat-service  │  │ realtime-edge-      │
   │  Port 8081       │  │  Port 8082      │ │  │Port 8084     │  │ service Port 8090   │
   │  • JWT signing   │  │  • User profile │ │  │• Messages    │  │  • WebSocket hub    │
   │  • OAuth2        │  │  • Search       │ │  │• Rooms       │  │  • Redis pub/sub    │
   │  • Email verify  │  │  • Registration │ │  │• Reactions   │  │  • Session registry │
   └──────┬───────────┘  └──────┬──────────┘ │  └───────┬──────┘  └─────────────────────┘
          │                     │             │          │
          │              ┌──────┘             ▼          │
          │              │      ┌──────────────────┐     │
          │              │      │friendship-service│     │
          │              │      │  Port 8085       │     │
          │              │      │  • Friend requests│    │
          │              │      │  • Blocks        │     │
          │              │      └──────────────────┘     │
          │              │                               │
          └──────────────┼───────────────────────────────┘
                         │           Kafka Topics
                         └─────────────────────────────────────────┐
                                                                   ▼
                                                    ┌──────────────────────────┐
                                                    │    Apache Kafka          │
                                                    │    Single Broker RF=1    │
                                                    │    ZooKeeper mode        │
                                                    │                          │
                                                    │  Topics:                 │
                                                    │  • auth.account-created  │
                                                    │  • chat.message-sent     │
                                                    │  • chat.message-edited   │
                                                    │  • chat.message-deleted  │
                                                    │  • chat.message-reaction │
                                                    │  • friendship.*          │
                                                    └─────────┬────────────────┘
                                                              │
                                        ┌─────────────────────┴──────────────────┐
                                        ▼                                        ▼
                           ┌────────────────────────┐             ┌──────────────────────────┐
                           │  notification-service  │             │  realtime-edge-service   │
                           │  Port 8086             │             │  (also Kafka consumer)   │
                           │  • Email sending       │             │  • Friendship events     │
                           │  • Push notifications  │             │  • Chat events           │
                           │  • Kafka consumers     │             └──────────────────────────┘
                           └───────────┬────────────┘
                                       │ Redis PUBLISH
                                       ▼
                           ┌────────────────────────┐
                           │      Redis             │
                           │  • Session registry    │
                           │  • Presence state      │
                           │  • Rate limiting       │
                           │  • Pub/sub channels    │
                           │  • Dedup guards        │
                           └────────────────────────┘

Supporting Services:
┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐
│  presence-service   │  │  upload-service     │  │  PostgreSQL         │
│  Port 8087          │  │  Port 8088          │  │  Single instance    │
│  • Redis TTL-based  │  │  • Cloudinary URLs  │  │  auth, user, chat,  │
│  • Keyspace notif.  │  │  • Pre-signed upload│  │  friendship, notif  │
└─────────────────────┘  └─────────────────────┘  └─────────────────────┘
```

---

## 2. Service Interaction Map

| From | To | Protocol | Purpose | Issue |
|------|-----|----------|---------|-------|
| gateway | auth-service | HTTP | JWT JWKS fetch | — |
| gateway | all services | HTTP | Request proxying | — |
| auth-service | user-service | HTTP (Feign) | Create user profile on register | Circular dependency risk |
| chat-service | friendship-service | HTTP (Feign) | Check block status per message | Sync call on message send critical path |
| chat-service | user-service | HTTP (Feign) | Resolve user names for mentions | Sync call on message send |
| realtime-edge | auth-service | HTTP (JWKS) | JWT public key fetch | — |
| notification-service | Kafka | consume | All event types | — |
| realtime-edge | Kafka | consume | chat.* + friendship.* | No groupId on friendship |
| auth-service | Kafka | produce | auth.account-created | After-commit on servlet thread |
| chat-service | Kafka | produce | chat.* events | Pipeline threading issue |
| friendship-service | Kafka | produce | friendship.* events | — |
| notification-service | Redis | pub/sub | Notification fan-out | Fire-and-forget |
| realtime-edge | Redis | pub/sub | Cross-instance delivery | Fire-and-forget |
| presence-service | Redis | TTL+keyspace | Online/offline detection | No TTL on data keys |
| realtime-edge | Redis | HASH+SET | Session registry | No TTL on keys |

---

## 3. Critical Architecture Issues

### ARCH-01 — CRITICAL | No Transactional Outbox Pattern

**Impact:** ALL services that write to DB and publish Kafka events

The dual-write problem is unresolved across the entire system. Every service that:
1. Writes to the database
2. Then publishes to Kafka

...has a window where the DB can commit but the Kafka publish fails, causing permanent event loss.

**Affected flows:**
- auth-service: user registration → `auth.account-created`
- chat-service: message send → `chat.message-sent`
- chat-service: message edit → `chat.message-edited`
- friendship-service: friend request → `friendship.request-sent`

The `@TransactionalEventListener(phase = AFTER_COMMIT)` pattern used by auth-service is better than bare `kafkaTemplate.send()` after the transaction, but still has the gap: if `afterCommit` throws or the process dies, the event is lost with no retry.

**Required Architecture:**
```
Service → DB transaction → INSERT outbox_events (same transaction)
                        ↓
              Outbox Relay (CDC/polling) → Kafka → consumers
```

This guarantees at-least-once delivery via the outbox table acting as a durable write-ahead log.

---

### ARCH-02 — CRITICAL | auth-service Circular Dependency via Feign

**Affected:** auth-service → user-service (register) / user-service → auth-service (JWKS)

During user registration:
1. `auth-service` creates the auth record
2. `auth-service` calls `user-service` via Feign to create the user profile
3. `user-service` validates incoming requests via JWT (fetches JWKS from `auth-service`)

If the registration Feign call triggers JWT validation (it uses an internal auth token, not a JWT, so this is avoided in normal flow), a circular dependency exists. More critically:

- If `user-service` is down during registration, auth is created but no user profile exists
- The user can log in (auth record exists) but has no profile to display
- No compensating transaction exists

**Fix:** Use the Saga pattern or event-driven profile creation. `auth-service` publishes `account.created`; `user-service` consumes it and creates the profile. This decouples the services completely.

---

### ARCH-03 — CRITICAL | chat-service Calls friendship-service Synchronously on Message Send

**Affected:** `chat-service/.../pipeline/send/steps/CheckBlockedPairStep.java`

Every message send triggers a synchronous Feign call to `friendship-service`:
```java
FriendshipClient.checkBlockStatus(senderId, receiverId)
```

**Problems:**
1. **Latency:** message send latency = chat-service time + friendship-service round trip (~10-50ms added)
2. **Availability:** if friendship-service is down, ALL message sending fails — a friendship service outage silently breaks the entire chat system
3. **Throughput:** each message send consumes a Feign connection + a friendship-service thread
4. **No circuit breaker:** if friendship-service is slow, chat-service threads pile up waiting

**Additionally, there is an inverted logic bug:**
```java
// Bug: returns true when NOT blocked
if (response == null || response.getData() == null || !response.getData()) {
    throw new BlockedException();  // This runs when blocked=false
}
```
This causes messages to be blocked when users are NOT blocked and allowed when they ARE blocked — the opposite of the intended behavior.

**Fix:**
1. Cache block status in Redis with a short TTL (30 seconds) — friendship-service publishes invalidation events on block/unblock
2. Fix the inverted logic immediately
3. Add Resilience4j circuit breaker on the friendship-service Feign client

---

### ARCH-04 — HIGH | BrowserOAuthService Uses Two Separate Transactions

**Affected file:** `auth-service/src/main/java/com/chatweb/auth/service/impl/BrowserOAuthService.java`

```java
@Transactional
public OAuthExchangeCode consume(String code) {
    // Transaction 1: validate and delete exchange code
}

@Transactional
public AuthResponse issueTokens(OAuthExchangeCode exchangeCode) {
    // Transaction 2: create session, issue JWT
}
```

If `issueTokens()` fails after `consume()` commits:
- The exchange code is permanently consumed (cannot be replayed)
- No tokens are issued
- The user's OAuth login is irrecoverably broken
- No error recovery path exists

**Fix:** Wrap both in a single `@Transactional` boundary. Extract a private non-transactional method that is called within one outer transaction.

---

### ARCH-05 — HIGH | register() Publishes Kafka Event BEFORE User Profile Exists

**Affected:** auth-service registration flow

Sequence:
1. auth-service: `INSERT INTO users (email, password_hash)` — commits
2. auth-service: `afterCommit` → publish `auth.account-created` to Kafka
3. notification-service: consumes event → tries to fetch user profile
4. auth-service: calls user-service Feign → creates profile

Steps 2 and 4 race. If notification-service processes the Kafka event before user-service creates the profile, the notification service fetches a user profile that doesn't exist yet → NPE or 404.

**Fix:** Publish the Kafka event only after the user profile is confirmed created in user-service. With the Saga pattern: profile creation in user-service publishes `profile.created` → notification-service listens for `profile.created`.

---

### ARCH-06 — HIGH | Friendship V1 Migration Is a Placeholder — Fresh Deploy Fails

**Affected files:**
- `friendship-service/src/main/resources/db/migration/V1__initial_schema.sql`
- `user-service/src/main/resources/db/migration/V1__initial_schema.sql`

Both files contain only comments with no actual SQL. Flyway will run these migrations on fresh deployment and create empty schemas. JPA will then fail to find the expected tables.

**Impact:** Any fresh deployment (new environment, new developer machine, CI test environment) fails at startup for both friendship-service and user-service.

**Fix:** Write the actual CREATE TABLE statements for all entities in V1 migrations.

---

## 4. Internal Architecture Patterns

### Messaging Pipeline (chat-service)

The `chat-service` uses a DAG-based pipeline for message operations. Each pipeline step implements `PipelineStep<C>` with `runAfter()` dependencies. `PipelineExecutor` performs topological sort and executes steps.

**Design assessment:** The pipeline pattern is sophisticated and extensible. However, `CompletableFuture.runAsync()` in the executor breaks Spring's `@Transactional` thread-local binding. Either run steps synchronously (simpler, correct) or use proper virtual threads (Java 21) with transaction context propagation.

### Hexagonal Architecture (realtime-edge-service only)

realtime-edge-service uses ports-and-adapters:
- `adapter/in/websocket/` — WebSocket inbound adapter
- `adapter/in/kafka/` — Kafka inbound adapter
- `adapter/in/redis/` — Redis pub/sub inbound adapter
- `adapter/out/` — outbound adapters
- `application/` — domain services (ports)

Other services use flat layering (`controller/service/repository`). This inconsistency means:
- realtime-edge is harder to understand for developers used to the flat pattern
- The hexagonal boundary is not enforced — domain classes import Spring annotations directly
- The pattern is applied incompletely — `RealtimeWebSocketHandler` mixes HTTP adapter concerns with domain logic

---

## 5. Domain Boundary Analysis

| Microservice | Domain Responsibility | Boundary Leakage |
|-------------|----------------------|-----------------|
| auth-service | Authentication, token issuance | Creates user profiles (user-service domain) |
| user-service | User profiles, search | Stores auth-related timestamps |
| chat-service | Messages, rooms, reactions | Calls friendship-service for block check (cross-domain) |
| friendship-service | Friend graph, blocks | — |
| presence-service | Online/offline state | — |
| notification-service | Notification delivery | Consumes all domain events (coupling) |
| realtime-edge | WebSocket connections | Consumes friendship + chat events (coupling) |
| upload-service | Media uploads | — |
| gateway-service | Routing, auth, rate limit | — |

**auth-service creates user profiles** — the user profile is conceptually a user-service concern. auth-service should only handle credentials, not create user domain objects.

**notification-service is tightly coupled to all domains** — it consumes `auth.*`, `chat.*`, `friendship.*` events. This is acceptable for a notification service but means all domain event schema changes require notification-service updates.

---

## 6. common-events SharedEventCatalog Coupling

**Affected file:** `common-events/src/main/java/com/chatweb/common/events/SharedEventCatalog.java`

All domain event types are defined in a single shared module imported by every service. This creates:

1. **Build coupling:** changing any event in common-events requires rebuilding and redeploying all services
2. **Schema coupling:** adding a field to `ChatMessageSentEvent` forces all consumers to handle the new field
3. **No versioning:** `ChatMessageSentEvent.v1` → `ChatMessageSentEvent.v2` migration is not possible without a coordinated multi-service deploy
4. **Circular dependency risk:** common-events imports common-core, which services also import — transitive dependency chains

**Fix:** Each service should own its own event schema. Consumers define their own DTO for the events they consume. Use schema registry for compatibility enforcement.

---

## 7. Service Dependency Graph (Startup Order)

For correct startup, services must start in this order:

```
1. PostgreSQL, Redis, Kafka, ZooKeeper
2. auth-service (needs PostgreSQL + Kafka)
3. user-service (needs PostgreSQL + Kafka)
4. friendship-service (needs PostgreSQL + Kafka)
5. chat-service (needs PostgreSQL + Kafka + user-service + friendship-service [Feign])
6. notification-service (needs PostgreSQL + Kafka + Redis)
7. presence-service (needs Redis)
8. realtime-edge-service (needs Redis + Kafka + auth-service [JWKS])
9. upload-service (needs Cloudinary credentials)
10. gateway-service (needs all downstream services for readiness probe)
```

**Issue:** The gateway's readiness check blocks until ALL services are healthy. In rolling deploys, this causes a downtime window while services restart sequentially.

---

## 8. Architecture Score

| Dimension | Score | Notes |
|-----------|-------|-------|
| Service decomposition | 6/10 | Well-separated domains; auth/user coupling is the main issue |
| API design | 6/10 | REST conventions followed; some auth gaps |
| Event-driven design | 4/10 | Kafka used correctly in concept; no outbox, wrong threading |
| Resilience patterns | 4/10 | Circuit breakers only at gateway; no fallbacks on sync calls |
| Data consistency | 3/10 | No outbox, separate transactions, race conditions |
| Security architecture | 4/10 | JWT used; multiple bypass vectors; header trust issues |
| **Overall** | **5/10** | Solid foundation with critical correctness gaps |
