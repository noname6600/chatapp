# Phase 1 + Phase 2 Execution Report

**Date:** 2026-05-20  
**Branch:** write-factory  
**Executed by:** Claude Sonnet 4.6

---

## Summary Table

| Item | Title | Status | Service(s) |
|------|-------|--------|------------|
| P1-1 | Add TTL to Redis session hash keys | [DONE] | realtime-edge-service |
| P1-2 | Replace `redis.keys()` with cursor SCAN | [DONE] | realtime-edge-service |
| P1-3 | Fix `CheckBlockedPairStep` fail-closed bug | [DONE] | chat-service |
| P1-4 | Fix Kafka DLQ partition routing | [DONE] | notification-service |
| P1-5 | Fix JWT issuer validation | [PARTIAL] | auth-service (gateway BLOCKED) |
| P1-6 | Fix `ddl-auto: update` → Flyway | [PARTIAL] | auth-service BLOCKED; others already fixed |
| P1-7 | Add WebSocket rate limiting | [DONE] | realtime-edge-service |
| P2-1 | Remove `@PostConstruct findAll()` | [DONE] | chat-service |
| P2-2 | Cache blocked-pair status in Redis | [DONE] | chat-service |
| P2-3 | Cache room membership for authorization | [DONE] | realtime-edge-service |
| P2-4 | Fix room member N+1 in message publishing | [DONE] | chat-service |
| P2-5 | Add `SessionCreationPolicy.STATELESS` to API routes | [DONE] | auth-service |
| P2-6 | Remove follow-up snapshot in presence bridge | [DONE] | realtime-edge-service |
| P2-7 | Add `ErrorHandlingDeserializer` to all Kafka consumers | [DONE] | notification, chat, user, friendship |
| P2-8 | Add WebSocket token refresh protocol | [DONE] | realtime-edge-service |

---

## Detailed Changes

### P1-1 [DONE] — Add TTL to Redis Session Hash Keys

**Evidence of bug:** `register()` called `redis.opsForHash().putAll(sessionKey, values)` with no `expire` call.
`refreshSessionLease()` updated fields without resetting TTL. Hash keys were immortal in Redis despite
lease-based eviction logic.

**Files changed:**
- `realtime-edge-service/src/main/java/com/chatweb/realtime/connection/RedisRealtimeSessionRegistry.java`

**Changes:**
- Added `redis.expire(sessionKey, leaseTtl)` immediately after `putAll()` in `register()` (line 87)
- Added `redis.expire(sessionKey, leaseTtl)` at the end of `refreshSessionLease()` (line 188)
- Added imports: `Cursor`, `ScanOptions`

**Why correct:** Hash key TTL is now kept in sync with the lease's logical expiry. Redis can auto-evict
orphaned sessions even when the cleanup job is delayed or skipped.

**Risks:** None. `expire` is additive and idempotent. Worst case: TTL resets to `leaseTtl` on every
`refreshSessionLease()` call, which is the desired behavior.

---

### P1-2 [DONE] — Replace `redis.keys()` with Cursor SCAN

**Evidence of bug:**
- `evictStaleSessions()` line 190: `Set<String> keys = redis.keys(SESSION_PREFIX + "*")`
- `cleanupSessionIdSetMembers()` line 325: `Set<String> keys = redis.keys(keyPattern)`
Both block Redis while scanning the full keyspace — O(N) on the server with N = total key count.

**Files changed:**
- `realtime-edge-service/src/main/java/com/chatweb/realtime/connection/RedisRealtimeSessionRegistry.java`

**Changes:**
- `evictStaleSessions()`: replaced with `redis.scan(ScanOptions)` in try-with-resources; preserves
  `cleanupBatchSize` limit
- `cleanupSessionIdSetMembers()`: replaced with `redis.scan(ScanOptions)` in try-with-resources;
  scan exceptions are caught and logged rather than crashing the cleanup job

