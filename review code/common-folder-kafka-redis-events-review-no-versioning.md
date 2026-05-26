## 1. Executive Summary

- Is the common folder freeze-ready? No. The dependency direction is good, and the three focused modules already have useful contract tests, but the shared foundation still exposes stale compatibility APIs and Kafka/Redis API boundaries that should not be frozen yet.
- Are common-events, common-kafka, and common-redis freeze-ready?
  - common-events: No. It is close, but stale compatibility contracts remain in the shared contract module.
  - common-kafka: No. Kafka still exposes Publisher wording in public/auto-configured APIs, duplicates semantic event aliases, and contains Kafka-specific wrapper events that compete with EventEnvelope.
  - common-redis: No. The module is Pub/Sub-focused and keeps cache logic out, but the subscriber API is still payload-first in practice and legacy RedisMessage compatibility is still visible from canonical APIs.
- Biggest blockers:
  - Kafka frozen API still exposes Publisher compatibility beans/classes.
  - KafkaTopics and Kafka wrapper DTOs duplicate semantic event contracts already owned by common-events.
  - Kafka deserialization rejects unknown/custom event types before dispatcher unknown-event policy can apply.
  - Redis subscriber contract is not truly EventEnvelope-first.
  - Redis canonical interfaces still expose RedisMessage compatibility paths.
  - common-events still contains stale compatibility contracts next to canonical payloads.
- What is already good:
  - EventEnvelope<T> is the standard event container across the reviewed modules.
  - SharedEventCatalog centralizes event type to payload mapping.
  - common-events has no direct Kafka, Redis, WebSocket, or service dependency.
  - common-kafka and common-redis each depend on common-events and do not depend on each other.
  - Redis cache code is not mixed into common-redis Pub/Sub code.
  - Targeted common-only tests passed: `:common:common-events:test`, `:common:common-kafka:test`, and `:common:common-redis:test`.
- Versioning was intentionally ignored for this review.
- Services and outside-common modules were intentionally ignored and not compatibility-scanned.

## 2. Scope

- Reviewed boundary: `chatappBE/common`.
- Deep review scope:
  - `chatappBE/common/common-events`
  - `chatappBE/common/common-kafka`
  - `chatappBE/common/common-redis`
- Services were not reviewed or scanned for compatibility.
- Service compile behavior was not reviewed.
- Versioning was intentionally ignored and is not considered in any finding or blocker below.

## 3. Common Folder Structure

Discovered `common-events` structure:

```text
common-events/
  build.gradle
  src/main/java/com/example/common/event
  src/main/java/com/example/common/event/validation
  src/main/java/com/example/common/integration/account
  src/main/java/com/example/common/integration/chat
  src/main/java/com/example/common/integration/contract
  src/main/java/com/example/common/integration/enums
  src/main/java/com/example/common/integration/friendship
  src/main/java/com/example/common/integration/notification
  src/main/java/com/example/common/integration/presence
  src/main/java/com/example/common/integration/realtime
  src/main/java/com/example/common/integration/user
  src/test/java/com/example/common/event/contract/SharedEventModelContractTest.java
```

Discovered `common-kafka` structure:

```text
common-kafka/
  build.gradle
  src/main/java/com/example/common/integration/kafka/event
  src/main/java/com/example/common/kafka/api
  src/main/java/com/example/common/kafka/config
  src/main/java/com/example/common/kafka/consumer
  src/main/java/com/example/common/kafka/exception
  src/main/java/com/example/common/kafka/flow
  src/main/java/com/example/common/kafka/observability
  src/main/java/com/example/common/kafka/producer
  src/main/java/com/example/common/kafka/retry
  src/main/java/com/example/common/kafka/serialization
  src/main/java/com/example/common/kafka/topic
  src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  src/test/java/com/example/common/kafka/contract/KafkaContractTest.java
```

Discovered `common-redis` structure:

