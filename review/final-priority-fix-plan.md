# Final Priority Fix Plan — chatappBE
> Date: 2026-05-20 | Audience: Engineering team, Tech Lead

---

## Overview

This plan covers all 81 issues identified across the 12-review deep-dive. Issues are grouped by severity tier, then by service, with explicit dependency ordering, rollback risk classification, and complexity estimates.

**Legend:**
- **Complexity:** Quick (< 2h) | Small (half-day) | Medium (1-3 days) | Large (1-2 weeks) | Epic (2+ weeks)
- **Rollback risk:** Low (revert file) | Medium (DB migration) | High (data loss possible) | Critical (service outage risk)
- **Dependency:** `→` means "must be done after"

---

## TIER 0 — Fix Before Any Production Traffic

These are system-breaking bugs or active security compromises. They must be fixed immediately regardless of other work.

---

### T0-01 | Rotate ALL Exposed Credentials
**Service:** External providers (Google, Resend, Cloudinary)  
**Issue:** SEC-01 — live credentials in git history  
**Action:**
1. Go to Google Cloud Console → revoke `GOCSPX-U_I6V5Q8mi...` OAuth secret → generate new
2. Go to Resend dashboard → revoke `re_gJP3mbc7_...` → generate new
3. Go to Cloudinary dashboard → revoke `KfHivMv0wUQ9D-89rVx8Wu5U12w` → generate new
4. Update `.env.production` with new values
5. Restart all services
6. Run `git filter-repo` or BFG Repo Cleaner to scrub git history
7. Force-push to all branches (coordinate with team)
8. Rotate any GitHub deploy keys that may have read the repo

**Complexity:** Quick (credential rotation) + Medium (git history scrub)  
**Rollback risk:** None (adding new credentials)  
**Dependency:** None — must be first

---

### T0-02 | Fix CheckBlockedPairStep Inverted Logic
**Service:** chat-service  
**Issue:** ARCH-03 — block check logic is inverted; all DMs blocked, blocked users can message  
**File:** `chat-service/.../pipeline/send/steps/CheckBlockedPairStep.java`

```java
// CURRENT (WRONG):
if (response == null || response.getData() == null || !response.getData()) {
    throw new BlockedException();  // Throws when NOT blocked
}

// FIX:
if (response == null || response.getData() == null || response.getData()) {
    throw new BlockedException();  // Throws when blocked
}
```

**Complexity:** Quick  
**Rollback risk:** Low (single line change)  
**Dependency:** None

---

### T0-03 | Write V1 Flyway Migrations
**Services:** friendship-service, user-service  
**Issue:** DEBT-04 — V1 SQL files are empty placeholders; fresh deploy fails  
**Files:**
- `friendship-service/src/main/resources/db/migration/V1__initial_schema.sql`
- `user-service/src/main/resources/db/migration/V1__initial_schema.sql`

**Action:** Generate complete CREATE TABLE DDL from JPA entities:
```sql
-- friendship-service V1
CREATE TABLE friendship (
    id UUID PRIMARY KEY,
    requester_id UUID NOT NULL,
    addressee_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_friendship_pair UNIQUE (requester_id, addressee_id)
);
CREATE INDEX idx_friendship_requester ON friendship(requester_id);
CREATE INDEX idx_friendship_addressee ON friendship(addressee_id);
```

**Complexity:** Small  
**Rollback risk:** Low (new environments only; do not run on existing DB with `ddl-auto: update` schema)  
**Dependency:** None

---

### T0-04 | Add groupId to FriendshipKafkaEventConsumer
**Service:** realtime-edge-service  
**Issue:** KAFKA-01 — random group per restart; drops friendship events  
**File:** `realtime-edge-service/.../adapter/in/kafka/FriendshipKafkaEventConsumer.java`

```java
@KafkaListener(
    topics = { TOPIC_FRIENDSHIP_REQUEST_SENT, TOPIC_FRIENDSHIP_ACCEPTED, ... },
    groupId = "realtime-edge-friendship-group"  // Add this
)
```

