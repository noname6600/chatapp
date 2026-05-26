## 1. Executive Summary

- Freeze-ready: no. The common modules are close in dependency direction and baseline tests pass, but the public APIs and compatibility wrappers are not clean enough to freeze as the standard foundation.
- Biggest blockers:
  - `common-kafka` still exposes Kafka `Publisher` public APIs and Kafka-specific event wrapper contracts.
  - `common-events` contains a stale duplicate friendship request contract that conflicts with the canonical catalog and breaks services.
  - Kafka serializer/deserializer, retry, and DLQ policy exist but are not wired into a complete standard consumer/producer foundation.
  - `common-redis` publishes `EventEnvelope`, but its subscriber dispatch path still converts back to legacy `RedisMessage`.
  - Redis deserialization drops `eventVersion`; traceId is not modeled anywhere.
- What is already good:
  - Dependency direction is mostly correct: `common-events` is transport-neutral at build/source level, and Kafka/Redis depend on it rather than each other.
  - `EventEnvelope` and `EventMetadata` provide a clear common JSON shape with `eventId`, `eventType`, `sourceService`, `createdAt`, `correlationId`, and `eventVersion`.
  - Shared event type names use lower-dot syntax and have catalog coverage tests.
  - Redis Pub/Sub is separated from `common-redis-cache`.
  - Existing tests for `common-events`, `common-kafka`, and `common-redis` pass when rerun.

## 2. Scope

- Reviewed modules:
  - `chatappBE/common/common-events`
  - `chatappBE/common/common-kafka`
  - `chatappBE/common/common-redis`
- Alias handling:
  - No separate `common-event` module exists; this review treats `common-events` as the event foundation.
  - No separate `common-redis-pubsub` module exists; this review treats Pub/Sub responsibility inside `common-redis` as the Redis Pub/Sub foundation.
- Business services were only scanned lightly for:
  - broken imports
  - old API references
  - compile compatibility fallout caused by common API changes
- Existing common tests were rerun with:
  - `.\gradlew.bat --no-daemon --console=plain --rerun-tasks :common:common-events:test :common:common-kafka:test :common:common-redis:test`
  - Result: `BUILD SUCCESSFUL`

## 3. Dependency Direction

- `common-events` dependency direction: valid.
  - `chatappBE/common/common-events/build.gradle` has Jackson/Lombok/JUnit dependencies only.
  - Main source scan found no Kafka, Redis, WebSocket, or service imports.
- `common-kafka` dependency direction: valid at module level.
  - `chatappBE/common/common-kafka/build.gradle` uses `api project(':common:common-events')`.
  - It does not depend on `common-redis`.
- `common-redis` dependency direction: valid at module level.
  - `chatappBE/common/common-redis/build.gradle` uses `api project(':common:common-events')`.
  - It does not depend on `common-kafka`.
- Kafka/Redis circular dependency: none found.
- Caution:
  - `common-kafka` contains `com.example.common.integration.kafka.event.*` compatibility event wrappers. This is not a Gradle dependency cycle, but it blurs the intended boundary by placing transport-specific event contracts beside shared integration contracts.

## 4. common-events Review

### Issue CE-1: Stale duplicate friendship request contract

- Severity: High
- File/class:
  - `chatappBE/common/common-events/src/main/java/com/example/common/integration/friendship/FriendRequestEvent.java`
  - `chatappBE/common/common-events/src/main/java/com/example/common/integration/friendship/FriendRequestPayload.java`
  - `chatappBE/common/common-events/src/main/java/com/example/common/event/SharedEventCatalog.java`
- Why it matters before freeze:
  - `FriendRequestPayload` is the canonical catalog payload for `friend.request.sent`, `friend.request.accepted`, `friend.request.declined`, and `friend.request.cancelled`.
  - `FriendRequestEvent` is a separate legacy payload with only `senderId`, `recipientId`, and `Type`; services expect fields such as `requestId`, `senderDisplayName`, `createdAt`, and enum `SENT`.
  - This duplicate contract already causes compile failures in friendship and notification services.
- Recommended fix:
  - Pick one canonical shared contract. Prefer `FriendRequestPayload`.
  - Remove `FriendRequestEvent` from the standard API, or make it a deprecated adapter with full compatibility fields and a clear conversion path to `FriendRequestPayload`.
  - Ensure all friendship request event types in `FriendshipEventType` map to the same canonical payload class.