**Why correct:** `SCAN` is cursor-based and O(1) per iteration on the server. `count(200)` is a hint;
Redis will iterate until cursor reaches 0. Wrapped in try-with-resources to ensure cursor is closed.

**Risks:** Cursor-based scan is not atomic — keys created or deleted during iteration may be missed or
double-visited. For an eviction job, this is acceptable (missed orphan = cleaned next cycle).

---

### P1-3 [DONE] — Fix `CheckBlockedPairStep` Fail-Closed Bug

**Evidence of bug:** Lines 60–68 in original file: any `Exception` (including network timeout or
connection refused from friendship-service) was caught and re-thrown as `BLOCKED_SEND`. A friendship-
service outage silently blocked ALL private-room sends.

**Files changed:**
- `chat-service/src/main/java/com/chatweb/chat/modules/message/application/pipeline/send/steps/CheckBlockedPairStep.java`

**Changes:** See P2-2 below (these two fixes were implemented as a unit since they both touch the same
try-catch logic).

---

### P1-4 [DONE] — Fix Kafka DLQ Partition Routing Bug

**Evidence of bug:** Line 43 in `KafkaConsumerConfig.java`:
```java
(record, ex) -> new TopicPartition(KafkaTopics.TOPIC_SYSTEM_DEAD_LETTER, record.partition())
```
If `system.dead-letter` has fewer partitions than the source topic, this throws
`InvalidTopicException` at runtime and the DLQ write fails.

**Files changed:**
- `notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/KafkaConsumerConfig.java`

**Changes:** Changed `record.partition()` → `-1`. With `-1`, `DeadLetterPublishingRecoverer` uses
the default partitioner to assign a valid partition.

**Risks:** None. This is a one-line fix with no behavioral change other than fixing the routing crash.

---

### P1-5 [PARTIAL] — Fix JWT Issuer Validation

**Evidence of mismatch:** `TokenService.java` builds JWT with no `.setIssuer()` call. Gateway
`SecurityConfig.java` `jwtDecoder()` only registers `JwtTimestampValidator` — no issuer validator.

**Incompatibility:** If gateway issuer validation is enabled NOW, all in-flight tokens (access tokens
have 15-min TTL) that lack an `iss` claim will immediately fail validation and log out all users.

**What was done (safe — backward-compatible):**

**Files changed:**
- `auth-service/src/main/java/com/chatweb/auth/service/impl/TokenService.java`
  - Added `@Value("${auth.jwt.issuer}") String issuer` field
  - Added `.setIssuer(issuer)` to JWT builder
- `auth-service/src/main/resources/application.yaml`
  - Added `auth.jwt.issuer: ${AUTH_JWT_ISSUER:http://auth-service:8081}`

**What is BLOCKED (gateway issuer validation):**
Adding `JwtIssuerValidator("http://auth-service:8081")` to `gateway/config/SecurityConfig.java` must
wait until all tokens issued without `iss` have expired (≤ 15 minutes after `auth-service` restarts
with the new code). Required manual steps:

1. Deploy auth-service with P1-5 changes
2. Wait ≥ 15 minutes (1 full access-token TTL)
3. Then add `JwtIssuerValidator` to gateway `SecurityConfig.jwtDecoder()`
4. Set `AUTH_JWT_ISSUER` env var on ALL services that validate JWTs (gateway, chat, user, presence,
   friendship, notification, realtime-edge)

---

### P1-6 [PARTIAL] — Fix `ddl-auto: update` → Flyway

**Status per service:**
| Service | `ddl-auto` | Flyway | Action |
|---------|-----------|--------|--------|
| chat-service | `validate` | ✓ `flyway-core` dep + config | Already fixed — SKIPPED |
| notification-service | `validate` | ✓ `baseline-on-migrate: true` | Already fixed — SKIPPED |
| friendship-service | `validate` | ✓ `baseline-on-migrate: true` | Already fixed — SKIPPED |
| user-service | `validate` | ✓ `baseline-on-migrate: true` | Already fixed — SKIPPED |
| **auth-service** | **`update`** | **No flyway dep, no migrations** | **BLOCKED** |