```text
common-redis/
  build.gradle
  src/main/java/com/example/common/redis/api
  src/main/java/com/example/common/redis/channel
  src/main/java/com/example/common/redis/config
  src/main/java/com/example/common/redis/dispatcher
  src/main/java/com/example/common/redis/exception
  src/main/java/com/example/common/redis/flow
  src/main/java/com/example/common/redis/listener
  src/main/java/com/example/common/redis/message
  src/main/java/com/example/common/redis/observability
  src/main/java/com/example/common/redis/publisher
  src/main/java/com/example/common/redis/registry
  src/main/java/com/example/common/redis/serialization
  src/main/java/com/example/common/redis/subscriber
  src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  src/test/java/com/example/common/redis/contract/RedisContractTest.java
```

## 4. Dependency Direction Inside Common

- common-events has no Gradle dependency on common-kafka, common-redis, common-websocket, services, or higher-level modules. Its reviewed build file only pulls Jackson/Lombok/test dependencies.
- common-kafka correctly depends on common-events via `api project(':common:common-events')`.
- common-redis correctly depends on common-events via `api project(':common:common-events')`.
- common-kafka does not depend on common-redis.
- common-redis does not depend on common-kafka.
- No circular dependency was found among common-events, common-kafka, and common-redis.
- Source imports inside the reviewed modules match the intended direction: common-kafka/common-redis import common event contracts; common-events does not import Kafka or Redis types.

## 5. common-events Review

Issue: stale FriendRequestEvent remains next to canonical FriendRequestPayload.

- Severity: Medium
- Exact file/class: `common-events/src/main/java/com/example/common/integration/friendship/FriendRequestEvent.java`, `FriendRequestEvent`
- Why it matters before freeze: common-events owns shared event contracts. Keeping a deprecated event-shaped compatibility payload next to `FriendRequestPayload` preserves the exact conflict the catalog is trying to resolve. Even though `SharedEventCatalog` maps friend request event types to `FriendRequestPayload`, freezing this class keeps an attractive stale contract in the shared foundation.
- Recommended fix: remove `FriendRequestEvent` from the frozen shared contract surface, or move it to an explicit non-frozen compatibility package/module. Keep `FriendRequestPayload` as the only canonical friend request payload and keep tests proving the catalog maps all friend request event types to it.

Issue: deprecated RealtimeContractValidator duplicates the canonical validation entrypoint.

- Severity: Medium
- Exact file/class: `common-events/src/main/java/com/example/common/integration/contract/RealtimeContractValidator.java`, `RealtimeContractValidator`
- Why it matters before freeze: the frozen shared contracts module should expose one canonical validator path. This facade is deprecated, but it still provides a second public entrypoint and keeps older naming alive in the event contract package.
- Recommended fix: remove it from the frozen API, or move it to a clearly non-frozen compatibility area. The canonical entrypoint should be `EventContractValidator`.

Issue: EventEnvelope itself only enforces non-null metadata.

- Severity: Low
- Exact file/class: `common-events/src/main/java/com/example/common/event/EventEnvelope.java`, `EventEnvelope`
- Why it matters before freeze: this is mostly a good transport-neutral design, but invalid payload combinations can still be constructed directly and are only rejected later by publisher/serializer paths that explicitly call `SharedEventCatalog.enforcePublishPayloadContract`.
- Recommended fix: keep EventEnvelope lightweight, but add a canonical common-events validation helper or factory for shared events and test it in common-events. Make publisher validation a second line of defense, not the only discoverable contract check.

Issue: common-events test coverage is concentrated in one contract test class.

- Severity: Low
- Exact file/class: `common-events/src/test/java/com/example/common/event/contract/SharedEventModelContractTest.java`
- Why it matters before freeze: the existing test is useful, but freeze confidence would be stronger with focused tests for transport neutrality, duplicate/stale contract exclusion, event type naming, and registry behavior as separate concerns.
- Recommended fix: add common-events-only tests that assert no Kafka/Redis/WebSocket imports, every shared event type is catalogued exactly once, friend request types use only `FriendRequestPayload`, and stale compatibility classes are not reachable from the canonical catalog.