**Complexity:** Quick  
**Rollback risk:** Low  
**Dependency:** None

---

### T0-05 | Remove Hardcoded Credential Defaults from application.yaml
**Services:** chat-service, all services  
**Issue:** SEC-01 — Cloudinary secret baked into JAR as default  
**Files:**
- `chat-service/src/main/resources/application.yaml` — remove `:KfHivMv0wUQ9D-89rVx8Wu5U12w`
- All `application.yaml` files — remove any `${VAR:secret-value}` patterns

```yaml
# Change from:
api-secret: ${CLOUDINARY_API_SECRET:KfHivMv0wUQ9D-89rVx8Wu5U12w}
# To:
api-secret: ${CLOUDINARY_API_SECRET}
```

**Complexity:** Quick  
**Rollback risk:** Low (services will fail at startup if env var missing — correct behavior)  
**Dependency:** T0-01 must complete first (have new credentials ready)

---

## TIER 1 — CRITICAL Issues (Fix Within Week 1-2)

---

### T1-01 | Fix Pipeline Threading — Remove runAsync() or Preserve Transaction Context
**Service:** chat-service  
**Issues:** KAFKA-06, DEBT-17 — CompletableFuture.runAsync() breaks @Transactional  
**File:** `chat-service/.../application/pipeline/PipelineExecutor.java`

**Option A (Recommended — simpler):** Run pipeline synchronously
```java
// Remove CompletableFuture.runAsync() wrapping
// Execute steps directly on the calling thread
// @Transactional on MessageCommandService.send() wraps the entire pipeline
```

**Option B:** Use Virtual Threads (Java 21) with transaction propagation
```java
// Configure TaskExecutor to use virtual threads
// Pass TransactionSynchronizationManager context explicitly
```

**Complexity:** Medium (Option A) / Large (Option B)  
**Rollback risk:** Medium (pipeline behavior change; test all message operations)  
**Dependency:** Must complete before T1-02
**Status:** Done (implemented Option A: synchronous execution)

---

### T1-02 | Add afterCommit Guards to Edit/Delete/Reaction Publish Steps
**Service:** chat-service  
**Issue:** KAFKA-07 — phantom events published before DB commits  
**Files:**
- `chat-service/.../pipeline/edit/steps/PublishMessageEditedEventStep.java`
- `chat-service/.../pipeline/delete/steps/PublishMessageDeletedEventStep.java`
- `chat-service/.../pipeline/reaction/steps/PublishReactionEventStep.java`

```java
// Add to each publish step:
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        kafkaTemplate.send(topic, key, event);
    }
});
```

**Complexity:** Small  
**Rollback risk:** Low  
**Dependency:** T1-01 (pipeline must be synchronous for afterCommit to work correctly)
**Status:** Done (after-commit guards added)

---

### T1-03 | Add TTL to Redis Presence Keys
**Service:** presence-service  
**Issue:** REDIS-04 — presence HASHes and SETs have no TTL; memory leak  
**File:** `presence-service/.../state/redis/RedisPresenceEphemeralStateStore.java`

```java
// After adding presence data:
redisTemplate.expire("presence::user:" + userId + ":connections", 90, TimeUnit.SECONDS);
redisTemplate.expire("presence::user:" + userId + ":online", 90, TimeUnit.SECONDS);
redisTemplate.expire("presence::user:" + userId + ":rooms", 90, TimeUnit.SECONDS);
// Refresh TTL on each heartbeat/activity
```

**Complexity:** Small  
**Rollback risk:** Low  
**Dependency:** Check REDIS-09 (prefix collision) before adding TTL to data keys
**Status:** Done (TTL refresh added for ephemeral user/room presence keys)

---