**BLOCKED — auth-service Flyway migration:**

**Exact incompatibility:** auth-service has `ddl-auto: update` (Hibernate auto-manages schema) and
no Flyway dependency. Switching requires:
1. A snapshot of the current production schema: `pg_dump --schema-only auth_service > V1__initial_schema.sql`
   — cannot be done without access to a running DB
2. Removing entity annotations that Hibernate currently uses for DDL (e.g. `@Column(nullable = false)`
   with no corresponding migration) — these will break on first start with `validate` unless the DB
   already matches
3. Adding `implementation 'org.flywaydb:flyway-core'` to `auth-service/build.gradle`
4. Testing the migration against a copy of production data

**Required manual steps to unblock:**
```bash
# 1. Connect to production/staging auth DB
pg_dump --schema-only -d auth_service -U auth_user > \
  auth-service/src/main/resources/db/migration/V1__initial_schema.sql

# 2. Add to auth-service/build.gradle:
implementation 'org.flywaydb:flyway-core'

# 3. Add to auth-service/src/main/resources/application.yaml:
spring.flyway.baseline-on-migrate: true

# 4. Change in application.yaml:
spring.jpa.hibernate.ddl-auto: validate

# 5. Test on a clone of production DB before deploying
```

---

### P1-7 [DONE] — Add WebSocket Rate Limiting

**Evidence of issue:** `handleTextMessage()` had no rate limiting — a single connection could flood
the server at unlimited message rate.

**Files changed:**
- `realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java`

**Changes:**
- Added `ConcurrentHashMap<String, Long> lastMessageTime` field (sessionId → last message timestamp)
- Added rate-check at the start of `handleTextMessage()`: rejects with `RATE_LIMITED` if last message
  was within 50 ms (20 msgs/second limit)
- Added `lastMessageTime.remove(sessionId)` in `afterConnectionClosed()` to prevent memory leak

**Why correct:** `ConcurrentHashMap.put()` is thread-safe for single-key updates. The `put` call
returns the previous value atomically enough for this sliding-window check (worst case: two threads
pass the check simultaneously at startup — both messages are processed, limit not strictly enforced,
acceptable). Cleanup on disconnect prevents unbounded growth.

**Risks:** The `lastMessageTime` map will retain one entry per active session (small). PING-type
messages are also rate-limited — at 20/s this should not affect normal heartbeat intervals.

---

### P2-1 [DONE] — Remove `@PostConstruct findAll()` in Sequence Service

**Evidence of bug:** `RedisMessageSequenceService.java` (class `RedisRoomSequenceService`) had:
```java
@PostConstruct
public void seedRedisCounters() {
    List<Room> rooms = roomRepository.findAll();
    ...
}
```
This runs `SELECT * FROM room` at every service startup. With thousands of rooms this causes a
multi-second startup delay and OOM risk on large datasets. Additionally, the file was missing imports
for `@PostConstruct` and `java.util.List` — it would not compile as written.

**Files changed:**
- `chat-service/src/main/java/com/chatweb/chat/modules/message/infrastructure/sequence/RedisMessageSequenceService.java`

**Changes:** Rewrote the class with lazy seeding:
- Removed `@PostConstruct seedRedisCounters()`
- In `nextSeq()`: if `INCR` returns `1` (key just created), load `lastSeq` from DB and re-seed.
  Uses `setIfPresent` to avoid overwriting a concurrent increment.
- Fixed missing imports (removed `@PostConstruct`, `List`, `@Async`, `@Transactional` — no longer needed)

**Risks:** First `nextSeq()` call for a room after a Redis flush will be slower (DB round-trip).
Subsequent calls are O(1) Redis INCR. Race condition on first seed: two concurrent `nextSeq` calls
could both see `nextSeq == 1`. The second caller's `setIfPresent` will be a no-op since the key
already exists; both callers get a valid (if out-of-order) sequence number, which is tolerable since
message sequences are for ordering, not strict gapless serial numbers.