Positive findings:

- `EventEnvelope<T>` is transport-neutral and does not depend on Kafka, Redis, WebSocket, or service types.
- `EventMetadata` validates event identity fields and event type syntax.
- `EventContractValidator` enforces lower-dot event type syntax.
- `SharedEventCatalog` is the canonical event type to payload map and covers payload-bearing and payload-less shared event types.
- `DefaultEventPayloadRegistry` rejects conflicting duplicate registrations.

## 6. common-kafka Review

Issue: Publisher wording is still exposed in the Kafka frozen API surface.

- Severity: High
- Exact file/class: `common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java`, `KafkaAutoConfiguration`; `common-kafka/src/main/java/com/example/common/kafka/producer/KafkaEventPublisher.java`; `common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventPublisher.java`; `common-kafka/src/main/java/com/example/common/kafka/api/KafkaEventPublisher.java`; `common-kafka/src/main/java/com/example/common/kafka/api/IKafkaEventPublisher.java`
- Why it matters before freeze: Kafka must use Producer/Consumer wording. The canonical `KafkaEventProducer.send(...)` exists, but auto-configuration still creates a `KafkaEventPublisher` bean, and both producer/api packages expose `publish(...)`. Freezing this would preserve mixed terminology as a supported shared API.
- Recommended fix: remove Publisher beans from `KafkaAutoConfiguration`, remove or move Publisher aliases to a non-frozen compatibility module/package, and keep the frozen public Kafka producer contract as `KafkaEventProducer.send(...)`.

Issue: KafkaTopics still contains semantic event-type aliases duplicated from common-events.

- Severity: High
- Exact file/class: `common-kafka/src/main/java/com/example/common/kafka/topic/KafkaTopics.java`, `KafkaTopics`
- Why it matters before freeze: `KafkaTopics` should be route constants only. Constants such as `ACCOUNT_CREATED`, `CHAT_MESSAGE_SENT`, and `NOTIFICATION_REQUESTED` duplicate semantic event types owned by common-events and create a second source of truth.
- Recommended fix: remove semantic aliases from the frozen Kafka topic class. Keep only true Kafka routes such as aggregate topics and infrastructure topics.

Issue: Kafka-specific wrapper DTOs duplicate EventEnvelope and shared payload contracts.

- Severity: High
- Exact file/class: `common-kafka/src/main/java/com/example/common/integration/kafka/event/*`, especially `BaseKafkaCompatEvent`, `ChatMessageSentEvent`, `AccountCreatedEvent`, `FriendshipEvent`, and `FriendRequestKafkaEvent`
- Why it matters before freeze: the standard event format is `EventEnvelope<T>`. These wrapper DTOs reintroduce transport-specific event classes and duplicate semantic event types already owned by common-events. `FriendRequestKafkaEvent` is worse because it wraps the stale `FriendRequestEvent` instead of canonical `FriendRequestPayload`.
- Recommended fix: remove these wrappers from the frozen module or quarantine them outside the frozen API. Use only `EventEnvelope<Payload>` with event types from common-events.

Issue: Kafka deserialization rejects unknown/custom event types before dispatcher policy can apply.

- Severity: High
- Exact file/class: `common-kafka/src/main/java/com/example/common/kafka/serialization/EventEnvelopeKafkaDeserializer.java`, `EventEnvelopeKafkaDeserializer`; `common-kafka/src/main/java/com/example/common/kafka/consumer/UnknownKafkaEventPolicy.java`
- Why it matters before freeze: the producer side allows syntactically valid event types not in SharedEventCatalog, but the Kafka deserializer calls `registry.resolvePayload(eventType)` for non-payload-less events. That means a valid but unregistered event can fail during deserialization and never reach `KafkaEventDispatcher`, where `UnknownKafkaEventPolicy` is supposed to decide fail/drop behavior.
- Recommended fix: make unknown handling consistent. Either require all Kafka-consumed event types to be registered and document that dispatcher unknown policy only means "known envelope, no handler", or add an explicit deserialization unknown policy/fallback. Add tests proving unknown behavior at deserializer plus dispatcher boundaries.

