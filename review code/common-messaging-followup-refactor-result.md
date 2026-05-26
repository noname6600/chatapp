# Common Messaging Follow-Up Refactor Result

## 1. Scope

**Exact folders/files changed:**

- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/KafkaEventProducer.java` — created
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventProducer.java` — created
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java` — modified
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventPublisher.java` — modified
- `chatappBE/common/common-kafka/src/test/java/com/example/common/kafka/contract/KafkaContractTest.java` — modified (imports only)
- `chatappBE/common/common-events/src/main/java/com/example/common/event/SharedEventCatalog.java` — modified
- `chatappBE/common/common-events/src/main/java/com/example/common/event/EventMetadata.java` — modified
- `chatappBE/common/common-events/src/main/java/com/example/common/event/EventEnvelope.java` — modified
- `chatappBE/common/common-events/src/main/java/com/example/common/integration/notification/NotificationEventType.java` — modified
- `chatappBE/common/common-events/src/test/java/com/example/common/event/contract/SharedEventModelContractTest.java` — modified
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/publisher/DefaultRedisEventPublisher.java` — modified
- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisEventSerializer.java` — modified

**Modules NOT modified:** all service modules, gateway, auth-service, chat-service, presence-service,
friendship-service, notification-service, user-service, upload-service, redis-cache module,
common-core, common-web, common-feign, common-security, common-websocket,
docker/nginx/deployment files, any unrelated Gradle files.

**Verification:**
`.\gradlew.bat :common:common-events:test :common:common-redis:test :common:common-kafka:test --no-daemon --rerun-tasks`
→ `BUILD SUCCESSFUL in 58s — 12 actionable tasks: 12 executed`

Deprecation warnings at compile time are expected; they originate from `@Deprecated(forRemoval=true)`
compat types and the factory method that wires them, which is correct behavior.

---

## 2. Problems Targeted

From `common-messaging-post-next-refactor-review.md` — Remaining Problems section:

**High (all addressed):**
- `common-kafka` internally inconsistent: `KafkaContractTest` asserted `KafkaEventProducer` and
  `DefaultKafkaEventProducer` that did not exist in main source. Module failed its own test suite.
  **Fixed** by re-creating both compat types in main source and wiring `DefaultKafkaEventProducer`
  from `KafkaAutoConfiguration`.
- Test/source/document misalignment: build was failing at `compileTestJava`.
  **Fixed** — all three modules now compile and pass.

**Medium (all addressed inside scope):**
- `EventMetadata` accepted null/blank identity fields by construction.
  **Fixed** — constructor now validates all five identity fields eagerly.
- `EventEnvelope` had no constructor-level validation.
  **Fixed** — compact constructor now rejects null `metadata`.
- `SharedEventCatalog` Javadoc still leaked transport-specific language ("custom Redis or Kafka
  registry bean") that contradicted the claimed transport-agnostic ownership model.
  **Fixed** — replaced with transport-neutral contract language.
- Both publishers collapsed VALIDATE and PUBLISH failures under the same lifecycle stage wrapper.
  **Fixed** in both `DefaultRedisEventPublisher` and `DefaultKafkaEventPublisher` — VALIDATE and
  PUBLISH are now distinct catch blocks with distinct stage labels.

**Low (addressed inside scope):**
- `NotificationEventType` naming uneven: `REQUESTED` sat beside `NOTIFICATION_CREATED` and
  `NOTIFICATION_SENT`.
  **Fixed** — renamed to `NOTIFICATION_REQUESTED` for uniform prefix.

---

## 3. Changes Made In Common-Events

### NotificationEventType — naming standardization

**File:** `com/example/common/integration/notification/NotificationEventType.java`

**Changed:** `REQUESTED` → `NOTIFICATION_REQUESTED`

The semantic value string `"notification.requested"` is unchanged. This is a constant-name-only
fix to align with the naming convention already used by `NOTIFICATION_CREATED` and
`NOTIFICATION_SENT`. All three constants now share the `NOTIFICATION_` prefix.

### SharedEventCatalog — transport leakage removed from Javadoc

**File:** `com/example/common/event/SharedEventCatalog.java`

**Changed:** Removed the sentence that said:
> "use transport-specific registry overrides provided by the transport module (e.g., a custom Redis
> or Kafka registry bean) for service-specific needs."