---

### P2-2 [DONE] — Cache Blocked-Pair Status in Redis

**Evidence of issue:** `CheckBlockedPairStep` called `friendshipClient.isBlockedBetween()` on every
message send in a private room — a synchronous HTTP call for every message.

**Files changed:**
- `chat-service/.../send/steps/CheckBlockedPairStep.java` (P1-3 + P2-2 combined)
- NEW: `chat-service/.../infrastructure/kafka/FriendshipBlockEventConsumer.java`

**Changes in CheckBlockedPairStep:**
- Added `StringRedisTemplate redisTemplate` injection
- Cache lookup before service call: key = `blocked_pair:{minUUID}:{maxUUID}` (symmetric), TTL = 60s
- Cache miss: calls friendship-service, stores result, then checks
- Fail-open: service exceptions are caught and logged, message is allowed through
- Added `invalidateCache(UUID, UUID)` package-private method

**Changes in FriendshipBlockEventConsumer:**
- New `@KafkaListener` on `friendship.events` topic, group `chat-service-block-cache`
- On `friend.blocked` or `friend.unblocked` events, calls `checkBlockedPairStep.invalidateCache()`
- Filters to only block/unblock events; all other friendship events are ignored

**Invalidation strategy:** Event-driven via Kafka. TTL of 60s provides a safety net for missed events.

**Risks:**
- Between block/unblock event and cache invalidation, a message may be allowed/blocked incorrectly.
  Maximum staleness = Kafka lag (typically < 1 second). This is acceptable for a chat application.
- `FriendshipBlockEventConsumer` uses a separate `groupId` (`chat-service-block-cache`) so it
  does not interfere with any future chat-service Kafka consumers on the same topic.

---

### P2-3 [DONE] — Cache Room Membership for Authorization

**Evidence of issue:** `ChannelSubscriptionManager.hasRoomAccess()` called
`chatCommandRouter.canAccessRoom()` on every subscription request — a synchronous HTTP call to
chat-service's room membership endpoint.

**Files changed:**
- `realtime-edge-service/src/main/java/com/chatweb/realtime/subscription/ChannelSubscriptionManager.java`

**Changes:**
- Added `StringRedisTemplate redisTemplate` injection
- Added cache constants: `ROOM_ACCESS_CACHE_PREFIX = "room:access:"`, TTL = 60s
- `hasRoomAccess()` now takes `userId` parameter (from `session.getUserId()`)
- Cache key: `room:access:{userId}:{roomId}` (per-user, per-room)
- Cache hit: return cached boolean, skip service call
- Cache miss: call service, cache result
- Both cache read and write exceptions are caught and logged (degrade gracefully to service call)

**Risks:**
- After a user leaves a room, they may retain `canAccessRoom=true` in the cache for up to 60s.
  This is the known stale-authorization risk documented in the plan.
- Acceptable for a chat application where room-leave events are infrequent and the consequence
  (user can subscribe to room events for ≤ 60s after leaving) is low-impact.

---

### P2-4 [DONE] — Fix Room Member N+1 in Message Publishing

**Evidence of bug:** `ChatMessageEventPublisherAdapter.publishMessageCreated()` made 3 separate DB
queries:
1. `roomMemberRepository.findUserIdsByRoomId()` — for recipient list
2. `roomRepository.findById()` — for room type check
3. `roomMemberRepository.findByRoomIdAndUserId()` — for sender's display name

**Files changed:**
- `chat-service/src/main/java/com/chatweb/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java`

**Changes:**
- Load all room members once with `roomMemberRepository.findByRoomId()` (returns `List<RoomMember>`)
- Derive `recipientUserIds` and `senderDisplayName` from the same in-memory list via stream
- Query count reduced from 3 to 2 (members + room)

**Why not 1 query:** A single JOIN query returning room type + member IDs + sender display name would
require a new projection interface and a JPQL/native query. The 2-query approach achieves a 33%
reduction in DB calls with minimal code change and no new types.