Issue: consumer serializer/deserializer properties are incomplete as a frozen common wiring contract.

- Severity: Medium
- Exact file/class: `common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java`, `commonKafkaConsumerProperties`
- Why it matters before freeze: the common consumer properties wire `ErrorHandlingDeserializer` for values, but do not set a key deserializer or provide a common way to inject the configured ObjectMapper/registry into `EventEnvelopeKafkaDeserializer`. This makes the shared auto-configuration less complete than the producer side and harder to rely on as a foundation.
- Recommended fix: add key deserializer wiring, make registry/ObjectMapper wiring explicit, and add a common-only test that validates the full properties map expected by consumers.

Issue: retry/DLQ policy is only partially connected to the error handler.

- Severity: Medium
- Exact file/class: `common-kafka/src/main/java/com/example/common/kafka/retry/KafkaRetryDlqPolicy.java`; `common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java`, `kafkaDefaultErrorHandler`
- Why it matters before freeze: `KafkaRetryDlqPolicy` exposes retry topic and retryability decisions, but the auto-configured handler only uses fixed backoff and a dead-letter recoverer, with a hard-coded not-retryable exception. A frozen policy should match the actual handler behavior.
- Recommended fix: either wire all policy methods into the handler or simplify the policy to only the behavior actually supported. Add tests around retryable/non-retryable exceptions and DLQ routing.

Issue: DLQ metadata headers are not consistent with producer metadata headers.

- Severity: Medium
- Exact file/class: `common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java`, `kafkaDefaultErrorHandler`
- Why it matters before freeze: the producer writes common metadata headers, but the DLQ recoverer only copies a subset. Durable cross-service event streams need consistent diagnostic headers on normal and DLQ records.
- Recommended fix: copy the full common metadata header set into DLQ records and test it.

Issue: ordering and idempotency rules are not captured in the common Kafka API.

- Severity: Medium
- Exact file/class: `common-kafka/src/main/java/com/example/common/kafka/producer/KafkaEventProducer.java`; `common-kafka/src/main/java/com/example/common/kafka/flow/KafkaEventRoutingContext.java`
- Why it matters before freeze: Kafka is the durable cross-service stream, so shared foundations should state how keys relate to ordering and what consumers can use for idempotency. The API accepts an arbitrary key, but there is no common guidance, helper, or test for key choice.
- Recommended fix: add common documentation and, if useful, helper methods for deriving Kafka keys from aggregate identifiers. Add common-only tests for key propagation and metadata availability.

Issue: Kafka observability comments still use publish wording.

- Severity: Low
- Exact file/class: `common-kafka/src/main/java/com/example/common/kafka/observability/KafkaEventObserver.java`; `common-kafka/src/main/java/com/example/common/kafka/observability/Slf4jKafkaEventLogger.java`
- Why it matters before freeze: the methods are mostly Producer-named, but comments still say publish in places. This is smaller than the API issue, but frozen docs should reinforce Producer/Consumer vocabulary.
- Recommended fix: update comments to produce/consume/dispatch wording.

Positive findings:

- `KafkaEventProducer.send(...)` is a clean canonical producer method.
- `DefaultKafkaEventProducer` uses `EventEnvelope<?>`, validates event type syntax, enforces SharedEventCatalog payload contracts before send, and adds metadata headers.
- `KafkaEventDispatcher` supports duplicate handler detection and fail/drop policy for no-handler dispatch.
- `ErrorHandlingDeserializer` is present in the common consumer property map.
- common-kafka has a common-only contract test covering producer API naming, metadata headers, serializer/deserializer round trip, unknown dispatcher behavior, and retry/DLQ defaults.

## 7. common-redis Pub/Sub Review

Issue: Redis subscriber flow is not truly EventEnvelope-first.