### T1-04 | Add TTL to Redis Session Registry Keys
**Service:** realtime-edge-service  
**Issue:** REDIS-05 — session registry keys have no TTL; memory leak on crash  
**File:** `realtime-edge-service/.../connection/RedisRealtimeSessionRegistry.java`

```java
// After registering session:
redisTemplate.expire("session:registry:" + sessionId, 120, TimeUnit.SECONDS);
redisTemplate.expire("session:user:" + userId + ":sessions", 120, TimeUnit.SECONDS);
// Refresh on each WS frame received
```

**Complexity:** Small  
**Rollback risk:** Low  
**Dependency:** T1-03 (coordinate TTL strategy)
**Status:** Done (TTL refresh added for session registry index keys)

---

### T1-05 | Remove Full JWT from Redis Ticket
**Service:** realtime-edge-service  
**Issue:** REDIS-06, SEC-16 — full JWT stored in Redis ticket  
**Files:**
- `realtime-edge-service/.../adapter/in/websocket/JwtHandshakeInterceptor.java`
- `realtime-edge-service/.../controller/RealtimeTicketController.java`

```java
// Store only userId in ticket:
redisTemplate.opsForValue().set("ticket:" + ticket, userId.toString(), 30, SECONDS);

// At WS upgrade, require JWT in Authorization header:
String jwt = request.getHeaders().getFirst("Authorization");
// Validate JWT, extract userId, verify matches ticket userId
```

**Complexity:** Small  
**Rollback risk:** Low (client must send Authorization header at WS upgrade — verify frontend supports this)  
**Dependency:** None
**Status:** Done (ticket stores userId only; handshake validates Authorization JWT and userId match)

---

### T1-06 | Add USER to All Dockerfiles and JVM Memory Flags
**Services:** ALL 9 services  
**Issues:** PROD-01, PROD-03 — containers run as root; no JVM memory config

```dockerfile
# Add before CMD in all Dockerfiles:
RUN addgroup -S app && adduser -S app -G app
USER app

# Change CMD to:
CMD ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
     "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
```

**Complexity:** Small (template change ×9)  
**Rollback risk:** Low  
**Dependency:** None

---

### T1-07 | Fix Redis maxmemory and Connection Pool
**Issues:** REDIS-01, REDIS-02, REDIS-03  
**Files:** `docker-compose.yml`, all service `application.yaml`

```yaml
# docker-compose.yml:
redis:
  command: redis-server --maxmemory 1gb --maxmemory-policy noeviction

# All service application.yaml:
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4
```

**Complexity:** Quick  
**Rollback risk:** Low  
**Dependency:** None
**Status:** Done (duplicate datasource key removed; hikari merged into primary datasource block)

---

### T1-08 | Fix auth-service ddl-auto and Add Flyway
**Service:** auth-service, user-service  
**Issue:** PROD-04 — ddl-auto: update risks data loss  

**Action:**
1. Set `ddl-auto: validate` in production profiles
2. Dump current schema: `pg_dump --schema-only auth_db > schema.sql`
3. Create `V1__initial_schema.sql` from the dump (one-time migration)
4. Add Flyway dependency to auth-service build.gradle
5. Test on a copy of production data

**Complexity:** Medium  
**Rollback risk:** High (schema migration; requires careful testing and full DB backup before running)  
**Dependency:** Full DB backup must exist before applying; coordinate deployment window

---

### T1-09 | Fix HikariCP YAML Duplicate Key in notification-service
**Service:** notification-service  
**Issue:** PROD-08 / DEBT-20 — duplicate YAML key silently drops pool size config  
**File:** `notification-service/src/main/resources/application.yaml`