**Risks:** Loading full `RoomMember` entities (vs. just user IDs) fetches more columns per row.
For typical room sizes (< 100 members) the memory overhead is negligible.

---

### P2-5 [DONE] — Add `SessionCreationPolicy.STATELESS` to Auth Service API Routes

**Evidence of issue:** `SecurityConfig.filterChain()` applied `SessionCreationPolicy.IF_REQUIRED`
globally — including to stateless JWT-authenticated API endpoints. This means every API request
could create an HTTP session.

**Files changed:**
- `auth-service/src/main/java/com/chatweb/auth/configuration/SecurityConfig.java`

**Changes:** Replaced single `SecurityFilterChain` with two ordered chains:
1. `oauth2FilterChain` (order=1): `securityMatcher("/oauth2/**", "/login/**")` + `IF_REQUIRED`
   + `oauth2Login(...)` — OAuth2 code flow needs session state
2. `apiFilterChain` (order=2): all other paths + `STATELESS` + `JwtAuthenticationFilter`

**Risks:**
- Spring Security's `securityMatcher` is evaluated in order. Requests to `/oauth2/**` will match
  chain 1 and never reach chain 2 — this is the desired behavior.
- Any path not matched by chain 1 falls to chain 2. The public path allow-list in chain 2 matches
  the original config exactly.
- The `corsConfigurationSource` bean is now created twice (once per chain) but configured
  identically — this is intentional to keep CORS consistent across both chains.

---

### P2-6 [DONE] — Remove Follow-Up Snapshot in Presence Bridge

**Evidence of issue:** `EdgePresenceLifecycleBridge.onPresenceConnected()` called
`presenceDomainClient.globalSnapshot()` twice on every WebSocket connect, making two HTTP calls to
presence-service, and sending a second snapshot only if the data changed.

**Files changed:**
- `realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/out/presence/EdgePresenceLifecycleBridge.java`

**Changes:**
- Removed `@Value("${realtime.presence.snapshot.followup-enabled:true}") boolean followupSnapshotEnabled`
- Removed the `if (followupSnapshotEnabled)` block (second snapshot call + `sameSnapshot` comparison)
- Removed `sameSnapshot()` helper method (now unused)
- Removed `@Value` import

**Why correct:** The follow-up snapshot was a convergence workaround for a race condition in
presence-service. Real-time presence updates are pushed via events after connect — the initial
snapshot is sufficient. Removing the second call saves one HTTP round-trip per WebSocket connection.

**Risks:** Theoretically, a presence change could occur between the `connect()` signal and the
`globalSnapshot()` call and not be included in the snapshot. This is an inherent race that the
follow-up snapshot did not fully solve either (it only detected changes between snapshot 1 and
snapshot 2, not between snapshot 2 and "now"). The correct fix is server-sent-event consistency
via the event stream.

---

### P2-7 [DONE] — Add `ErrorHandlingDeserializer` to All Kafka Consumers

**Evidence of issue:** 4 services used `JsonDeserializer` directly as the value deserializer. A
single malformed message on a topic would crash the consumer thread permanently (no error recovery).

**Files changed (YAML only):**
- `notification-service/src/main/resources/application.yaml`
- `chat-service/src/main/resources/application.yaml`
- `user-service/src/main/resources/application.yaml`
- `friendship-service/src/main/resources/application.yaml`

**Pattern applied (matching realtime-edge-service):**
```yaml
value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
properties:
  spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
  spring.json.trusted.packages: "..."
```

**Why correct:** `ErrorHandlingDeserializer` wraps the delegate deserializer. On deserialization
failure it passes a `DeserializationException` header to the error handler (e.g.
`DeadLetterPublishingRecoverer`) instead of crashing the consumer thread.

**Risks:** None. The existing `JsonDeserializer` behavior is unchanged for valid messages.

---

### P2-8 [DONE] — Add WebSocket Token Refresh Protocol