- Severity: High
- Exact file/class: `common-redis/src/main/java/com/example/common/redis/subscriber/RedisEventSubscriber.java`, `RedisEventSubscriber`
- Why it matters before freeze: the dispatcher calls `onEnvelope(...)`, but the interface requires implementors to implement payload-level `onMessage(T message)`. That makes payload-first handling the canonical implementation path and weakens the standard `EventEnvelope<T>` contract.
- Recommended fix: make `onEnvelope(EventEnvelope<T> envelope)` the primary abstract method. If a payload-only convenience remains, make it explicitly deprecated compatibility or a separate adapter.

Issue: RedisMessage compatibility is still exposed from canonical APIs.

- Severity: Medium
- Exact file/class: `common-redis/src/main/java/com/example/common/redis/publisher/RedisEventPublisher.java`; `common-redis/src/main/java/com/example/common/redis/subscriber/RedisEventHandler.java`; `common-redis/src/main/java/com/example/common/redis/message/RedisMessage.java`; `common-redis/src/main/java/com/example/common/redis/api/IRedisMessage.java`
- Why it matters before freeze: the standard shared event format is `EventEnvelope<T>`. Keeping `RedisMessage` overloads and handler paths visible from canonical Redis contracts freezes an older envelope model alongside the new one.
- Recommended fix: remove `RedisMessage` from canonical publisher/subscriber paths. If needed, move it to a compatibility adapter that converts to EventEnvelope before entering Pub/Sub.

Issue: RedisEventHandler is deprecated but its documentation still calls it canonical.

- Severity: Medium
- Exact file/class: `common-redis/src/main/java/com/example/common/redis/subscriber/RedisEventHandler.java`, `RedisEventHandler`
- Why it matters before freeze: a deprecated type that says "Canonical Redis callback contract" sends the wrong signal to downstream users and competes with `RedisEventSubscriber`.
- Recommended fix: update or remove the type before freeze. Keep one canonical subscriber contract and make all compatibility naming unambiguous.

Issue: Redis dispatcher does not validate subscriber eventType strings at registration.

- Severity: Medium
- Exact file/class: `common-redis/src/main/java/com/example/common/redis/dispatcher/RedisEventDispatcher.java`, `RedisEventDispatcher`
- Why it matters before freeze: common-events defines event type naming rules, and Kafka validates handler event types during dispatcher construction. Redis currently only collects subscribers by `eventType()` and detects duplicates. Invalid event type strings can be registered and silently never match valid envelopes.
- Recommended fix: call `EventContractValidator.validateEventNameOrThrow(subscriber.eventType())` during dispatcher construction and add tests for invalid subscriber event types.

Issue: Redis registry can be manually drifted from SharedEventCatalog.

- Severity: Medium
- Exact file/class: `common-redis/src/main/java/com/example/common/redis/registry/DefaultRedisEventRegistry.java`; `common-redis/src/test/java/com/example/common/redis/contract/RedisContractTest.java`
- Why it matters before freeze: auto-configuration pre-populates shared payload mappings correctly, but the registry type itself does not prevent a manually created registry from registering a shared event type to the wrong class before the catalog is loaded. The current Redis tests even use shared event type strings with `String.class` for serializer round-trip convenience.
- Recommended fix: make shared event mappings canonical regardless of construction path, or at minimum update tests so shared event types always use SharedEventCatalog payload classes. Keep custom registrations for non-shared event types only.

Issue: Redis serializer has inconsistent error classification for invalid event type syntax.

- Severity: Low
- Exact file/class: `common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisEventSerializer.java`, `JsonRedisEventSerializer`
- Why it matters before freeze: most deserialize failures are wrapped as `RedisPubSubException`, but invalid event type syntax is thrown directly from `EventContractValidator`. The listener catches it, but callers using the serializer directly get inconsistent exception types.
- Recommended fix: wrap event type syntax failures in `RedisPubSubException` with the same classified deserialize style used for other failures.

