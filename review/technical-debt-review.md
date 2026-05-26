# Technical Debt Review — chatappBE
> Deep-dive audit | Date: 2026-05-20 | Reviewer: multi-agent analysis

---

## 1. TODO / FIXME Inventory

### DEBT-01 — HIGH | MESSAGE_PINNED / MESSAGE_UNPINNED Kafka Publishers Unimplemented

**Affected:** `notification-service` has active `@KafkaListener` entries for:
- `chat.message-pinned`
- `chat.message-unpinned`

No service in the codebase publishes to these topics. The listener methods exist and allocate consumer threads and group coordinator resources in Kafka, but will never receive a message.

The corresponding chat-service pipeline steps for pin/unpin exist as stubs or were never created.

**Risk:** Dead consumer groups accumulate in Kafka. When the pin feature is eventually implemented, developers may forget that consumers are already registered, leading to duplicate processing surprises.

**Fix:** Either implement the publishers in chat-service (completing the feature), or remove the dead listeners immediately.

---

### DEBT-02 — HIGH | NotificationWebSocketPublisher DURABLE_FIRST Path Unimplemented

**Affected:** `notification-service/.../service/impl/NotificationWebSocketPublisher.java` (inferred from architecture)

The notification delivery strategy has two paths:
- `REALTIME_FIRST`: try WebSocket delivery first, fall back to push
- `DURABLE_FIRST`: store notification first, then deliver

The `DURABLE_FIRST` branch has a comment indicating future implementation. Currently, all notifications use `REALTIME_FIRST`, meaning:
- A user who is offline when a message arrives has no guaranteed delivery
- After reconnect, the user must poll for missed notifications
- The notification record is created in DB but the push path is not triggered

**Fix:** Implement push notification delivery for offline users. Integrate with a push provider (FCM/APNs).

---

### DEBT-03 — MEDIUM | Stale JavaDoc Misleads Developers

**Affected:** `realtime-edge-service/.../adapter/in/websocket/RealtimeWebSocketHandler.java`

```java
/**
 * Placeholder: future implementation will extract userId from handshake attributes
 * set by the JwtHandshakeInterceptor.
 */
@Override
public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    // ... FULLY IMPLEMENTED ...
}
```

The JavaDoc says "Placeholder: future implementation" but the method is complete. This stale comment will mislead future developers into thinking this code needs replacement.

**Fix:** Remove or update the JavaDoc to describe what the method actually does.

---

## 2. Dead Code

### DEBT-04 — CRITICAL | V1 Flyway Migrations Are Empty Placeholders

**Affected files:**
- `friendship-service/src/main/resources/db/migration/V1__initial_schema.sql`
- `user-service/src/main/resources/db/migration/V1__initial_schema.sql`

Both files contain only SQL comments with no actual DDL statements. Flyway runs these migrations on fresh deployment, marking them as applied. Hibernate then expects tables to exist (because entities are defined) but finds nothing.

**Impact:** Fresh deployments to new environments (dev machine, CI, staging) will fail at startup for both services. This is not a latent risk — it actively blocks new environment setup.

**Fix:** Write the complete `CREATE TABLE` statements for all entities in both V1 migrations.

---

### DEBT-05 — HIGH | MessageCacheService Is Not a Spring Bean

**Affected file:** `chat-service/src/main/java/com/chatweb/chat/infrastructure/cache/MessageCacheService.java`

```java
// Missing @Service annotation
public class MessageCacheService {
    @Autowired
    private StringRedisTemplate redisTemplate;  // Never injected — always null
    // ...
}
```

This class is never instantiated by Spring. The `@Autowired` fields remain null. Any call to this class would throw `NullPointerException`. Since the class is never injected anywhere, the NPE never occurs — the code is simply dead.

All Redis-based message caching described in this class is non-functional.

**Fix:** Either add `@Service` and inject it into `MessageQueryService`, or delete the class and remove all cache-related logic.

---

### DEBT-06 — HIGH | GenerateSequenceStep Is a No-Op

**Affected file:** `chat-service/.../pipeline/send/steps/GenerateSequenceStep.java`