**Replaced with:** Transport-neutral language stating that service-specific event types must use
distinct event type strings not listed in the shared catalog.

This is not cosmetic. The removed text directly contradicted the canonical model where
`common-events` is transport-agnostic and registry ownership is not per-transport.

### EventMetadata — construction-level validation

**File:** `com/example/common/event/EventMetadata.java`

**Added:** Validation in the `@JsonCreator` constructor for all five identity fields:
- `eventId` — not null, not blank
- `eventType` — not null, not blank
- `sourceService` — not null, not blank
- `createdAt` — not null
- `correlationId` — not null, not blank

**Reason:** `EventMetadata` was a validity-soft container. The downstream VALIDATE stage in both
publishers called `EventContractValidator.validateIdentityOrThrow(...)` as a second pass, but the
model itself was permissive. Now invalid metadata objects cannot be constructed at all. This is
the more correct architecture: the model enforces its own contract, not a validator called later.

**Note:** Pattern validation (`eventType` must follow lower-dot convention) is intentionally NOT
added to the constructor. Pattern validation remains the responsibility of
`EventContractValidator.validateEventNameOrThrow(...)` at the publish boundary. Construction-level
validation covers only structural presence (non-null / non-blank), not semantic correctness.

### EventEnvelope — null metadata guard

**File:** `com/example/common/event/EventEnvelope.java`

**Added:** `if (metadata == null) throw new IllegalArgumentException("EventEnvelope metadata must not be null")`
in the compact record constructor.

**Reason:** An envelope without metadata is not a valid event in any transport. This is a
minimal structural invariant.

### SharedEventModelContractTest — aligned with construction-time validation

**File:** `com/example/common/event/contract/SharedEventModelContractTest.java`

**Replaced** the old validator-centric negative tests (which tested that `EventMetadata` could be
built with blank fields and the validator would catch them) with:
- `eventMetadata_rejectsBlankEventId` — tests construction throws for blank eventId
- `eventMetadata_rejectsNullEventType` — tests construction throws for null eventType
- `eventMetadata_rejectsNullCreatedAt` — tests construction throws for null createdAt
- `eventEnvelope_rejectsNullMetadata` — tests envelope construction throws for null metadata
- Retained `validator_validateMetadata_rejectsNonConformingEventType` — pattern validation
  (upper-case event name) is still a validator concern, not a construction concern

---

## 4. Changes Made In Common-Redis

### DefaultRedisEventPublisher — split VALIDATE / PUBLISH lifecycle stages

**File:** `com/example/common/redis/publisher/DefaultRedisEventPublisher.java`

**Before:** Single `try/catch` block that wrapped both validation and the actual Redis send.
Error message was always `"Failed at Redis lifecycle stage PUBLISH"` regardless of whether the
failure was a contract violation or a Redis I/O error.

**After:** Two distinct `try/catch` blocks:
- Stage `VALIDATE`: catches `IllegalArgumentException` from `EventContractValidator`; throws
  `RedisPubSubException` with stage label `"VALIDATE"`.
- Stage `PUBLISH`: catches `Exception` from serializer + `redisTemplate.convertAndSend`; throws
  `RedisPubSubException` with stage label `"PUBLISH"`.

This improves operational distinguishability — operators can tell from the exception message
whether the failure was a contract error (bad event metadata) or a transport error (Redis unavailable, serialization failure).

### JsonRedisEventSerializer — use construction-time validation

**File:** `com/example/common/redis/serialization/JsonRedisEventSerializer.java`