### Issue CE-2: Trace ID is not modeled

- Severity: Medium
- File/class:
  - `chatappBE/common/common-events/src/main/java/com/example/common/event/EventMetadata.java`
  - `chatappBE/common/common-events/src/main/java/com/example/common/event/Event.java`
- Why it matters before freeze:
  - `correlationId` is required and consistently carried, but there is no `traceId`.
  - If the foundation is frozen now, distributed tracing semantics will be ambiguous: services may overload `correlationId`, invent service-local fields, or diverge by transport.
- Recommended fix:
  - Either add an optional `traceId` field to `EventMetadata`, serializers, headers, logs, and tests, or document that `correlationId` is the only trace/correlation field and must be populated from the active trace ID.

### Issue CE-3: Versioning support is present but not cataloged

- Severity: Medium
- File/class:
  - `chatappBE/common/common-events/src/main/java/com/example/common/event/EventMetadata.java`
  - `chatappBE/common/common-events/src/main/java/com/example/common/event/SharedEventCatalog.java`
- Why it matters before freeze:
  - `EventMetadata` has `eventVersion`, but the catalog does not define supported versions per event type or validate publisher/consumer compatibility.
  - This makes versioning a field, not yet a contract.
- Recommended fix:
  - Add a minimal version policy before freeze: supported version per event type, default version, and validation helpers.
  - Keep it small: one version map in `SharedEventCatalog` is enough for the first freeze.

### Issue CE-4: Shared catalog allows unknown event types on publish

- Severity: Medium
- File/class:
  - `chatappBE/common/common-events/src/main/java/com/example/common/event/SharedEventCatalog.java`
- Why it matters before freeze:
  - `enforcePublishPayloadContract()` intentionally passes through event types not in the shared catalog.
  - Kafka and Redis deserializers reject unknown payload-bearing types unless registries are extended, so a producer can publish an event the default common consumers cannot read.
- Recommended fix:
  - Decide the freeze rule explicitly:
    - strict shared mode: reject unknown event types by default in common publishers
    - extension mode: require services to register custom event types before publish and consume
  - Reflect the rule in both Kafka and Redis tests.

### Issue CE-5: Transport-neutral design is structurally good but comments leak Kafka compatibility

- Severity: Low
- File/class:
  - `chatappBE/common/common-events/src/main/java/com/example/common/integration/friendship/FriendRequestEvent.java`
- Why it matters before freeze:
  - The class comment says it is used by friendship Kafka flows. That does not create a code dependency, but it signals that a transport-specific compatibility contract is living in `common-events`.
- Recommended fix:
  - Remove the class as part of CE-1, or rewrite it as a transport-neutral compatibility DTO with a deprecation path.

## 5. common-kafka Review

### Issue CK-1: Kafka public API still exposes Publisher terminology

- Severity: High
- File/class:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/KafkaEventPublisher.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventPublisher.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/api/KafkaEventPublisher.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/api/IKafkaEventPublisher.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/KafkaEventProducer.java`
- Why it matters before freeze:
  - The target rule is explicit: Kafka uses Producer/Consumer wording, not Publisher/Subscriber wording.
  - Even though some aliases are deprecated, they remain public API and `KafkaEventProducer` still exposes deprecated `publish(...)` methods.
- Recommended fix:
  - Make `KafkaEventProducer.send(...)` the only standard public produce method.
  - Move publisher aliases into a clearly deprecated compatibility package or remove them before the freeze.
  - Update service compatibility later, not by preserving Publisher as the frozen standard.

### Issue CK-2: Kafka-specific event wrappers duplicate shared event contracts

- Severity: High
- File/class:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/integration/kafka/event/BaseKafkaCompatEvent.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/integration/kafka/event/AccountCreatedEvent.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/integration/kafka/event/ChatMessageSentEvent.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/integration/kafka/event/ChatMessageEditedEvent.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/integration/kafka/event/ChatMessageDeletedEvent.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/integration/kafka/event/ChatReactionUpdatedEvent.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/integration/kafka/event/FriendRequestKafkaEvent.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/integration/kafka/event/FriendshipEvent.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/integration/kafka/event/NotificationRequestedEvent.java`
- Why it matters before freeze:
  - The standard event contract should be `EventEnvelope<T>` plus payloads from `common-events`.
  - These wrappers preserve the older `IKafkaEvent` model and hard-code event type strings outside the shared catalog.
  - `FriendRequestKafkaEvent.of(...)` can emit `friend.request.created`, while the canonical shared type is `friend.request.sent`.
