# common-kafka / common-redis / common-events Refactor Result

Source of truth used: review code/common-kafka-redis-events-core-review.md

## 1) What Was Changed

### Core standard-vs-compat boundary hardening
- Kept standard Kafka producer API as send-only in `KafkaEventProducer`.
- Kept publisher wording only in deprecated compatibility surfaces (`KafkaEventPublisher` interfaces/classes in compatibility paths).
- Marked legacy wrapper/event compatibility contracts as deprecated in:
  - `com.example.common.integration.kafka.event.*`
  - `com.example.common.kafka.api.*`

### common-events strict contract updates
- `EventMetadata` now clearly documents and enforces:
  - `eventVersion` defaulting to `1`
  - trace policy constant (`TRACE_POLICY = correlationId-only`)
- `SharedEventCatalog` now exposes explicit version policy helpers:
  - `getSupportedEventVersion(...)`
  - `validateEventVersionOrThrow(...)`
- Deprecated `FriendRequestEvent` kept as compatibility DTO only; canonical shared friendship contract remains `FriendRequestPayload` mapped in `SharedEventCatalog`.
- Added contract tests verifying:
  - canonical friendship payload mapping
  - deprecated stale type is non-canonical
  - version policy default + rejection behavior

### common-kafka strict contract updates
- `DefaultKafkaEventProducer` validates event version with shared catalog before send.
- Producer metadata headers include `eventVersion` end-to-end.
- `KafkaEventDispatcher` now supports strict unknown-event policy with observer callback:
  - FAIL default behavior retained
  - DROP behavior observable via `logUnknownEvent(...)`
- `KafkaRetryDlqPolicy` hardened with explicit attempts/backoff and safe DLQ partition strategy.
- `KafkaAutoConfiguration` strengthened:
  - producer/consumer serde property exposure for envelope serializer/deserializer
  - DLQ recoverer header propagation for original topic/partition/offset and exception metadata
- `KafkaContractTest` rewritten for strict freeze checks:
  - standard API has no publish method
  - metadata headers and observer callback behavior
  - unknown event FAIL/DROP dispatch policy behavior
  - serde roundtrip and unknown-event rejection
  - retry policy defaults and auto-config serde wiring

### common-redis strict contract updates
- Standard dispatch path is envelope-first:
  - `RedisEventDispatcher` now dispatches via `subscriber.onEnvelope(...)`
- Compatibility path retained:
  - `RedisEventSubscriber#onEnvelope` defaults to payload-based `onMessage` bridge
  - deprecated `RedisEventHandler` overrides `onEnvelope` to keep legacy behavior
- `JsonRedisEventSerializer` now preserves and validates `eventVersion` during deserialize:
  - default to `EventMetadata.DEFAULT_EVENT_VERSION` when absent
  - validate with `SharedEventCatalog.validateEventVersionOrThrow(...)`
- Added compatibility alias `IRedisPublisher` -> extends canonical `RedisEventPublisher`.

### Minimal compile-only service compatibility changes
- Updated service call sites that still used removed `KafkaEventProducer.publish(...)` to use `send(...)` with `EventEnvelope` bridging.
- Touched minimal call sites in:
  - auth-service
  - chat-service
  - friendship-service
  - notification-service
- Fixed one compile-only cache exception import mismatch in chat-service cache adapter.

## 2) What Was Deleted / Removed

- Removed Redis dispatcher internal envelope->`RedisMessage` conversion from standard dispatch path.
- Removed standard Kafka producer `publish(...)` usage from common API contract.
- Kept deprecated compatibility wrappers instead of hard delete to preserve migration runway.

## 3) Compatibility Wrappers Kept (Quarantined)

### Kafka
- `com.example.common.kafka.api.IKafkaEvent`
- `com.example.common.kafka.api.KafkaEvent`
- `com.example.common.kafka.api.IKafkaEventPublisher`
- `com.example.common.kafka.api.KafkaEventPublisher`
- `com.example.common.integration.kafka.event.*` wrappers

All above are now explicitly deprecated as compatibility-only APIs.

### Redis
- `com.example.common.redis.api.IRedisMessage`
- `com.example.common.redis.message.RedisMessage`
- `com.example.common.redis.subscriber.RedisEventHandler`
- `com.example.common.redis.api.IRedisPublisher` (new compatibility alias)

### Realtime compatibility shims added for compile stability
- `com.example.common.integration.realtime.RealtimeContractVersions`
- `com.example.common.integration.contract.RealtimeContractValidator`

## 4) Public API Old -> New Mapping

### Kafka producer
- `KafkaEventProducer.publish(topic,key,event)` -> `KafkaEventProducer.send(topic,key,envelope)`
- Standard API keeps `send(...)` only.
- Publisher wording kept only in deprecated compatibility APIs.

### Redis subscriber flow
- Standard path: `RedisEventDispatcher -> RedisEventSubscriber.onEnvelope(...)`
- Compatibility path: `onEnvelope(...)` -> `onMessage(...)` payload bridge

### Event metadata
- `EventMetadata` now consistently carries `eventVersion` (default `1`) and trace policy is correlationId-only.

## 5) Test Execution Results

### Required scoped module tests
- PASS: `./gradlew :common:common-events:test`
- PASS: `./gradlew :common:common-kafka:test`
- PASS: `./gradlew :common:common-redis:test`

### Workspace compile validation
- FAIL: `./gradlew testClasses`
- Current first failing target:
  - `gateway-service/src/test/java/com/example/gateway/config/GatewayCorsIntegrationTest.java`
- Error summary:
  - test instantiates `new GatewayConfig()` but constructor now requires `CorsProperties`.
- This blocker is outside common-events/common-kafka/common-redis scope.

## 6) Freeze-Ready Verdict

- For scoped common modules (`common-events`, `common-kafka`, `common-redis`): freeze-ready for the strict core-review criteria in this pass.
- For repository-wide freeze gate: not yet green due out-of-scope `testClasses` failure in gateway-service tests.