```java
@Override
public void execute(SendMessageContext context) {
    // Sequence is allocated atomically together with message persistence.
    context.setSeq(0L);
}
```

This step:
1. Sets `seq = 0` as a placeholder
2. The actual sequence is set in `PersistMessageStep` (Redis INCR)
3. The 0 value from this step is overwritten immediately after

The step exists only as a dependency anchor in the DAG (`PersistMessageStep.runAfter(GenerateSequenceStep)`). Its functional existence is dead code. The comment in the method body even acknowledges this.

**Fix:** Remove the step. Update `PersistMessageStep`'s `runAfter()` to depend on `ResolveRoomStep` or whatever `GenerateSequenceStep` previously depended on.

---

### DEBT-07 — HIGH | FriendshipEventDedupeGuard Is Unused

**Affected file:** `realtime-edge-service/.../FriendshipEventDedupeGuard.java`

This class appears to implement event deduplication for friendship events in realtime-edge. However, no `@KafkaListener` in realtime-edge invokes this guard. The `FriendshipKafkaEventConsumer` processes events without any deduplication.

**Consequence:** If a friendship event is delivered twice (Kafka at-least-once guarantee), realtime-edge will send the friend notification twice. The dedup guard exists but is wired to nothing.

**Fix:** Inject `FriendshipEventDedupeGuard` into `FriendshipKafkaEventConsumer` and call it before processing.

---

### DEBT-08 — MEDIUM | `TOPIC_SYSTEM_RETRY` Constant Is Dead

```java
// common-kafka/KafkaTopics.java
public static final String TOPIC_SYSTEM_RETRY = "system.retry";
```

No producer publishes to this topic. No consumer subscribes. The constant implies retry infrastructure that does not exist.

**Fix:** Delete. If retry infrastructure is planned, track it in an issue.

---

### DEBT-09 — MEDIUM | `common-redis` RedisEventRegistry and RedisEventDispatcher Are Unused

**Affected:** `common-redis/` — `RedisEventRegistry`, `DefaultRedisEventRegistry`, `RedisEventDispatcher`

These abstractions are defined in common-redis but never imported or used by any service. The realtime-edge service implements its own Redis pub/sub handling without using the common registry/dispatcher.

**Fix:** Delete from common-redis. If services should use them, add an implementation guide.

---

### DEBT-10 — MEDIUM | Old Package Exclusions in build.gradle

**Affected:** One or more service `build.gradle` files contain package exclude patterns referencing packages that may no longer exist:

```groovy
// Excluding old auth package that was renamed
exclude 'com.example.auth.*'
```

These exclusions were added during package renaming (`com.example.*` → `com.chatweb.*`) and were never cleaned up. They silently exclude any future class placed in those packages.

**Fix:** Remove stale exclusion patterns. Verify via `./gradlew compileJava` that no required classes are excluded.

---

## 3. Code Duplication

### DEBT-11 — HIGH | InternalServiceAuthFilter Duplicated Across 3 Services

**Affected:**
- `user-service/src/main/java/com/chatweb/user/configuration/InternalServiceAuthFilter.java`
- `friendship-service/src/main/java/com/chatweb/friendship/configuration/InternalServiceAuthFilter.java`
- `presence-service/src/main/java/com/chatweb/presence/configuration/InternalServiceAuthFilter.java`

Three independent copies of the same security filter. The common module `common-security` exists for exactly this purpose but is not used.

**Divergence risk:** A security fix (e.g., SEC-13 — empty string default for token) must be applied manually to all three copies. Omitting one copy leaves a security hole in that service.

**Fix:** Move the canonical implementation to `common-security`. Each service imports via Gradle dependency and gets the fix automatically.

---

### DEBT-12 — HIGH | DatabaseSchemaFixer Duplicated Across 2 Services

**Affected:**
- `auth-service/src/main/java/com/chatweb/auth/configuration/DatabaseSchemaFixer.java`
- `notification-service/src/main/java/com/chatweb/notification/configuration/DatabaseSchemaFixer.java`

These classes perform ad-hoc schema repairs at startup — a sign of production incidents fixed with code rather than proper migrations. Both classes run DDL directly via JDBC, bypassing Flyway versioning.