```yaml
# Merge both spring.datasource.hikari blocks into one:
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**Complexity:** Quick  
**Rollback risk:** Low  
**Dependency:** None

---

### T1-10 | Add JWT Issuer Validator at Gateway
**Service:** gateway-service  
**Issue:** SEC-05 — any JWT signed with a known kid is accepted regardless of issuer  
**File:** `gateway-service/src/main/java/com/chatweb/gateway/config/SecurityConfig.java`

```java
OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
    new JwtTimestampValidator(Duration.ofSeconds(clockSkewSeconds)),
    new JwtIssuerValidator("http://auth-service:8081")  // Add this
);
```

**Complexity:** Quick  
**Rollback risk:** Low  
**Dependency:** Confirm auth-service issues JWTs with `iss: http://auth-service:8081`
**Status:** Done (gateway now validates configured issuer)

---

## TIER 2 — HIGH Issues (Fix Within Weeks 3-5)

---

### T2-01 | Add Prometheus Metrics to All Services
**Services:** auth, user, chat, friendship, notification, presence, upload (7 services)  
**Issue:** PROD-10

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true
```

**Complexity:** Quick ×7  
**Dependency:** None

---

### T2-02 | Add Structured JSON Logging
**Services:** ALL 9 services  
**Issue:** PROD-09 — no logback.xml; text format logs

Add `logback-spring.xml` to each service with:
- JSON encoder (Logstash or net.logstash.logback)
- Fields: timestamp, level, service, traceId, thread, message

**Complexity:** Small ×9  
**Dependency:** None

---

### T2-03 | Disable DEBUG Logging in Production
**Service:** realtime-edge-service  
**Issue:** PROD-07  
**File:** `realtime-edge-service/src/main/resources/application.yaml`

```yaml
logging:
  level:
    com.chatweb.realtime: INFO
    org.springframework.web.socket: WARN
```

**Complexity:** Quick  
**Rollback risk:** Low  
**Dependency:** None

---

### T2-04 | Add Feign Timeouts and Circuit Breakers
**Services:** chat-service (→ friendship-service), auth-service (→ user-service)  
**Issues:** PROD-12, PROD-13

```yaml
feign:
  client:
    config:
      friendship-service:
        connectTimeout: 500
        readTimeout: 1000
      user-service:
        connectTimeout: 1000
        readTimeout: 2000
```

Add `@CircuitBreaker(name = "friendship-service", fallbackMethod = "...")` to Feign clients.

**Complexity:** Small  
**Rollback risk:** Low  
**Dependency:** Decide fallback behavior (allow message if friendship-service is down, or reject)

---

### T2-05 | Add Graceful Shutdown to All Services
**Services:** ALL  
**Issue:** PROD-15

```yaml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
server:
  shutdown: graceful
```

For Kafka consumers:
```yaml
spring:
  kafka:
    listener:
      shutdown-timeout: 30000
```

**Complexity:** Quick ×9  
**Rollback risk:** Low  
**Dependency:** None

---

### T2-06 | Replace Redis KEYS * with INCR Counter for Session Count
**Service:** realtime-edge-service  
**Issue:** REDIS-10 — KEYS * is O(N) blocking  
**File:** `realtime-edge-service/.../connection/RedisRealtimeSessionRegistry.java`

```java
// Replace keys-based count with a dedicated counter:
redisTemplate.opsForValue().increment("session:count");   // on add
redisTemplate.opsForValue().decrement("session:count");   // on remove
redisTemplate.opsForValue().get("session:count");          // for count queries
```

**Complexity:** Small  
**Rollback risk:** Low  
**Dependency:** T1-04 (session registry keys must have TTL; counter must account for TTL expirations — use a maintenance job to reconcile)
**Status:** Done (counter-based active session tracking implemented)

---

### T2-07 | Fix Thread.sleep() on Redis Pub/Sub Listener
**Service:** realtime-edge-service  
**Issue:** REDIS-08  
**File:** `realtime-edge-service/.../dispatch/EdgeDeliveryHandoffPublisher.java`

Remove `Thread.sleep()`. If ordering delay is needed, use an async scheduled delivery:
```java
scheduler.schedule(() -> deliver(payload), 50, TimeUnit.MILLISECONDS);
```

**Complexity:** Small  
**Rollback risk:** Low  
**Dependency:** None
**Status:** Done (`Thread.sleep()` removed from retry backoff path)

---

### T2-08 | Add WebSocket Frame Size Limit and Native Ping
**Service:** realtime-edge-service  
**Issues:** SEC-17, WS frame size  
**File:** `realtime-edge-service/src/main/java/com/chatweb/realtime/config/WebSocketConfig.java`

```java
registry.addHandler(handler, "/ws")
        .setMaxTextMessageBufferSize(64 * 1024)
        .setMaxBinaryMessageBufferSize(64 * 1024)
        .setSendBufferSizeLimit(512 * 1024)
        .setSendTimeLimit(10000);