- Recommended fix:
  - Remove these wrappers from the frozen common API or mark them internal/deprecated compatibility only.
  - Move services toward `EventEnvelope<Payload>` with event type enums from `common-events`.

### Issue CK-3: EventEnvelope serializer/deserializer are not fully wired as the standard Kafka foundation

- Severity: High
- File/class:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/serialization/EventEnvelopeKafkaSerializer.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/serialization/EventEnvelopeKafkaDeserializer.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventProducer.java`
- Why it matters before freeze:
  - `DefaultKafkaEventProducer` sends an `EventEnvelope` through `KafkaTemplate<String, Object>`, but auto-configuration does not standardize the producer value serializer.
  - `commonKafkaConsumerProperties()` declares an `ErrorHandlingDeserializer` delegate, but common-kafka does not provide a full `ConsumerFactory` or listener container factory that is guaranteed to use it.
  - This means services can still compile while using different Kafka serialization behavior.
- Recommended fix:
  - Provide explicit common producer/consumer properties or factories for `EventEnvelope` JSON.
  - Wire `EventEnvelopeKafkaSerializer` for producer values and `ErrorHandlingDeserializer` plus `EventEnvelopeKafkaDeserializer` for consumer values.
  - Add tests that a Spring context picks up the standard serializer/deserializer path.

### Issue CK-4: Retry and DLQ policy is incomplete

- Severity: High
- File/class:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/retry/KafkaRetryDlqPolicy.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java`
- Why it matters before freeze:
  - `KafkaRetryDlqPolicy.retryTopic(...)` exists but is not used by the configured `DefaultErrorHandler`.
  - Backoff is hard-coded as `FixedBackOff(1000L, 2L)`.
  - DLQ routing keeps the original partition when sending to `system.dead-letter`; this can fail if the DLQ topic does not have matching partition counts.
  - There is no standard header policy for original topic, partition, offset, exception, or retry count.
- Recommended fix:
  - Define a minimal freeze policy: retry attempts, backoff, retryable exceptions, DLQ topic, partition behavior, and DLQ headers.
  - Route DLQ records safely, preferably with configured partition behavior rather than blindly using the source partition.
  - Add unit tests for retryable vs non-retryable exceptions and DLQ destination resolution.

### Issue CK-5: Topic constants duplicate semantic event types

- Severity: Medium
- File/class:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/topic/KafkaTopics.java`
- Why it matters before freeze:
  - The comments correctly say semantic event routing should use event type enums from `common-events`.
  - The class still exposes legacy constants such as `ACCOUNT_CREATED`, `CHAT_MESSAGE_SENT`, and `NOTIFICATION_REQUESTED`, which duplicate event type strings and can drift.
- Recommended fix:
  - Keep only true Kafka route constants such as aggregate topics and infrastructure topics.
  - Remove or deprecate event-type aliases before freezing the standard.

### Issue CK-6: Unknown event handling lacks a complete observer and DLQ story

- Severity: Medium
- File/class:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventDispatcher.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/consumer/UnknownKafkaEventPolicy.java`
- Why it matters before freeze:
  - `FAIL` is a reasonable default for durable Kafka events, and `DROP` exists.
  - Unknown-event drops are only logged with SLF4J; there is no observer callback equivalent to Redis `logDroppedNoSubscriber`.
  - The dispatcher does not connect unknown handling to DLQ policy except by throwing.
- Recommended fix:
  - Add observer callbacks for unknown event decisions.
  - Document that `FAIL` should enter the listener error handler/DLQ path.
  - Add tests for both policies and observer calls.

### Issue CK-7: Idempotency and ordering are only implicit