**Risk:** The "fix" runs on every startup. If it modifies a table that a later Flyway migration also touches, conflicts arise.

**Fix:** Translate the schema fixers into proper Flyway migrations (V2 or V3). Delete the Java classes.

---

### DEBT-13 — HIGH | SecurityConfig/SwaggerConfig Duplicated Across 6+ Services

Each service has its own `SecurityConfig` and `SwaggerConfig` class. The majority of the configuration is identical (permit Swagger paths, configure JWT, set CORS). Only route-specific `authorizeHttpRequests()` rules differ.

**Duplication count:**
- SecurityConfig: auth, user, chat, friendship, notification, presence, realtime-edge = 7 copies
- SwaggerConfig: auth, user, chat, friendship, notification, presence = 6 copies

A change to the shared security pattern (e.g., SEC-03 — removing `@Component` from JwtAuthenticationFilter) must be applied to all 7 SecurityConfigs individually.

**Fix:** Create `common-security` base security configurations with extension points. Each service extends the base and overrides only the route-specific rules.

---

### DEBT-14 — MEDIUM | `JwtHelper.extractUserId()` Called in Every Service

Every service that handles authentication extracts the user ID from the JWT using the same pattern:
```java
String userId = (String) authentication.getPrincipal();
// or
Jwt jwt = (Jwt) authentication.getPrincipal();
UUID userId = UUID.fromString(jwt.getSubject());
```

This logic is duplicated across every controller method in every authenticated service. A change to how userId is represented in JWT claims requires updating every service.

**Fix:** Add `JwtUtils.extractUserId(Authentication auth)` to `common-security`. Controllers call this static utility method.

---

## 4. Deprecated API Usage

### DEBT-15 — HIGH | jjwt 0.11.5 Uses Deprecated APIs

**Affected:** `auth-service/build.gradle`

```groovy
implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
implementation 'io.jsonwebtoken:jjwt-impl:0.11.5'
implementation 'io.jsonwebtoken:jjwt-jackson:0.11.5'
```

jjwt 0.11.x uses deprecated builder APIs:
```java
Jwts.builder().setSubject(userId)  // deprecated in 0.12.x
Jwts.parserBuilder()               // deprecated in 0.12.x
```

jjwt 0.12.6 introduced breaking API changes. The codebase must be updated before jjwt 0.11.x reaches end-of-life or security vulnerabilities are discovered.

**Fix:** Upgrade to `io.jsonwebtoken:jjwt-api:0.12.6`. The API migration:
```java
// Old (0.11.x)
Jwts.builder().setSubject(id).signWith(key, SignatureAlgorithm.RS256)
// New (0.12.x)
Jwts.builder().subject(id).signWith(key)
```

---

### DEBT-16 — MEDIUM | `@SuppressWarnings("unchecked")` in WebSocket Handler

**Affected file:** `realtime-edge-service/.../adapter/in/websocket/RealtimeWebSocketHandler.java`

```java
@SuppressWarnings("unchecked")
private Map<String, Object> extractPayload(JsonNode root) {
    Map<String, Object> payload = objectMapper.convertValue(root, LinkedHashMap.class);
```

Using raw `LinkedHashMap.class` without a TypeReference suppresses the unchecked warning. The correct approach:

```java
Map<String, Object> payload = objectMapper.convertValue(
    root, new TypeReference<LinkedHashMap<String, Object>>() {}
);
```

This eliminates the unchecked cast and removes the `@SuppressWarnings`.

---

## 5. Transaction and Consistency Debt

### DEBT-17 — CRITICAL | PersistMessageStep Has Independent Transaction

**Affected:** `chat-service/.../pipeline/send/steps/PersistMessageStep.java`

Each pipeline step runs in `CompletableFuture.runAsync()` on a new thread. `@Transactional` on each step starts a new, independent transaction. The message is committed by `PersistMessageStep`'s transaction before `PersistMentionStep` runs. If `PersistMentionStep` fails:
- The message exists in the DB without its @mentions
- The message was already published to Kafka (phantom event for mentions)
- No rollback is possible

**Fix:** Run the pipeline synchronously. Wrap the entire pipeline in a single `@Transactional` on `MessageCommandService.send()`.