**Before:** Built `EventMetadata` from potentially null fields without a try-catch, then called
`EventContractValidator.validateIdentityOrThrow(...)` as a second phase to catch the resulting
invalid state.

**After:** Wraps `new EventMetadata(...)` in a `try/catch (IllegalArgumentException)` and maps
the exception to `RedisPubSubException`. Removed the separate Phase 5 validator call and the
`EventContractValidator` import, which is no longer needed.

This is now consistent with the model's own construction-level validation: the serializer lets
the canonical model type enforce identity validity at construction, then maps the failure to the
transport exception type.

---

## 5. Changes Made In Common-Kafka

### KafkaEventProducer — re-created as deprecated compat interface

**File:** `com/example/common/kafka/producer/KafkaEventProducer.java` (created)

Deprecated interface with `@Deprecated(since="2.2", forRemoval=true)`. Declares the single
`publish(String topic, String key, EventEnvelope<?> envelope)` method. Retained because downstream
services (`auth-service`, `friendship-service`, `notification-service`) still inject this type.

### DefaultKafkaEventProducer — re-created as deprecated compat class

**File:** `com/example/common/kafka/producer/DefaultKafkaEventProducer.java` (created)

Deprecated subclass of `DefaultKafkaEventPublisher` that also implements `KafkaEventProducer`.
Annotated with `@Deprecated(since="2.2", forRemoval=true)`. This is the single bean that satisfies
both `KafkaEventPublisher` (canonical) and `KafkaEventProducer` (compat) injection points without
registering two beans.

### KafkaAutoConfiguration — wire DefaultKafkaEventProducer

**File:** `com/example/common/kafka/config/KafkaAutoConfiguration.java`

**Changed:** `kafkaEventPublisher` factory method now instantiates `DefaultKafkaEventProducer`
instead of `DefaultKafkaEventPublisher`. Added `@SuppressWarnings("deprecation")` on the factory
method and a Javadoc comment explaining the rationale.

**Result:**
- Services injecting `KafkaEventPublisher` (canonical) → satisfied ✓
- Services injecting `KafkaEventProducer` (deprecated compat) → satisfied ✓
- Only one bean registered for both interfaces ✓

### DefaultKafkaEventPublisher — split VALIDATE / PUBLISH lifecycle stages

**File:** `com/example/common/kafka/producer/DefaultKafkaEventPublisher.java`

Equivalent to the Redis publisher fix. Split the single try-catch into:
- Stage `VALIDATE`: catches `IllegalArgumentException` from `EventContractValidator`; throws
  `KafkaMessagingException` with stage label `"VALIDATE"`.
- Stage `PUBLISH`: catches `Exception` from `kafkaTemplate.send(...).join()`; throws
  `KafkaMessagingException` with stage label `"PUBLISH"`.

### KafkaContractTest — added missing compat-type imports

**File:** `com/example/common/kafka/contract/KafkaContractTest.java`

Added `import com.example.common.kafka.producer.KafkaEventProducer;` and
`import com.example.common.kafka.producer.DefaultKafkaEventProducer;` to resolve the
`compileTestJava` failure that was the primary regression reported in the post-next-refactor review.

---

## 6. Deleted / Removed Code

### Removed from EventMetadata (common-events)
- The five direct field assignments in the constructor (replaced with validation + assignment).
  No behavioral code removed; only the permissive assignment-without-check pattern.

### Removed from EventEnvelope (common-events)
- The empty compact constructor comment `// Record compact constructor - validation can be added here`.
  Replaced with real null-metadata guard.

### Removed from SharedEventCatalog Javadoc (common-events)
- Transport-ownership leak: the sentence describing "custom Redis or Kafka registry bean" overrides.

### Removed from JsonRedisEventSerializer (common-redis)
- Phase 5 explicit validator call block (`EventContractValidator.validateIdentityOrThrow(...)`)
- `import com.example.common.event.validation.EventContractValidator` — no longer needed

### Removed from DefaultRedisEventPublisher (common-redis)
- Single monolithic try-catch wrapping both validation and publish (replaced by two split blocks)