Issue: Redis channel helper methods do not validate required identifiers.

- Severity: Low
- Exact file/class: `common-redis/src/main/java/com/example/common/redis/channel/RedisChannels.java`, `chatRoom`, `notificationUser`, `presenceRoom`
- Why it matters before freeze: passing null creates channels ending with `null`, which can silently fan out to an invalid realtime route.
- Recommended fix: validate UUID arguments with `Objects.requireNonNull` and add tests.

Issue: Redis dispatch success observability is implicit.

- Severity: Low
- Exact file/class: `common-redis/src/main/java/com/example/common/redis/listener/RedisEventListener.java`; `common-redis/src/main/java/com/example/common/redis/dispatcher/RedisEventDispatcher.java`; `common-redis/src/main/java/com/example/common/redis/observability/RedisPubSubObserver.java`
- Why it matters before freeze: receive, deserialize failure, publish, no-subscriber, subscriber count, and errors are observable. Successful subscriber dispatch is only implied by receive and absence of error.
- Recommended fix: add an optional `logDispatch` hook or document that receive means accepted-for-dispatch only.

Positive findings:

- common-redis is Pub/Sub-focused. No cache classes or cache operations were found in the reviewed common-redis source.
- Redis cache logic is separated into a sibling common module, not mixed into common-redis Pub/Sub code.
- Publisher terminology is appropriate for Redis Pub/Sub.
- `RedisEventPublisher.publish(String, EventEnvelope<?>)` uses EventEnvelope as the primary method.
- `DefaultRedisEventPublisher` validates event type syntax and SharedEventCatalog payload contracts before Redis I/O.
- `JsonRedisEventSerializer` deserializes EventEnvelope using a registry and handles payload-less event types distinctly.
- `RedisEventDispatcher` drops no-subscriber events without throwing, which matches best-effort realtime fanout semantics.
- Publisher records subscriber count and does not fail merely because subscriber count is zero.

## 8. Cross-module Consistency Inside Common

- Naming consistency:
  - common-events uses event/payload/catalog vocabulary cleanly.
  - common-kafka is inconsistent because Producer and Publisher names coexist.
  - common-redis mostly uses Publisher/Subscriber vocabulary, but `RedisEventHandler` remains as deprecated compatibility.
- Package consistency:
  - common-events package split is clear: `event` for foundation contracts and `integration.*` for domain payloads/enums.
  - common-kafka has an odd `com.example.common.integration.kafka.event` package containing deprecated transport wrapper DTOs. That package is inconsistent with the goal that common-events owns shared event contracts.
  - common-redis package layout is coherent for Pub/Sub, but `api` and `message` are compatibility surfaces that should not be part of a frozen canonical path.
- EventEnvelope consistency:
  - EventEnvelope is the standard shape in common-events, common-kafka producer/serde/dispatcher, and common-redis publisher/serializer/listener.
  - Redis subscriber implementation is the main inconsistency because it still asks implementors for payload-first `onMessage`.
- Catalog/registry consistency:
  - SharedEventCatalog is the canonical event type to payload registry owner.
  - Kafka deserializer uses a default common-events registry but does not expose the same service-facing registry adapter pattern as Redis.
  - Redis exposes a registry adapter, but manually constructed registries can drift from SharedEventCatalog.
- Kafka vs Redis boundary:
  - Kafka is positioned as durable cross-service stream through producer/consumer packages, retry/DLQ, and Kafka topics.
  - Redis is positioned as realtime fanout through channel constants, listener, publisher/subscriber, and best-effort no-subscriber behavior.
  - common-kafka and common-redis do not depend on each other.
- Duplicate abstractions:
  - Kafka has duplicate old/new APIs: `KafkaEventProducer` and multiple `KafkaEventPublisher`/`IKafkaEventPublisher` paths.
  - Kafka wrapper DTOs duplicate EventEnvelope plus common-events payloads.
  - Redis has EventEnvelope plus RedisMessage compatibility.
  - RedisEventRegistry is only a marker over EventPayloadRegistry; acceptable for injection compatibility, but it should not allow canonical shared mappings to drift.
