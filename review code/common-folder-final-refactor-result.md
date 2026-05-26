# Common Folder Final Refactor Result

Date: 2026-05-06
Scope: chatappBE/common only
Modules: common-events, common-kafka, common-redis
Services: not modified, not scanned for compatibility
Versioning: none added, none restored

## 1. Changed Files/Classes

### common-events
- `common-events/src/main/java/com/example/common/event/EventEnvelope.java`
  - Removed `implements Event<T>` so `EventEnvelope<T>` is now the standalone canonical event shape.
- `common-events/src/main/java/com/example/common/event/SharedEventCatalog.java`
  - Renamed `enforcePublishPayloadContract(...)` to `validatePayloadContract(...)`.
  - Updated docs to describe a transport-neutral payload validation hook.
- `common-events/src/test/java/com/example/common/event/contract/SharedEventModelContractTest.java`
  - Removed `Event` interface assertions.
  - Updated payload-contract tests to use `validatePayloadContract(...)`.
  - Changed the generic round-trip string-envelope test to use a non-catalog event type (`test.string.event`) instead of a shared catalog event.

### common-kafka
- `common-kafka/src/main/java/com/example/common/kafka/observability/KafkaEventObserver.java`
  - Removed deprecated `logPublish(...)` and `logError(...)` aliases.
  - Kept only `logProduceSuccess(...)`, `logProduceError(...)`, `logDispatch(...)`, `logDispatchError(...)`, and `logUnknownEvent(...)`.
- `common-kafka/src/main/java/com/example/common/kafka/observability/Slf4jKafkaEventLogger.java`
  - Updated wording to producer/consumer/dispatch terminology.
- `common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventProducer.java`
  - Enforced strict shared-event mode: producer now rejects event types outside `SharedEventCatalog`.
  - Switched to `SharedEventCatalog.validatePayloadContract(...)`.
- `common-kafka/src/main/java/com/example/common/kafka/serialization/EventEnvelopeKafkaDeserializer.java`
  - Made unknown payload-bearing event types fail explicitly and consistently.
- `common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventHandler.java`
  - Removed stale Redis-oriented Javadoc wording.
- `common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventDispatcher.java`
  - Updated Javadoc to match real `UnknownKafkaEventPolicy` behavior.
- `common-kafka/src/main/java/com/example/common/kafka/retry/KafkaRetryDlqPolicy.java`
  - Removed unused `retryTopic(...)` API.
- `common-kafka/src/main/java/com/example/common/kafka/topic/KafkaTopics.java`
  - Removed `TOPIC_SYSTEM_RETRY`.
  - Retained only the DLQ transport constant.
- `common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java`
  - Added key deserializer wiring (`StringDeserializer.class`) to common consumer properties.
  - Added canonical DLQ header copying for `eventId`, `eventType`, `correlationId`, `sourceService`, and `createdAt`.
  - Centralized DLQ header creation into the same configuration path used by the error handler.
- `common-kafka/src/test/java/com/example/common/kafka/contract/KafkaContractTest.java`
  - Added tests for observer alias removal, no publish wording, strict producer rejection, strict deserializer rejection, FAIL/DROP dispatch behavior, retry/DLQ surface, and DLQ header parity.

### common-redis
- `common-redis/src/main/java/com/example/common/redis/config/RedisAutoConfiguration.java`
  - Replaced Redis-specific registry wrappers with `EventPayloadRegistry` and `DefaultEventPayloadRegistry` directly.
- `common-redis/src/main/java/com/example/common/redis/dispatcher/RedisEventDispatcher.java`
  - Validates every subscriber `eventType()` with `EventContractValidator.validateEventNameOrThrow(...)` before registration.
  - Still rejects duplicates.
- `common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisEventSerializer.java`
  - Wraps invalid event-name syntax in `RedisPubSubException` for consistent deserialize error taxonomy.
- `common-redis/src/main/java/com/example/common/redis/publisher/DefaultRedisEventPublisher.java`
  - Switched to `SharedEventCatalog.validatePayloadContract(...)`.
- `common-redis/src/main/java/com/example/common/redis/listener/RedisPubSubSubscriberAdapter.java`
  - Added low-risk rename of the Spring `MessageListener` adapter from `RedisEventListener`.
- `common-redis/src/test/java/com/example/common/redis/contract/RedisContractTest.java`
  - Rewritten around `EventPayloadRegistry` instead of deleted Redis registry wrappers.
  - Added tests for deleted wrapper classes, deleted message package, invalid subscriber event types, and deserialize invalid-event wrapping.

## 2. Deleted Files/Classes/Packages

### common-events
- `common-events/src/main/java/com/example/common/event/Event.java`

### common-kafka
- Empty package directory: `common-kafka/src/main/java/com/example/common/integration/kafka`
- Public retry-topic surface removed:
  - `KafkaRetryDlqPolicy.retryTopic(...)`
  - `KafkaTopics.TOPIC_SYSTEM_RETRY`