```

**Complexity:** Quick  
**Rollback risk:** Low  
**Dependency:** None

---

### T2-09 | Add InternalServiceAuthFilter to common-security, Remove Duplicates
**Services:** user-service, friendship-service, presence-service → common-security  
**Issues:** SEC-13, DEBT-11

1. Move canonical `InternalServiceAuthFilter` to `common-security`
2. Fix the empty-string default token issue:
   ```java
   @PostConstruct
   public void validate() {
       if (token == null || token.isBlank()) {
           throw new IllegalStateException("INTERNAL_AUTH_TOKEN must not be blank");
       }
   }
   ```
3. Add common-security dependency to user, friendship, presence build.gradle
4. Delete the three service-specific copies

**Complexity:** Small  
**Rollback risk:** Low  
**Dependency:** None

---

### T2-10 | Fix FriendshipEventDedupeGuard Wiring
**Service:** realtime-edge-service  
**Issue:** DEBT-07 — guard exists but not used  
**File:** `realtime-edge-service/.../adapter/in/kafka/FriendshipKafkaEventConsumer.java`

```java
@KafkaListener(...)
public void handle(ConsumerRecord<String, FriendshipEvent> record) {
    String eventId = record.headers().lastHeader("event-id").value().toString();
    if (dedupeGuard.isDuplicate(eventId)) return;  // Wire the guard
    // ... process event
}
```

**Complexity:** Quick  
**Rollback risk:** Low  
**Dependency:** T0-04 (groupId must be set first)
**Status:** Done (dedupe guard wired; stable consumer groupId added)

---

### T2-11 | Fix BrowserOAuthService Separate Transactions
**Service:** auth-service  
**Issue:** ARCH-04 — consume() and issueTokens() in separate transactions  
**File:** `auth-service/.../service/impl/BrowserOAuthService.java`

```java
@Transactional  // Single wrapping transaction
public AuthResponse consumeAndIssueTokens(String code) {
    OAuthExchangeCode exchangeCode = consumeCodeInternal(code);   // private
    return issueTokensInternal(exchangeCode);                      // private
}
```

**Complexity:** Small  
**Rollback risk:** Low  
**Dependency:** None

---

### T2-12 | Fix CORS — Remove Wildcard Headers with Credentials
**Issue:** SEC-24  
**File:** `common-web/src/main/java/com/chatweb/common/web/cors/CorsProperties.java`

```yaml
cors:
  allowed-headers:
    - Authorization
    - Content-Type
    - X-Requested-With
    - X-Trace-Id
  # Remove: allowed-headers: "*"
```

**Complexity:** Quick  
**Rollback risk:** Low (may break frontend if it sends custom headers — test thoroughly)  
**Dependency:** Audit all frontend HTTP requests to confirm headers used

---

### T2-13 | Add @PrePersist / @PreUpdate to Friendship Entity
**Service:** friendship-service  
**Issue:** NPE on save if timestamps not set  
**File:** `friendship-service/.../entity/Friendship.java`

```java
@PrePersist
public void prePersist() {
    this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    this.updatedAt = this.createdAt;
}