- Stale/deprecated compatibility APIs:
  - common-events: `FriendRequestEvent`, `RealtimeContractValidator`.
  - common-kafka: `KafkaEventPublisher`, `DefaultKafkaEventPublisher`, `IKafkaEvent`, `IKafkaEventPublisher`, `KafkaEvent`, `com.example.common.integration.kafka.event.*`.
  - common-redis: `RedisMessage`, `IRedisMessage`, `IRedisPublisher`, `RedisEventHandler`.

Versioning was not reviewed in this section.
Service compatibility was not reviewed in this section.

## 9. Freeze Blockers Inside Common Only

High: must fix before freeze

- Remove Publisher wording from frozen common-kafka APIs and auto-configuration.
- Remove semantic event-type aliases from `KafkaTopics`.
- Remove or quarantine Kafka-specific wrapper DTOs in `com.example.common.integration.kafka.event`.
- Make Kafka unknown-event behavior consistent across deserializer and dispatcher.
- Make Redis subscriber flow truly EventEnvelope-first.

Medium: should fix before freeze

- Remove or quarantine `FriendRequestEvent` from common-events frozen contracts.
- Remove or quarantine `RealtimeContractValidator` from common-events frozen contracts.
- Remove RedisMessage from canonical Redis publisher/subscriber paths.
- Fix `RedisEventHandler` documentation or remove it from the frozen path.
- Validate Redis subscriber event type syntax during dispatcher construction.
- Prevent Redis registry drift for shared event types.
- Complete Kafka consumer serde property wiring.
- Align Kafka retry/DLQ policy with actual handler behavior.
- Copy a consistent metadata header set to Kafka DLQ records.
- Capture Kafka ordering/idempotency expectations in common-kafka.
- Expand common-only tests around the above freeze boundaries.

Low: can fix later

- Add Redis channel helper null validation.
- Add explicit Redis dispatch success observability or document current semantics.
- Normalize wording/comments in Kafka observability to Producer/Consumer vocabulary.
- Split large contract test classes into smaller common-only test classes for maintainability.

Versioning is not listed as a blocker.
Service issues are not listed as blockers.

## 10. Minimal Refactor Plan For Common Only

1. common-events cleanup
   - Keep `EventEnvelope<T>`, `EventMetadata`, `EventContractValidator`, `SharedEventCatalog`, and `EventPayloadRegistry` as the canonical contracts.
   - Remove or quarantine stale compatibility classes from the frozen surface.
   - Add common-events-only tests for transport neutrality and stale contract exclusion.

2. Kafka naming/API cleanup
   - Freeze `KafkaEventProducer.send(...)` as the producer API.
   - Remove auto-configured Publisher beans and Publisher aliases from frozen packages.
   - Remove Kafka wrapper DTOs and semantic aliases from `KafkaTopics`.

3. Kafka serializer/retry/DLQ hardening
   - Define unknown event behavior at deserializer and dispatcher boundaries.
   - Complete ErrorHandlingDeserializer/property wiring.
   - Align retry/DLQ policy methods with actual DefaultErrorHandler behavior.
   - Preserve consistent metadata headers on normal and DLQ records.

4. Redis Pub/Sub envelope-first cleanup
   - Make `RedisEventSubscriber` envelope-first.
   - Move RedisMessage compatibility behind explicit adapter APIs.
   - Validate subscriber event type strings.
   - Keep zero-subscriber behavior best-effort and observable.

5. Cross-module consistency cleanup
   - Ensure common-events owns event semantics.
   - Ensure KafkaTopics and RedisChannels are transport routes only.
   - Ensure common-kafka and common-redis do not introduce semantic aliases for shared event types.
   - Remove stale/deprecated compatibility surfaces from frozen API packages.