- Severity: Medium
- File/class:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/KafkaEventProducer.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventProducer.java`
  - `chatappBE/common/common-events/src/main/java/com/example/common/event/EventMetadata.java`
- Why it matters before freeze:
  - Producers accept a Kafka key and metadata includes `eventId`, but there is no common guidance or helper for idempotent consumption or ordering keys.
  - Freezing now leaves each service to invent dedupe and key semantics.
- Recommended fix:
  - Define minimum rules: `eventId` is the idempotency key; Kafka message key is the ordering key.
  - Optionally provide helper interfaces for idempotent handlers later, but freeze the semantic rule now.

### Issue CK-8: Observability still uses publish wording

- Severity: Low
- File/class:
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/observability/KafkaEventObserver.java`
  - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/observability/Slf4jKafkaEventLogger.java`
- Why it matters before freeze:
  - Methods such as `logPublish(...)` are deprecated aliases, but they remain visible on the public observer API.
- Recommended fix:
  - Keep `logProduceSuccess` and `logProduceError` as the standard.
  - Move old aliases behind compatibility only or remove them before freezing.

## 6. common-redis-pubsub Review

### Issue CR-1: Subscriber path is still RedisMessage-first instead of EventEnvelope-first

- Severity: High
- File/class:
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/subscriber/RedisEventSubscriber.java`
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/subscriber/RedisEventHandler.java`
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/dispatcher/RedisEventDispatcher.java`
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/message/RedisMessage.java`
- Why it matters before freeze:
  - Publisher and serializer use `EventEnvelope`, but dispatcher converts the envelope into legacy `RedisMessage` before calling subscribers.
  - `RedisEventHandler` is deprecated and converts `RedisMessage` back into `EventEnvelope`, making the standard path circular and compatibility-heavy.
  - The intended foundation is EventEnvelope usage across transports.
- Recommended fix:
  - Make `RedisEventSubscriber<T>` handle `EventEnvelope<T>` directly as the standard.
  - Keep `RedisMessage` only as a deprecated adapter for service migration.
  - Update dispatcher tests to assert envelope delivery without RedisMessage conversion.

### Issue CR-2: Redis deserializer drops eventVersion

- Severity: Medium
- File/class:
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisEventSerializer.java`
  - `chatappBE/common/common-events/src/main/java/com/example/common/event/EventMetadata.java`
- Why it matters before freeze:
  - `EventMetadata` supports `eventVersion`, and Kafka deserialization reads it.
  - Redis deserialization constructs `EventMetadata` with the 5-argument constructor, so any incoming `eventVersion` defaults back to `1`.
  - This breaks cross-transport envelope consistency.
- Recommended fix:
  - Parse `eventVersion` in `JsonRedisEventSerializer` and call the 6-argument `EventMetadata` constructor.
  - Add Redis serializer/deserializer tests for non-default event versions.

### Issue CR-3: Pub/Sub module name is still broad

- Severity: Medium
- File/class:
  - `chatappBE/common/common-redis/build.gradle`
  - `chatappBE/settings.gradle`
- Why it matters before freeze:
  - The implementation is Pub/Sub-only, and cache logic is separated into `common-redis-cache`.
  - The module name `common-redis` can still be read as a general Redis foundation, which invites cache logic to drift back in later.
- Recommended fix:
  - Either rename to `common-redis-pubsub`, or explicitly freeze the module contract as "Redis Pub/Sub only" in package docs/build description.

### Issue CR-4: No common listener container wiring

- Severity: Medium
- File/class:
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/listener/RedisEventListener.java`
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/config/RedisAutoConfiguration.java`
- Why it matters before freeze:
  - `RedisEventListener` exists, but services still own `RedisMessageListenerContainer` and channel subscriptions.
  - That may be acceptable because channel patterns are service-specific, but it means common-redis is not a complete subscribe foundation by itself.
- Recommended fix:
  - Freeze the boundary explicitly: common provides listener/dispatcher/serializer/publisher; services provide channel subscription patterns.
  - Add a small documentation or test fixture showing the standard wiring.

### Issue CR-5: Unknown event handling is best-effort but not configurable

- Severity: Low
- File/class:
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/dispatcher/RedisEventDispatcher.java`
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/observability/RedisPubSubObserver.java`
- Why it matters before freeze:
  - Dropping unknown/no-subscriber events is correct for realtime fanout, and observer hooks exist.
  - There is no explicit `UnknownRedisEventPolicy`, so the behavior is fixed.
- Recommended fix:
  - Keep DROP as the default/frozen Redis behavior and document it as best-effort realtime semantics.
  - Add a test that no-subscriber dispatch never throws.

### Issue CR-6: Subscriber eventType validation is implicit