@PreUpdate
public void preUpdate() {
    this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
}
```

**Complexity:** Quick  
**Rollback risk:** Low  
**Dependency:** None

---

### T2-14 | Remove TOPIC_SYSTEM_RETRY, Dead Kafka Constants
**Service:** common-kafka, notification-service  
**Issues:** DEBT-08, DEBT-01 — dead constants and dead listeners

1. Delete `TOPIC_SYSTEM_RETRY` from `KafkaTopics.java`
2. Remove `@KafkaListener` for `chat.message-pinned` and `chat.message-unpinned` (or implement the publishers in chat-service)

**Complexity:** Quick  
**Rollback risk:** Low  
**Dependency:** Decision required on pin/unpin feature timeline

---

## TIER 3 — Medium-Effort Architectural Improvements (Weeks 6-8)

---

### T3-01 | Implement Transactional Outbox Pattern (auth-service + chat-service)
**Services:** auth-service, chat-service  
**Issue:** ARCH-01, KAFKA-05

**High-level implementation:**
1. Create `outbox_events` table in each service DB:
   ```sql
   CREATE TABLE outbox_events (
       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
       topic VARCHAR(255) NOT NULL,
       partition_key VARCHAR(255),
       payload JSONB NOT NULL,
       created_at TIMESTAMP NOT NULL DEFAULT NOW(),
       published_at TIMESTAMP
   );
   ```
2. In the same DB transaction, INSERT to `outbox_events`
3. Add scheduled job (every 100ms) that polls for unpublished events → publish to Kafka → mark published
4. For CDC approach: use Debezium with PostgreSQL WAL reader

**Complexity:** Large  
**Rollback risk:** Medium (schema migration required)  
**Dependency:** T1-08 (Flyway must be in place before adding new migrations)

---

### T3-02 | Upgrade jjwt to 0.12.6
**Service:** auth-service  
**Issue:** DEBT-15

```groovy
// build.gradle
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
```

Migrate builder API:
```java
// From 0.11.x:
Jwts.builder().setSubject(id).setIssuedAt(now).signWith(key, HS256)
// To 0.12.x:
Jwts.builder().subject(id).issuedAt(now).signWith(key)
```

**Complexity:** Medium (API migration touches all JWT creation/parsing code)  
**Rollback risk:** Medium (JWT format change; ensure all services that validate tokens are updated simultaneously)  
**Dependency:** Coordinate deployment of all services simultaneously — old tokens are backward compatible but new library must be consistent

---

### T3-03 | Migrate to Kafka Streams / Replace Redis Pub/Sub with Persistent Delivery
**Services:** notification-service, realtime-edge-service  
**Issue:** REDIS-07 — fire-and-forget pub/sub

Replace Redis pub/sub for cross-instance delivery with Redis Streams:
```java
// Publish:
redisTemplate.opsForStream().add("delivery:" + userId, Map.of("payload", json));

// Consume:
redisTemplate.opsForStream().read(Consumer.from("edge-group", instanceId),
    StreamReadOptions.empty().count(100),
    StreamOffset.create("delivery:" + userId, ReadOffset.lastConsumed()));