6. Common-only tests and freeze validation
   - Run only common-events, common-kafka, and common-redis tests.
   - Add tests that assert no common-kafka to common-redis dependency and no common-redis to common-kafka dependency.
   - Add tests for no transport imports in common-events.
   - Add API-surface tests for Kafka Producer wording and Redis Publisher/Subscriber wording.

No service-level refactor is included.
No versioning work is included.

## 11. Required Tests Before Freeze

common-events tests:

- EventEnvelope serializes/deserializes with metadata and payload.
- EventMetadata rejects missing identity fields and invalid event type syntax.
- EventContractValidator accepts only lower-dot event type names.
- SharedEventCatalog covers every shared event enum value exactly once.
- SharedEventCatalog maps friend request event types only to `FriendRequestPayload`.
- SharedEventCatalog rejects wrong payload classes and payload presence/absence violations.
- DefaultEventPayloadRegistry rejects conflicting duplicate registration.
- common-events source has no Kafka, Redis, WebSocket, or service imports.
- Stale compatibility event classes are not part of the canonical catalog.

common-kafka tests:

- Frozen producer API exposes `send(...)` and no Publisher method/bean in canonical auto-configuration.
- KafkaTopics contains only route constants and no semantic event-type aliases.
- EventEnvelopeKafkaSerializer/EventEnvelopeKafkaDeserializer round-trip shared catalog payloads.
- ErrorHandlingDeserializer property wiring includes value and key deserializer behavior expected by consumers.
- Unknown/custom event behavior is tested at deserializer and dispatcher levels.
- KafkaEventDispatcher routes known events, rejects duplicate handlers, and applies fail/drop no-handler policy.
- DefaultKafkaEventProducer validates payload contracts before send and writes metadata headers.
- DefaultErrorHandler retry/DLQ behavior follows KafkaRetryDlqPolicy.
- DLQ records preserve expected metadata headers.
- Kafka key propagation is tested for ordering/idempotency expectations.
- common-kafka source has no common-redis dependency.

common-redis tests:

- RedisEventPublisher publishes EventEnvelope as the primary path.
- RedisEventSubscriber receives EventEnvelope as the primary path.
- RedisMessage compatibility does not appear in canonical publisher/subscriber flow.
- JsonRedisEventSerializer round-trips shared catalog payloads and rejects malformed envelopes consistently.
- Redis registry cannot override canonical shared event mappings.
- RedisEventDispatcher validates subscriber event type syntax and rejects duplicate subscribers.
- RedisEventDispatcher drops no-subscriber events with observer hook and without throwing.
- Redis publisher logs subscriber count and treats zero subscribers as best-effort, not failure.
- RedisEventListener logs deserialize errors without crashing the Pub/Sub listener.
- RedisChannels helper methods validate required identifiers.
- common-redis source has no cache operations and no common-kafka dependency.

Excluded:

- Service tests.
- Compile checks outside common.
- Versioning tests.

## 12. Final Verdict

- Freeze common folder now? No.
- Freeze each module now?
  - common-events: No. It is close, but stale compatibility contracts should be removed or quarantined before freeze.
  - common-kafka: No. It has high-priority API and contract duplication blockers.
  - common-redis: No. It is Pub/Sub-focused, but subscriber and compatibility surfaces need cleanup before freeze.
- Minimum common-only changes before freezing:
  - Clean common-events stale compatibility contracts.
  - Make common-kafka strictly Producer/Consumer named in frozen APIs.
  - Remove Kafka semantic aliases and wrapper DTOs.
  - Harden Kafka deserializer, ErrorHandlingDeserializer, retry/DLQ, metadata header, and unknown-event behavior.
  - Make Redis Pub/Sub envelope-first end to end.
  - Move RedisMessage compatibility out of canonical Redis APIs.
  - Add the common-only freeze tests listed above.
- What can be postponed:
  - Redis channel helper null validation.
  - Extra Redis dispatch success observability.
  - Comment-only wording cleanup once API names are fixed.
  - Test class splitting for readability.
- Versioning was not considered.
- Services and outside-common modules were not considered.