### Removed from DefaultKafkaEventPublisher (common-kafka)
- Single monolithic try-catch wrapping both validation and publish (replaced by two split blocks)

### Removed from SharedEventModelContractTest (common-events)
- Two old tests that required constructing invalid `EventMetadata` objects and expecting the
  *validator* (not the constructor) to catch them:
  - `validator_validateMetadata_rejectsBlankEventId`
  - (Replaced by: construction-time tests for `EventMetadata` and `EventEnvelope`)

---

## 7. Canonical Decisions Confirmed

**Final owner of semantic event names:** `common-events` — enum constants in
`com.example.common.integration.*`. No transport module owns semantic event-name literals.
`KafkaTopics` owns only transport route strings (topic names) that are structurally distinct from
event-type values.

**Final owner of payload mappings:** `SharedEventCatalog.registerAll(...)` — the canonical
event-type-to-payload class map. All transport auto-configurations pre-populate their registry
from this single source. No transport module maintains its own payload mapping.

**Final handling of payload-less events:** `SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES` is the
authoritative set. `JsonRedisEventSerializer` skips payload resolution for members of this set.
Transport modules must not register payload-less events in the payload registry.

**Final Redis contract integration:** `RedisAutoConfiguration.redisEventRegistry()` calls
`SharedEventCatalog.registerAll(registry)` at boot. `JsonRedisEventSerializer` depends on
`EventPayloadRegistry` (transport-neutral interface). `RedisEventRegistry` and
`DefaultRedisEventRegistry` are retained as injection-by-type adapter surfaces for downstream
services that have not yet migrated to injecting `EventPayloadRegistry` directly. They add no
Redis-specific behavior and are still non-canonical duplicate surface, but removal requires
downstream migration (out of scope).

**Final Kafka route ownership:** `KafkaTopics` contains only transport route strings (Kafka topic
names). Semantic event names live exclusively in `common-events`. Kafka no longer duplicates or
aliases any semantic event literal.

**Final compatibility surface intentionally kept:**
- `KafkaEventProducer` interface — `@Deprecated(forRemoval=true)`, needed by auth-service,
  friendship-service, notification-service.
- `DefaultKafkaEventProducer` class — `@Deprecated(forRemoval=true)`, wired from
  `KafkaAutoConfiguration` as the single compat bean satisfying both interfaces.
- `RedisEventRegistry` / `DefaultRedisEventRegistry` — kept as injection-by-type adapters for
  presence-service and other downstream services; no new behavior added.

---

## 8. Validation / Error Handling / Observability Improvements

### Construction-time validation (common-events)
- `EventMetadata` now enforces all five identity fields at construction. Invalid metadata objects
  cannot be created. This removes a class of latent bugs where invalid metadata reached serializers
  or publishers without failing at the earliest possible point.
- `EventEnvelope` now rejects null metadata at construction.

### Lifecycle stage observability (common-redis + common-kafka)
- Both `DefaultRedisEventPublisher` and `DefaultKafkaEventPublisher` now distinguish two failure
  stages:
  - `VALIDATE` — contract violation (bad event-type name, missing identity field). These are
    programming errors / caller contract violations.
  - `PUBLISH` — transport failure (serialization error, Redis/Kafka unavailable). These are
    operational errors.
  Operators and service owners can now distinguish these two failure categories from exception
  messages alone, without inspecting stack traces.

### Serializer validation simplification (common-redis)
- `JsonRedisEventSerializer` Phase 5 (explicit `validateIdentityOrThrow` call) was removed.
  `EventMetadata` construction now covers the same validation. The serializer mapping is simpler
  and no longer has a conceptual Phase 5 that was partially redundant with the model.

---

## 9. Remaining External Migration Risks

These all require edits **outside** the three allowed modules and are left for downstream migration passes:

1. **auth-service** — `AccountCreatedEventProducer` still injects `KafkaEventProducer` and
   references `KafkaTopics.ACCOUNT_CREATED`. `KafkaTopics.ACCOUNT_CREATED` may need to be verified
   as present (it was removed in an earlier pass; confirm status).

2. **friendship-service** — `FriendshipEventProducer` still uses `KafkaEventProducer` and removed
   `KafkaTopics.FRIENDSHIP_EVENTS` / `FRIENDSHIP_REQUEST_EVENTS` constants, and the removed
   `FriendRequestEvent` DTO.

3. **notification-service** — `FriendRequestEventConsumer` still uses removed `FriendRequestEvent`
   and removed `KafkaTopics.FRIENDSHIP_REQUEST_EVENTS`.

4. **Repository-wide old Kafka wrapper events** — many services still import types under
   `com.example.common.integration.kafka.event.*` which were removed in earlier cleanup passes.

5. **Repository-wide old Redis API** — some services still import `RedisMessage`, `IRedisMessage`,
   `IRedisPublisher`, `IRedisPubSubLogger`, and `RedisEventSubscriber` (old pre-refactor API).

6. **chat-service Redis conflict** — `ChatRedisEventConfig` re-registers `MESSAGE_PINNED` and
   `MESSAGE_UNPINNED` (canonical: `MessagePinPayload`) to `RoomMessagePinEventPayload`. This
   contradicts `SharedEventCatalog` and will produce a registry conflict at runtime. Must be fixed
   in chat-service by either removing the re-registration or migrating to `MessagePinPayload`.

7. **presence-service duplicate registration** — `PresenceRedisRegistryConfig` re-registers event
   types that `SharedEventCatalog` already covers. These are same-class registrations so they are
   currently operationally harmless (no conflict), but the redundant registration should be removed
   once ownership migration is confirmed.

8. **RedisEventRegistry / DefaultRedisEventRegistry** — still injected by type in presence-service.
   These types can only be removed once all downstream services have migrated to injecting
   `EventPayloadRegistry` directly.

9. **KafkaEventProducer / DefaultKafkaEventProducer** — still injected by type in auth-service,
   friendship-service, notification-service. These deprecated compat types can only be removed
   after all downstream services have migrated to `KafkaEventPublisher`.

---

## 10. Final Summary

This pass resolved the primary regression introduced in the previous refactor — the broken
`common-kafka` test suite — and addressed four medium/low issues that remained from the
post-next-refactor review.

**What changed:**
- `KafkaEventProducer` (interface) and `DefaultKafkaEventProducer` (class) are re-created in
  main source as `@Deprecated(forRemoval=true)` compat types. `KafkaAutoConfiguration` wires
  `DefaultKafkaEventProducer` as the single bean satisfying both `KafkaEventPublisher` and
  `KafkaEventProducer`. The module now passes its own test suite.
- `EventMetadata` and `EventEnvelope` now enforce structural validity at construction time.
  The model layer is no longer validity-soft.
- `NotificationEventType.REQUESTED` renamed to `NOTIFICATION_REQUESTED` for uniform naming.
- `SharedEventCatalog` Javadoc no longer leaks transport-specific ownership language.
- Both publishers now distinguish `VALIDATE` and `PUBLISH` failure stages in exception messages.
- `JsonRedisEventSerializer` now relies on `EventMetadata` construction validation instead of a
  redundant explicit validator call.

**Canonical state after this pass:**
- `common-events`: transport-agnostic, owns semantic event names, payload types, canonical
  event-to-payload mapping, and now enforces model validity at construction.
- `common-redis`: pure Redis pub/sub concern, boots from shared catalog, correctly rejects
  payload-bearing events with missing payloads, distinguishes VALIDATE/PUBLISH failures.
- `common-kafka`: pure transport route module, wires compat bean for still-migrating services,
  distinguishes VALIDATE/PUBLISH failures, passes all tests.

**Build status:** `BUILD SUCCESSFUL` — all three common modules compile and all tests pass.