- Severity: Low
- File/class:
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/dispatcher/RedisEventDispatcher.java`
  - `chatappBE/common/common-redis/src/main/java/com/example/common/redis/subscriber/RedisEventSubscriber.java`
- Why it matters before freeze:
  - Duplicate subscriber prevention exists, but blank/null/invalid `eventType()` values are not validated with the shared event name validator.
  - Bad subscribers can fail with generic collection errors at startup.
- Recommended fix:
  - Validate subscriber event types in the dispatcher constructor and fail with a clear message.

## 7. Cross-module Consistency

- Naming consistency:
  - Good: event type values are lower-dot and validated by `EventContractValidator`.
  - Not ready: Kafka still exposes `Publisher` public API; Redis correctly uses Publisher/Subscriber terms.
- Package consistency:
  - Good: transport-specific Kafka and Redis infrastructure generally live under `com.example.common.kafka` and `com.example.common.redis`.
  - Not ready: Kafka compatibility events live under `com.example.common.integration.kafka.event`, which looks like shared integration contract space but is transport-specific.
- Event envelope consistency:
  - Good: both Kafka and Redis serializers/deserializers target `EventEnvelope`.
  - Not ready: Redis subscriber dispatch converts to `RedisMessage`; Redis deserializer drops `eventVersion`.
- Registry/catalog consistency:
  - Good: `SharedEventCatalog` is the central payload catalog and is used by both Kafka and Redis deserializers.
  - Not ready: unknown event publishing is allowed, while default deserialization rejects unknown payload-bearing event types.
- Transport boundary consistency:
  - Good: `common-events` has no Kafka/Redis/WebSocket/service dependency.
  - Not ready: Kafka-specific event wrapper contracts and stale friendship contracts blur the boundary.

## 8. Freeze Blockers

### High: must fix before freeze

- Remove or quarantine Kafka `Publisher` public APIs from the frozen standard.
- Remove Kafka-specific event wrappers from the frozen standard API, or make them clearly internal/deprecated compatibility only.
- Resolve `FriendRequestEvent` vs `FriendRequestPayload` and align friendship request event type names.
- Wire Kafka `EventEnvelope` serializer/deserializer, `ErrorHandlingDeserializer`, retry, and DLQ policy into a complete standard path.
- Make Redis Pub/Sub subscriber dispatch envelope-first, with `RedisMessage` kept only as compatibility.

### Medium: should fix before freeze

- Decide and implement traceId vs correlationId semantics.
- Complete versioning support, including Redis preservation of `eventVersion`.
- Remove duplicate event-type constants from `KafkaTopics`.
- Freeze unknown event policy for shared events vs service-local extensions.
- Document whether `common-redis` is permanently Pub/Sub-only or rename it to `common-redis-pubsub`.

### Low: can fix later

- Add explicit validation for Kafka handler and Redis subscriber event type values.
- Add observer hooks for Kafka unknown-event decisions.
- Clean stale comments and deprecated method names after service migration.

## 9. Minimal Refactor Plan

1. `common-events`
   - Consolidate friendship request contracts around `FriendRequestPayload`.
   - Add or explicitly reject `traceId` as a metadata field.
   - Add minimal event-version catalog support.
   - Keep `common-events` free of transport-specific classes and comments.

2. `common-kafka`
   - Freeze `KafkaEventProducer.send(...)` and `KafkaEventHandler`/`KafkaEventDispatcher` as the standard API.
   - Move `Publisher`, `IKafkaEvent`, and Kafka wrapper events to compatibility-only, or remove them before freeze.
   - Wire `EventEnvelopeKafkaSerializer`, `EventEnvelopeKafkaDeserializer`, `ErrorHandlingDeserializer`, and `DefaultErrorHandler` through standard config/factories.
   - Fix retry/DLQ policy and tests.
   - Keep `KafkaTopics` to transport routes only.

3. `common-redis`
   - Freeze it as Pub/Sub-only.
   - Change subscriber dispatch to deliver `EventEnvelope<T>` directly.
   - Preserve `eventVersion` in Redis deserialization.
   - Keep `RedisMessage` as compatibility only.

4. Services
   - Only adjust enough imports/usages after the common freeze to compile.
   - Defer service behavior refactor to a later service-level pass.

## 10. Service Compatibility Notes

- Compile checks:
  - `auth-service:compileJava` passed/up-to-date in the first service scan before `chat-service` stopped the build.
  - `upload-service:compileJava` passed/up-to-date.
  - `gateway-service:compileJava` passed/up-to-date.
  - `chat-service:compileJava` failed in cache code, outside the three target Pub/Sub/Kafka/events modules: `RedisRoomListCacheAdapter` calls `ITimeRedisCacheManager.put/evict`, which throw `CreateCacheException`.
  - `friendship-service:compileJava` failed due stale `FriendRequestEvent` expectations: missing `requestId(...)`, missing enum `SENT`, and missing `getRequestId()`.
  - `notification-service:compileJava` failed due stale `FriendRequestEvent` expectations: missing enum `SENT`, `getSenderDisplayName()`, and `getCreatedAt()`.
  - `presence-service:compileJava` failed due old Redis API reference `RedisPubSubLogger`; common-redis now exposes `RedisPubSubObserver` and `Slf4jRedisPubSubLogger`.
  - `user-service:compileJava` failed in cache code, outside the three target modules: `RedisCacheConfig` expects legacy `com.example.common.redis.core.TimeRedisCacheManager` but builder returns `com.example.common.redis.cache.core.TimeRedisCacheManager`.
- Light reference scan:
  - Several services still import `com.example.common.integration.kafka.event.*` wrappers. These will need follow-up migration if wrappers are removed from the frozen standard.
  - Several services still call `kafkaEventProducer.publish(...)`. These will need follow-up migration to `send(...)`.
  - Redis services commonly use `RedisMessage` and `RedisEventSubscriber<RedisMessage<...>>`. These will need follow-up migration if Redis freezes as envelope-first.
  - Test-only references to removed/missing APIs exist, including `IRedisPublisher`, `RealtimeContractValidator`, and `RealtimeContractVersions`.

## 11. Required Tests Before Freeze

### common-events

- `EventEnvelope` JSON round-trip with payload and payload-less event.
- `EventMetadata` required fields, `correlationId`, traceId policy, and non-default `eventVersion`.
- Event type naming validation for valid/invalid lower-dot names.
- Shared catalog coverage: every enum value is either payload-bearing or payload-less, never both.
- Duplicate/stale contract guard for friendship request events.
- Transport-neutral dependency guard: no Kafka/Redis/WebSocket/service imports in main source.

### common-kafka

- Producer API guard: no frozen public Kafka Publisher API.
- Producer sends `EventEnvelope` with metadata headers: eventId, eventType, correlationId, sourceService, createdAt, eventVersion, and traceId if added.
- Serializer/deserializer round-trip through `EventEnvelopeKafkaSerializer` and `EventEnvelopeKafkaDeserializer`.
- Spring auto-configuration test proving `ErrorHandlingDeserializer` and the delegate deserializer are actually used.
- Unknown event policy tests for FAIL and DROP, including observer/DLQ behavior.
- Retry/DLQ tests for retryable, non-retryable, destination topic, partition behavior, and DLQ headers.
- Dispatcher tests for duplicate handlers, invalid handler event types, and handler exceptions.
- Idempotency/ordering contract tests or documentation tests for eventId and Kafka key semantics.

### common-redis-pubsub

- Publisher serializes and publishes `EventEnvelope` only.
- Serializer/deserializer round-trip including non-default `eventVersion`.
- Unknown event deserialization and no-subscriber dispatch tests.
- Dispatcher delivers `EventEnvelope<T>` to subscribers without `RedisMessage` in the standard path.
- Duplicate subscriber prevention and invalid subscriber event type validation.
- Listener tests for deserialize failures and best-effort swallow/log behavior.
- Channel naming tests for `RedisChannels`.
- Guard test that Pub/Sub module does not contain Redis cache logic.

## 12. Final Verdict

- Freeze now: no.
- Minimum required changes before freezing:
  - Make `common-events` the only owner of shared event contracts and remove/fix stale friendship request duplicates.
  - Make Kafka producer/consumer naming clean and envelope-first, with no public Publisher API in the frozen standard.
  - Wire Kafka serialization, `ErrorHandlingDeserializer`, retry, and DLQ policy as a complete standard path.
  - Make Redis Pub/Sub subscriber dispatch envelope-first and preserve `eventVersion`.
  - Decide traceId/correlationId semantics.
- Can be postponed to service refactor later:
  - Replacing service-specific Kafka wrapper usages.
  - Replacing `kafkaEventProducer.publish(...)` calls with `send(...)`.
  - Migrating Redis service subscribers from `RedisMessage` to `EventEnvelope`.
  - Cleaning service-local channel constants once common channel names are frozen.