```

**Complexity:** Epic (architectural change to delivery path)  
**Rollback risk:** High (changes the delivery contract; requires careful rollout)  
**Dependency:** T1-04 (session TTL must be reliable first)

---

### T3-04 | Standardize Package Structure Across All Services
**All services**  
**Issue:** PKG-01, PKG-02, PKG-03

1. Rename `configuration/` → `config/` in auth-service, user-service, notification-service, presence-service
2. Extract domain model layer in auth-service (stop exposing JPA entities to service layer)
3. Consolidate SecurityConfig and SwaggerConfig to common-security base classes

**Complexity:** Medium  
**Rollback risk:** Low (refactoring; no behavior change)  
**Dependency:** None (coordinate with team to avoid merge conflicts)

---

### T3-05 | Add Comprehensive Test Suite
**All services**  
**Issue:** DEBT test coverage = 0%

Priority order:
1. Unit tests for pipeline steps (validation, block check, persistence, publish)
2. Integration tests for Kafka consumer/producer flows (Testcontainers)
3. WebSocket integration tests for message routing (Spring WebSocket test support)
4. Security tests for JWT validation, CORS, rate limiting

**Complexity:** Epic  
**Rollback risk:** None  
**Dependency:** T1-01 (pipeline must be synchronous — async pipeline is untestable without extensive mocking)

---

## TIER 4 — Low-Effort Cleanup (Ongoing)

| Task | Service | Issue | Complexity |
|------|---------|-------|-----------|
| Delete GenerateSequenceStep, wire runAfter correctly | chat-service | DEBT-06 | Quick |
| Delete TOPIC_SYSTEM_RETRY constant | common-kafka | DEBT-08 | Quick |
| Delete RedisEventRegistry/Dispatcher | common-redis | DEBT-09 | Quick |
| Fix rootProject.name = 'demo' | root settings.gradle | NAME-05 | Quick |
| Remove `I` prefix from ITimeRedisCache | common-redis-cache | NAME-01 | Quick |
| Standardize Friend vs Friendship naming | friendship-service | NAME-02 | Small |
| Replace magic strings with enum in MessageMutationEventConsumer | notification-service | NAME-06 | Small |
| Fix stale JavaDoc in RealtimeWebSocketHandler | realtime-edge | DEBT-03 | Quick |
| Remove @SuppressWarnings("unchecked") via TypeReference | realtime-edge | DEBT-16 | Quick |
| Remove @Transactional from nextSeq() | chat-service | DEBT-18 | Quick |
| Delete MessageCacheService or add @Service | chat-service | DEBT-05 | Quick |
| Reduce JWT clock skew from 60s to 5s | gateway | SEC-25 | Quick |
| Add security headers at gateway | gateway | SEC-23 | Small |
| Remove GET /friends/blocks/by-others endpoint | friendship-service | SEC-21 | Quick |
| Restrict GET /presence/global with auth + pagination | presence-service | SEC-20 | Small |
| Add AUTH to GET /users/search | user-service | SEC-18 | Quick |
| Add auth + size cap to POST /rooms/members/bulk | chat-service | SEC-19 | Small |
| Rename phaseb.local.validation.enabled | all services | NAME-15 | Quick |

---

## Dependency Order Summary

```
T0-01 (rotate creds)
  └→ T0-05 (remove hardcoded defaults)

T0-02 (fix block logic)        — independent
T0-03 (write V1 migrations)    — independent
T0-04 (add groupId)            — independent

T0-03 → T1-08 (auth Flyway)
T1-08 → T3-01 (outbox pattern)

T1-01 (sync pipeline)
  └→ T1-02 (afterCommit guards)
       └→ T3-05 (test suite — pipeline must be sync to test)

T1-03 (presence TTL)
T1-04 (session TTL)
  └→ T2-06 (INCR counter for session count)
       └→ T3-03 (Redis Streams delivery)

T2-09 (common-security consolidation)
  → Remove duplicate InternalServiceAuthFilter copies
```

---

## Critical Production Blockers Checklist

The following must ALL be resolved before routing any real user traffic:

- [ ] T0-01 — Credentials rotated
- [ ] T0-02 — Block logic fix (currently ALL messages are rejected OR blocked users can message)
- [ ] T0-03 — V1 migrations written (fresh deploy fails)
- [ ] T0-04 — Friendship consumer groupId added (friendship events dropped on restart)
- [ ] T0-05 — Hardcoded secrets removed from YAML
- [x] T1-01 — Pipeline threading fixed (data integrity)
- [ ] T1-06 — Non-root containers + JVM flags
- [ ] T1-07 — Redis maxmemory policy set
- [ ] T1-08 — auth-service ddl-auto switched to validate + Flyway
- [x] T1-09 — HikariCP YAML duplicate key fixed (notification-service undersized pool)
- [x] T1-10 — JWT issuer validator added at gateway