**Evidence of issue:** Access tokens expire after 15 minutes. Long-lived WebSocket connections had
no way to refresh the token without reconnecting, leaving presence and subscription sessions
unnecessarily short-lived.

**Files changed:**
- `realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java`

**Changes:**
- Added early-exit branch at the top of `handleTextMessage()` for `type == "auth.token.refresh"`
- Added `handleTokenRefresh()` method that replaces `accessToken` in session attributes and logs
  the refresh event

**Protocol:**
```json
{ "type": "auth.token.refresh", "accessToken": "<new-jwt>" }
```

**Note:** The token is accepted structurally (non-null, non-blank) but not validated for signature
or expiry at the edge — validation would require the JWKS endpoint. Downstream calls using the token
(room auth checks, presence calls) will fail naturally if the new token is invalid. A future
improvement would add lightweight JWT structure validation (header/payload Base64-decodable, `exp`
not in past).

---

## Compile/Test Results

Tests were not run (no running Gradle build environment). All changes are:
- Standard Spring Boot patterns
- No new dependencies introduced (all imports already available in each service's build.gradle)
- Consistent with existing code style in each file

**Manual verification checklist before merge:**
```bash
cd chatappBE
./gradlew :realtime-edge-service:compileJava
./gradlew :chat-service:compileJava
./gradlew :auth-service:compileJava
./gradlew :notification-service:compileJava
./gradlew :user-service:compileJava
./gradlew :friendship-service:compileJava
./gradlew :chat-service:test
./gradlew :auth-service:test
./gradlew :realtime-edge-service:test
```

---

## Rejected Fixes and Why

### P1-5 — Gateway Issuer Validation [BLOCKED]
Adding `JwtIssuerValidator` to gateway before all in-flight tokens include `iss` would immediately
invalidate all active sessions. Must wait one full access-token TTL (15 min) after auth-service
deploys with `setIssuer()` before enforcing validation at the gateway.

### P1-6 — auth-service Flyway [BLOCKED]
Cannot generate `V1__initial_schema.sql` without access to the running auth DB. Attempting to
switch from `ddl-auto: update` to `validate` without a migration script would crash auth-service
on startup with schema validation failures. Manual DB dump required.

---

## Remaining Risks After P1+P2

1. **P1-5 gateway enforcement** — Must be manually triggered after ≥1 access-token TTL
2. **P1-6 auth-service schema** — `ddl-auto: update` still in production; schema drift risk on
   every auth-service restart
3. **P2-3 stale authorization** — Room access cache TTL = 60s means a user can subscribe to
   channels for up to 60s after leaving a room
4. **P2-2 Kafka consumer group** — `chat-service-block-cache` group must be created in Kafka before
   deploy (or `auto.create.topics.enable=true` is required)
5. **P1-7 rate limiting** — `ConcurrentHashMap` approach is per-instance, not distributed.
   In a multi-instance deployment, each instance enforces its own rate limit independently.
   For a strict global rate limit, a Redis-backed token bucket is needed.
6. **WebSocket rate limit covers PING** — PING messages count toward the 20/s limit. In
   pathological PING-heavy clients this could cause spurious RATE_LIMITED errors.

---

## Follow-Up Recommendations

1. **Next session:** Add `JwtIssuerValidator` to gateway (15 min after P1-5 auth-service deploy)
2. **Next session:** Run `pg_dump` on auth DB → create `V1__initial_schema.sql` → unblock P1-6
3. **Consider:** Upgrade P1-7 rate limiting to Resilience4j `RateLimiter` or Redis token bucket
   for distributed enforcement
4. **Consider:** Add room-leave event cache invalidation for P2-3 (currently relies on TTL only)
5. **Consider:** Add structured monitoring alert on `[CheckBlockedPairStep] Block check failed
   (fail-open)` log pattern to detect friendship-service outages
6. **Phase 3 must run** before production: Kafka multi-broker, Redis HA, Flyway for all services,
   JWT key management via Vault/KMS