---

### DEBT-18 — HIGH | @Transactional on Redis Method Has No Effect

**Affected:** `chat-service/.../domain/service/impl/RedisRoomSequenceService.java`

```java
@Override
@Transactional  // Has NO effect on Redis operations
public long nextSeq(UUID roomId) {
    return redisTemplate.opsForValue().increment("room:seq:" + roomId);
}
```

Spring's `@Transactional` manages JDBC connections via `PlatformTransactionManager`. Redis `INCR` is not part of the JDBC transaction. If the surrounding JPA transaction rolls back:
- The DB changes are rolled back
- The Redis sequence number is NOT rolled back (already incremented)
- The message sequence has a gap (sequence 5 was allocated but no message with seq=5 exists)

This is acceptable for a chat system (gaps in sequence numbers are tolerable), but the `@Transactional` annotation is misleading — it implies rollback behavior that doesn't exist.

**Fix:** Remove `@Transactional` from `nextSeq()`. Add a comment explaining that gaps are expected and acceptable.

---

## 6. Configuration Debt

### DEBT-19 — HIGH | Hardcoded Fallback Values for Secrets

**Affected:** `chat-service/src/main/resources/application.yaml`

```yaml
cloudinary:
  api-secret: ${CLOUDINARY_API_SECRET:KfHivMv0wUQ9D-89rVx8Wu5U12w}
```

The fallback default bakes a live API secret into the JAR file. Even if the env var is overridden at runtime, the secret is extractable from the JAR's classpath resources.

**Fix:** Remove ALL fallback defaults for secrets. Use `${ENV_VAR}` with no default — Spring will throw `IllegalArgumentException` at startup if the variable is missing, which is the correct behavior (fail fast, fail loud).

---

### DEBT-20 — HIGH | Duplicate YAML Key in notification-service

**Affected:** `notification-service/src/main/resources/application.yaml`

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # ← This key block
  datasource:                # ← DUPLICATE KEY
    hikari:
      connection-timeout: 30000
```

YAML spec prohibits duplicate keys at the same level. Most parsers silently use the last value. The `maximum-pool-size: 20` setting is effectively ignored — notification-service runs with HikariCP default (10 connections).

**Fix:** Merge into a single `spring.datasource.hikari:` block:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      connection-timeout: 30000
```

---

## 7. Technical Debt Priority

| ID | Severity | Debt Type | Effort | Priority |
|----|----------|-----------|--------|----------|
| DEBT-04 | CRITICAL | Dead code | Low | P0 — blocks fresh deploy |
| DEBT-17 | CRITICAL | Transaction | Medium | P0 — data integrity |
| DEBT-05 | HIGH | Dead code | Low | P1 — silent caching failure |
| DEBT-01 | HIGH | Incomplete | Medium | P1 — dead Kafka consumers |
| DEBT-06 | HIGH | Dead code | Low | P1 — confusion |
| DEBT-11 | HIGH | Duplication | Low | P1 — security fix propagation |
| DEBT-12 | HIGH | Duplication | Low | P1 — schema management |
| DEBT-15 | HIGH | Deprecated | Medium | P1 — jjwt upgrade |
| DEBT-18 | HIGH | Misleading | Low | P1 — remove @Transactional |
| DEBT-19 | HIGH | Security | Low | P1 — secret in JAR |
| DEBT-20 | HIGH | Config | Low | P1 — wrong pool size |
| DEBT-02 | HIGH | Incomplete | High | P2 — offline notifications |
| DEBT-07 | HIGH | Dead code | Low | P2 — wire dedup guard |
| DEBT-13 | HIGH | Duplication | Medium | P2 — SecurityConfig ×7 |
| DEBT-08 | MEDIUM | Dead code | Low | P3 |
| DEBT-09 | MEDIUM | Dead code | Low | P3 |
| DEBT-10 | MEDIUM | Config | Low | P3 |
| DEBT-14 | MEDIUM | Duplication | Low | P3 |
| DEBT-16 | MEDIUM | Code quality | Low | P3 |
| DEBT-03 | MEDIUM | Docs | Low | P4 |