### common-redis
- `common-redis/src/main/java/com/example/common/redis/registry/RedisEventRegistry.java`
- `common-redis/src/main/java/com/example/common/redis/registry/DefaultRedisEventRegistry.java`
- `common-redis/src/main/java/com/example/common/redis/listener/RedisEventListener.java`
- Empty package directory: `common-redis/src/main/java/com/example/common/redis/registry`
- Empty package directory: `common-redis/src/main/java/com/example/common/redis/message`

## 3. Removed Deprecated APIs

### Kafka
- Removed deprecated observer aliases from `KafkaEventObserver`:
  - `logPublish(...)`
  - `logError(...)`

### Redis
- Removed Redis-specific compatibility registry wrapper surface:
  - `RedisEventRegistry`
  - `DefaultRedisEventRegistry`

### common-events
- Removed the extra public `Event<T>` abstraction.

No deprecated APIs remain in the three target modules.

## 4. Kafka Unknown-Event Policy Chosen

Chosen policy: strict shared-event mode.

Behavior:
- `DefaultKafkaEventProducer` rejects any event type not present in `SharedEventCatalog`.
- `EventEnvelopeKafkaDeserializer` fails for unknown payload-bearing event types with a consistent deserialize failure path.
- `KafkaEventDispatcher` now only handles envelopes that already passed validation/deserialization.
- `UnknownKafkaEventPolicy` applies only when an envelope has a known event type but no registered `KafkaEventHandler`.
  - `FAIL`: throws `KafkaMessagingException`
  - `DROP`: logs through `KafkaEventObserver.logUnknownEvent(...)` and returns

This removes the previous split where the producer allowed service-local event types that the default Kafka deserializer could not reconstruct.

## 5. Kafka Retry/DLQ Policy Chosen

Chosen policy: simple in-place retry plus DLQ.

Behavior:
- Retries are performed in place by `DefaultErrorHandler` using `FixedBackOff`.
- No retry-topic publishing is exposed or implemented.
- `KafkaRetryDlqPolicy` now exposes only the behavior actually used:
  - `backoffMs()`
  - `retryAttempts()`
  - `deadLetterTopic(...)`
  - `deadLetterPartition(...)`
  - `isRetryable(...)`
- `KafkaTopics` retains `TOPIC_SYSTEM_DEAD_LETTER` only.

DLQ metadata parity now includes:
- `eventId`
- `eventType`
- `correlationId`
- `sourceService`
- `createdAt`

## 6. Redis Registry Cleanup Result

Completed.

Result:
- `common-redis` no longer has a Redis-specific marker registry or wrapper implementation.
- Auto-configuration now uses:
  - `DefaultEventPayloadRegistry`
  - `EventPayloadRegistry`
  - `SharedEventCatalog.registerAll(...)`
- `JsonRedisEventSerializer` continues to depend on `EventPayloadRegistry`, which is now the only registry abstraction in use.

This removes the last Redis compatibility layer that added no Redis-specific behavior.

## 7. Tests Run and Results

Commands run:

```text
./gradlew :common:common-events:compileJava :common:common-kafka:compileJava :common:common-redis:compileJava :common:common-events:test :common:common-kafka:test :common:common-redis:test
```

Result:

```text
BUILD SUCCESSFUL
```

Validated by tests:
- No deprecated public Kafka observer aliases remain.
- No Kafka public API method uses publish wording.
- Kafka producer rejects unknown payload-bearing event types.
- Kafka deserializer rejects unknown payload-bearing event types.
- Kafka dispatcher FAIL/DROP behavior is covered for known event types with no handler.
- Kafka retry/DLQ surface matches actual configuration behavior.
- Kafka DLQ metadata headers preserve canonical producer metadata.
- Redis registry wrappers no longer exist.
- Redis message package is gone from the classpath.
- Redis dispatcher rejects null, blank, and malformed subscriber event types.
- Redis deserialize invalid event names are wrapped as `RedisPubSubException`.

## 8. Remaining Blockers, If Any

None found inside `chatappBE/common` for the requested blocker set.

Notes:
- Services were intentionally not updated and may still require migration to the final common APIs.
- No service compile or compatibility scan was performed.

## 9. Freeze Verdict

### common-events
Verdict: ready to freeze.

Reasons:
- Transport-neutral.
- No duplicate event abstraction remains.
- Canonical payload validation API is now transport-neutral in naming.
- Tests pass.

### common-kafka
Verdict: ready to freeze.

Reasons:
- No public publisher wording remains.
- Strict unknown-event behavior is coherent across producer, deserializer, and dispatcher.
- Retry/DLQ API now matches implemented behavior.
- DLQ metadata parity is covered.
- Tests pass.

### common-redis
Verdict: ready to freeze.

Reasons:
- No Redis message path remains.
- No Redis registry wrapper remains.
- Subscriber registration is validated.
- Deserialize error taxonomy is consistent.
- Tests pass.

Overall verdict: the requested remaining freeze blockers inside `chatappBE/common` are resolved.