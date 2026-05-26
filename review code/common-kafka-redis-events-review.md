# common-kafka / common-redis-pubsub / common-event Review

Scope reviewed:

- `chatappBE/common/common-kafka`
- `chatappBE/common/common-redis` as the current Redis PubSub module
- `chatappBE/common/common-events` as the current common-event module

Business services were not reviewed for behavior. They were searched only to validate dependency direction and consumer compatibility.

## 1. Executive Summary

Current quality: medium, improving, but not freeze-ready. The three common modules now have a clearer center than older common messaging designs: `EventEnvelope` and `EventMetadata` are transport-neutral, Redis has a dedicated JSON envelope serializer, both Kafka and Redis publish paths enforce the shared payload catalog before I/O, and Gradle dependency direction among the three modules is correct.

Freeze readiness: no. The major blockers are Kafka terminology drift, missing Kafka serializer/deserializer and retry/DLQ hardening, silent Kafka unknown-event drops, Redis subscriber API inconsistencies, and public API drift against current service consumers. The scoped common-module tests pass, but they mostly cover contract shape and publisher validation, not operational behavior.

Major blockers:

- Kafka uses `Publisher` / `publish` terminology in its primary API even though the architecture rule says Kafka must use producer/consumer terminology.
- Kafka has no common `EventEnvelope` serializer/deserializer, no `ErrorHandlingDeserializer`, no common consumer factory/container policy, and no wired retry/DLQ strategy.
- `KafkaEventDispatcher` logs and returns for unknown event types, which is unsafe for durable broker semantics because a service listener can accidentally ack and lose the record.
- Current common APIs are not aligned with existing service references: services still reference missing `KafkaTopics` constants, missing `com.example.common.integration.kafka.event.*` wrappers, missing `com.example.common.redis.message.RedisMessage`, missing `RedisEventSubscriber`, and old `com.example.common.kafka.api.*` types.
- Redis PubSub is mostly shaped correctly for realtime fanout, but the common module still exposes `RedisEventHandler` instead of a subscriber-facing contract and auto-configures a `MessageListener` without owning subscription registration.

What is already good:

- `common-events` has no Gradle dependency on Kafka, Redis, WebSocket, or services.
- `common-kafka -> common-events` and `common-redis -> common-events` are the only common-module project edges.
- Kafka and Redis modules do not depend on each other.
- Redis channel names are clearly realtime-prefixed.
- Shared payload validation is centralized in `SharedEventCatalog.enforcePublishPayloadContract`.
- Redis deserialization distinguishes invalid JSON, missing metadata, unknown event type, payload-less violations, and invalid payload content.
- Observability hooks exist for Kafka and Redis, and default loggers avoid raw Redis payload logging.
- Scoped tests passed: `:common:common-events:test`, `:common:common-kafka:test`, `:common:common-redis:test`.

## 2. Current Structure

Discovered module names:

- `settings.gradle:5` includes `common:common-kafka`
- `settings.gradle:8` includes `common:common-redis`
- `settings.gradle:9` includes `common:common-events`
- `settings.gradle:11` also includes `common:common-redis-cache`

`common-events` structure:

```text
com.example.common.event
  Event
  EventEnvelope
  EventMetadata
  EventPayloadRegistry
  DefaultEventPayloadRegistry
  SharedEventCatalog
com.example.common.event.validation
  EventContractValidator
com.example.common.integration.account
com.example.common.integration.chat
com.example.common.integration.enums
com.example.common.integration.friendship
com.example.common.integration.notification
com.example.common.integration.presence
com.example.common.integration.user
```

`common-kafka` structure:

```text
com.example.common.kafka.config
  KafkaAutoConfiguration
com.example.common.kafka.consumer
  KafkaEventDispatcher
  KafkaEventHandler
com.example.common.kafka.exception
  KafkaMessagingException
com.example.common.kafka.flow
  KafkaEventRoutingContext
com.example.common.kafka.observability
  KafkaEventObserver
  Slf4jKafkaEventLogger
com.example.common.kafka.producer
  KafkaEventPublisher
  DefaultKafkaEventPublisher
com.example.common.kafka.topic
  KafkaTopics
```

`common-redis` structure:

```text
com.example.common.redis.channel
  RedisChannels
com.example.common.redis.config
  RedisAutoConfiguration
com.example.common.redis.dispatcher
  RedisEventDispatcher
com.example.common.redis.exception
  RedisPubSubException
com.example.common.redis.flow
  RedisEventRoutingContext
com.example.common.redis.listener
  RedisEventListener
com.example.common.redis.observability
  RedisPubSubObserver
  Slf4jRedisPubSubLogger
com.example.common.redis.publisher
  RedisEventPublisher
  DefaultRedisEventPublisher
com.example.common.redis.registry
  RedisEventRegistry
  DefaultRedisEventRegistry
com.example.common.redis.serialization
  RedisEventSerializer
  JsonRedisEventSerializer
com.example.common.redis.subscriber
  RedisEventHandler
```

Consistency analysis:

- The current physical names are `common-events` and `common-redis`, while the requested architecture names are `common-event` and `common-redis-pubsub`. This is not just cosmetic: `common-redis` is ambiguous beside `common-redis-cache`.
- `common-events` uses singular package `com.example.common.event` but plural module name `common-events`.
- `common-redis` uses package `com.example.common.redis`, which is too broad for a PubSub-only module and overlaps conceptually with cache packages.
- Kafka packages correctly separate `producer` and `consumer`, but the public producer API is named `KafkaEventPublisher`, violating the stated Kafka terminology rule.
- Redis packages contain `publisher` and `subscriber`, but the subscriber contract is named `RedisEventHandler`, while services appear to expect `RedisEventSubscriber`.

## 3. Dependency Direction

Validated build graph:

```text
common-events
  -> external only: Jackson, Lombok

common-kafka
  -> common-events
  -> Spring Kafka, Spring Boot autoconfigure, SLF4J, Jackson

common-redis
  -> common-events
  -> Spring Data Redis, Jackson
```

Validation details:

- `common-kafka/build.gradle:34` declares `api project(':common:common-events')`.
- `common-redis/build.gradle:34` declares `api project(':common:common-events')`.
- `common-events/build.gradle:33-37` declares only Jackson and Lombok in main dependencies.
- Gradle compile classpath dependency reports for all three scoped modules completed successfully.
- Source search found no Kafka, Redis, WebSocket, or business-service imports in `common-events/src/main/java`.
- No circular dependency exists among the three reviewed modules.
- Business services depend on common modules, but the reviewed common modules do not depend back on services.

Transport neutrality validation:

- `EventEnvelope`, `EventMetadata`, `EventPayloadRegistry`, `SharedEventCatalog`, and `EventContractValidator` are transport-neutral in build and imports.
- `common-events` does contain application-level integration contracts such as `ChatMessagePayload`, `PresenceUserOnlinePayload`, and `NotificationRequestedPayload`. This is acceptable only if `common-events` is explicitly the shared cross-service event-contract catalog. If common modules are intended to be infrastructure-only, this is domain leakage.

Consumer compatibility finding from dependency-direction search:

- Existing service source references missing common API packages and constants, for example `com.example.common.kafka.api.*`, `com.example.common.integration.kafka.event.*`, `com.example.common.redis.message.RedisMessage`, `RedisEventSubscriber`, and `KafkaTopics.CHAT_MESSAGE_SENT`.
- This does not create a reverse dependency, but it means the current common modules are not API-freeze aligned with their consumers.

## 4. Kafka Review

Producer API:

- `KafkaEventPublisher` is the public API in `common-kafka/src/main/java/com/example/common/kafka/producer/KafkaEventPublisher.java:10-11`.
- `DefaultKafkaEventPublisher` implements it at `DefaultKafkaEventPublisher.java:23`.
- The API accepts `EventEnvelope<?>`, validates metadata event type, validates payload contract, then calls `KafkaTemplate.send(topic, key, envelope).join()` at `DefaultKafkaEventPublisher.java:37-55`.
- Good: the publish path rejects blank topic early and fails validation before network I/O.
- Issue: naming uses publisher/publish terminology instead of producer/produce/send terminology.
- Issue: the method blocks on `.join()` and returns `void`, hiding Kafka send metadata and making async composition impossible.

Consumer API:

- `KafkaEventHandler<T>` is event-type based and receives `EventEnvelope<T>` at `KafkaEventHandler.java:18-35`.
- `KafkaEventDispatcher` routes by `metadata.eventType` at `KafkaEventDispatcher.java:63-75`.
- Good: duplicate handlers fail at construction via `Collectors.toMap` merge function at `KafkaEventDispatcher.java:31-41`.
- Issue: unknown event types only log and return at `KafkaEventDispatcher.java:68-70`. For Kafka, durable consumption should normally fail or explicitly route to DLQ, not silently drop.

Event routing:

- Topic routing is caller-provided: `publish(String topic, String key, EventEnvelope<?> envelope)`.
- Event type routing is dispatcher-owned.
- There is no common `KafkaTopicResolver` that maps event type to topic or validates that a durable-only topic is being used.

Topic naming:

- `KafkaTopics` currently defines only:
  - `TOPIC_FRIENDSHIP_EVENTS`
  - `TOPIC_FRIENDSHIP_REQUEST_EVENTS`
  - `TOPIC_SYSTEM_DEAD_LETTER`
  - `TOPIC_SYSTEM_RETRY`
- These are at `KafkaTopics.java:23-29`.
- Good: comments state event semantics belong in `common-events`.
- Issue: current service references still expect non-`TOPIC_` constants such as `KafkaTopics.ACCOUNT_CREATED`, `KafkaTopics.CHAT_MESSAGE_SENT`, `KafkaTopics.FRIENDSHIP_EVENTS`, and `KafkaTopics.NOTIFICATION_REQUESTED`. The current constants are not consumer-compatible.
- Issue: retry/DLQ topic constants exist without any common retry/DLQ wiring.

Envelope usage:

- Kafka sends the full `EventEnvelope<?>` as the record value.
- Publisher validation relies on envelope metadata and shared payload catalog.
- Good: envelope is now the canonical value type.

Metadata/header handling:

- `DefaultKafkaEventPublisher` sends `topic`, `key`, and `envelope` only at `DefaultKafkaEventPublisher.java:49`.
- No `ProducerRecord` or `Message<?>` is used, and no Kafka headers are written for `eventId`, `eventType`, `correlationId`, `sourceService`, or schema version.
- This weakens observability, retry diagnostics, DLQ inspection, and downstream filtering.

Serializers/deserializers:

- `common-kafka` has no `serialization` package and no `EventEnvelope` serializer/deserializer classes.
- `KafkaAutoConfiguration.java:17-43` only registers observer, producer, and dispatcher beans.
- No `JsonSerializer`, `JsonDeserializer`, type mapper, trusted-package policy, or envelope-aware payload registry is configured in common Kafka.

`ErrorHandlingDeserializer` usage:

- No `ErrorHandlingDeserializer` configuration exists in `common-kafka`.
- This is a freeze blocker for durable consumer readiness.

Retry strategy:

- No common `DefaultErrorHandler`, backoff policy, retry classification, retry metrics, or retry topic strategy exists.
- `spring-kafka` is on the classpath, but no retry configuration is exposed.

DLQ strategy:

- `KafkaTopics.TOPIC_SYSTEM_DEAD_LETTER` exists at `KafkaTopics.java:28`.
- No `DeadLetterPublishingRecoverer`, DLQ topic resolver, DLQ envelope/header policy, or DLQ observability hook exists in common Kafka.

Observability hooks:

- `KafkaEventObserver` covers publish success/error and dispatch success/error at `KafkaEventObserver.java:14-34`.
- `Slf4jKafkaEventLogger` logs eventId, eventType, correlationId, topic, key, and sourceService at `Slf4jKafkaEventLogger.java:20-60`.
- Good: fields are structured and null-safe.
- Gaps: no Micrometer counters/timers, no tracing context extraction/injection, no MDC, no Kafka record offset/partition in dispatch logs, and method names still use publish terminology.

Idempotency support:

- `EventMetadata.eventId` exists at `EventMetadata.java:26`.
- There is no consumer-side idempotency abstraction, processed-event store, idempotency key extractor, or duplicate-event policy.

Ordering guarantees:

- Kafka caller supplies a key, but common Kafka gives no helper or rule for aggregate keys.
- Chat payloads carry `seq` in `ChatMessagePayload.java:25`, `MessageUpdatedPayload.java:20`, and `MessageDeletedPayload.java:20`, but common Kafka does not enforce ordering or key consistency.

Test coverage:

- Current Kafka contract tests cover routing context mapping, publisher signature, dispatcher routing, duplicate handler failure, observer callbacks, registry behavior, and publisher payload validation.
- Missing Kafka tests are listed in section 12.

## 5. Redis PubSub Review

Publisher API:

- `RedisEventPublisher.publish(String channel, EventEnvelope<?> eventEnvelope)` is at `RedisEventPublisher.java:8-9`.
- `DefaultRedisEventPublisher` validates channel, event type, and payload contract, serializes the envelope, then calls `StringRedisTemplate.convertAndSend` at `DefaultRedisEventPublisher.java:21-44`.
- Good: Redis uses publisher terminology correctly.
- Gap: `convertAndSend` returns subscriber count, but the publisher ignores it and logs success unconditionally.

Subscriber API:

- The common subscriber-facing interface is `RedisEventHandler<T>` at `RedisEventHandler.java:11-20`.
- The package is `com.example.common.redis.subscriber`, but the type name is handler, not subscriber.
- Service source still appears to expect `RedisEventSubscriber` and `RedisMessage`, neither of which exists in the current common Redis module.
- Recommendation: rename or add an adapter contract named `RedisEventSubscriber<T>` with `eventType()` and `onMessage(EventEnvelope<T>)`, then deprecate/remove `RedisEventHandler`.

Channel naming:

- `RedisChannels` uses realtime-prefixed channels:
  - `realtime.chat.room.*`
  - `realtime.notification.user.*`
  - `realtime.presence.*`
- These are defined at `RedisChannels.java:15-24`.
- Good: Redis route names are clearly realtime fanout channels, not durable event topics.

eventType routing:

- Redis routing uses channel for fanout scope and envelope `metadata.eventType` for handler dispatch.
- This is the correct split for realtime PubSub.

Dispatcher design:

- `RedisEventDispatcher` maps event type to handler at construction and dispatches at `RedisEventDispatcher.java:17-45`.
- Duplicate event handlers fail at construction at `RedisEventDispatcher.java:19-29`.
- Unknown event types log and return at `RedisEventDispatcher.java:39-41`, which is acceptable for best-effort realtime fanout but should have an observer hook.

Registry design:

- `RedisEventRegistry` extends `EventPayloadRegistry` without adding behavior at `RedisEventRegistry.java:16-17`.
- `DefaultRedisEventRegistry` extends `DefaultEventPayloadRegistry` at `DefaultRedisEventRegistry.java:13`.
- This is a duplicate adapter abstraction. It is reasonable as a compatibility facade, but it should not become another source of event contract truth.

Serializers/deserializers:

- `RedisEventSerializer` defines `serialize` and `deserialize` at `RedisEventSerializer.java:8-12`.
- `JsonRedisEventSerializer` serializes the whole envelope and deserializes by reading `metadata.eventType`, resolving a payload class from the registry, then building `EventMetadata` and `EventEnvelope` at `JsonRedisEventSerializer.java:42-135`.
- Good: payload-less event types are handled without registry lookup at `JsonRedisEventSerializer.java:82-88`.
- Good: unknown event types are rejected during deserialization at `JsonRedisEventSerializer.java:91-95`.
- Gap: deserialize does not call `EventContractValidator.validateEventNameOrThrow`, so a custom registered event type can bypass event-name syntax validation.
- Gap: `serialize` can be used directly without publish-path validation.

Duplicate subscriber prevention:

- Duplicate handler registration by event type is prevented in the dispatcher.
- Duplicate channel subscription prevention is not owned by common Redis. Service configs add listener subscriptions directly.

Unknown event handling:

- Deserializer unknown event type: fails with `RedisPubSubException`.
- Dispatcher unknown handler: warns and drops.
- This matches realtime fanout better than Kafka, but should still report through `RedisPubSubObserver`.

Logging hooks:

- `RedisPubSubObserver` covers publish, receive, error, and deserialize error at `RedisPubSubObserver.java:12-38`.
- `Slf4jRedisPubSubLogger` logs structured fields and payload length only at `Slf4jRedisPubSubLogger.java:17-61`.
- Good: raw payload content is not logged.
- Gap: no metrics/tracing/MDC and no hook for unknown handler.

Realtime semantics correctness:

- Channel names are realtime-scoped.
- Listener catches deserialization/dispatch errors and returns, which is consistent with best-effort PubSub.
- Redis should not be used for durable cross-service events. The common module does not enforce this because it accepts any envelope event type.

Test coverage:

- Redis tests cover serializer roundtrip, metadata preservation, payload-bearing/payload-less contract behavior, registry behavior, routing context mapping, and publisher validation.
- Missing Redis tests are listed in section 12.

## 6. Common Event Review

`EventEnvelope` design:

- `EventEnvelope<T>` is a record containing `metadata` and `payload` at `EventEnvelope.java:37-40`.
- Constructor rejects null metadata at `EventEnvelope.java:48-52`.
- Good: minimal and transport-neutral.
- Gap: no schema version, no envelope version, and no extension metadata map.

Metadata model:

- `EventMetadata` contains `eventId`, `eventType`, `sourceService`, `createdAt`, and `correlationId` at `EventMetadata.java:26-30`.
- Constructor validates required presence at `EventMetadata.java:48-62`.
- Good: event identity and correlation are first-class.
- Gaps: no `schemaVersion`, `traceId`, `spanId`, `causationId`, `tenantId`, or event name pattern validation in the constructor.

Event typing:

- Event type enums live under `com.example.common.integration.*`.
- `SharedEventCatalog` maps event type strings to payload classes at `SharedEventCatalog.java:71-116`.
- Good: payload validation is centralized.
- Gap: no common `EventType` interface implemented by all enums, so catalog completeness tests must enumerate each enum manually.

Versioning support:

- No event or schema version exists in `EventMetadata` or `EventEnvelope`.
- Backward compatibility depends on `@JsonIgnoreProperties(ignoreUnknown = true)` on payloads.

Trace/correlation IDs:

- `correlationId` exists.
- No explicit trace/span/causation model exists.

Timestamp handling:

- `createdAt` is an `Instant`.
- Redis deserializer manually parses `createdAt` via `Instant.parse` at `JsonRedisEventSerializer.java:113-118`.
- Good: timestamp type is transport-neutral and UTC-friendly.

Transport neutrality:

- `common-events` has no Kafka/Redis/WebSocket dependencies.
- It is transport-neutral by build and imports.

Duplicated contracts:

- `EventPayloadRegistry` is transport-neutral.
- `RedisEventRegistry` duplicates it as a Redis-scoped adapter.
- `Event` is a thin marker-like interface implemented only by `EventEnvelope`.

Stale/deprecated code:

- No production `TODO`, `FIXME`, `@Deprecated`, or obvious demo/staging markers were found in the scoped common modules.
- Some constants and compatibility adapters appear unused or staging-like; see section 8.

## 7. Kafka vs Redis Boundary

Boundary validation:

- Kafka and Redis modules are separated at build level.
- Kafka does not import Redis.
- Redis does not import Kafka.
- `common-events` imports neither.
- Kafka topic constants are transport routes.
- Redis channel constants are realtime channel routes.

Kafka as durable broker/event stream:

- The Kafka module API is suitable for sending durable events, but it is not hardened for durable consumption yet because it lacks common serde, `ErrorHandlingDeserializer`, retry, DLQ, headers, idempotency, and unknown-event failure policy.

Redis PubSub as realtime fanout only:

- Redis channel names are correctly realtime-prefixed.
- Listener behavior is best-effort and drop-on-failure, which matches PubSub.
- The module name/package should say PubSub explicitly to avoid confusion with Redis cache/state modules.

Semantic overlap:

- Both Kafka and Redis publishers accept any `EventEnvelope<?>`.
- `SharedEventCatalog` includes durable-looking events and realtime-looking presence/chat fanout events together at `SharedEventCatalog.java:74-114`.
- There is no transport-neutral delivery semantics classification such as `DURABLE_EVENT` vs `REALTIME_SIGNAL`.
- Recommended fix: keep `common-events` transport-neutral but add explicit event semantics metadata or transport-specific route policy in Kafka/Redis modules so Kafka cannot accidentally be used for realtime-only fanout and Redis cannot accidentally be used for durable cross-service events.

Naming confusion:

- Kafka uses `KafkaEventPublisher` and `logPublish`.
- Redis uses `RedisEventPublisher`, but subscriber-side names are mixed: `subscriber` package, `RedisEventHandler`, `RedisEventListener`, and `RedisEventDispatcher`.
- `common-redis` is too broad beside `common-redis-cache`.

## 8. Duplicate / Deprecated / Unused Code

Candidate: `common-kafka/src/main/java/com/example/common/kafka/topic/KafkaTopics.java`, class `KafkaTopics`

- Severity: High when freezing public API.
- Why removable or fixable: constants `TOPIC_FRIENDSHIP_EVENTS`, `TOPIC_FRIENDSHIP_REQUEST_EVENTS`, `TOPIC_SYSTEM_DEAD_LETTER`, and `TOPIC_SYSTEM_RETRY` are defined at `KafkaTopics.java:24-29`, but usage searches did not find callers using the `TOPIC_` names. Services instead reference older names like `KafkaTopics.FRIENDSHIP_EVENTS`, `KafkaTopics.CHAT_MESSAGE_SENT`, and `KafkaTopics.ACCOUNT_CREATED`.
- References found: external service references include `auth-service/.../AccountCreatedEventProducer.java:25`, `chat-service/.../KafkaChatMessageEventPublisher.java:70`, `notification-service/.../MessageCreatedEventConsumer.java:33`, and others.
- Recommended fix: either restore compatibility aliases during migration or rename all service references to the current constants. Remove unused retry/DLQ constants until they are wired to a real retry/DLQ strategy, or wire them now.

Candidate: `common-redis/src/main/java/com/example/common/redis/registry/RedisEventRegistry.java`, interface `RedisEventRegistry`, and `DefaultRedisEventRegistry.java`, class `DefaultRedisEventRegistry`

- Severity: Medium.
- Why removable: `RedisEventRegistry` adds no behavior beyond `EventPayloadRegistry` at `RedisEventRegistry.java:16-17`; `DefaultRedisEventRegistry` only extends `DefaultEventPayloadRegistry` at `DefaultRedisEventRegistry.java:13`.
- References found: `RedisAutoConfiguration.java:37-49`, `JsonRedisEventSerializer.java:40`, Redis tests, and service registry configs.
- Recommended fix: bind a named/qualified `EventPayloadRegistry` bean for Redis PubSub. Keep `RedisEventRegistry` only as a temporary compatibility alias if service injection requires it.

Candidate: `common-events/src/main/java/com/example/common/event/Event.java`, interface `Event`

- Severity: Low.
- Why removable: it only exposes `metadata()` and `payload()` at `Event.java:13-33`; `EventEnvelope` is the only production implementation at `EventEnvelope.java:40`.
- References found: `EventEnvelope` and `SharedEventModelContractTest`.
- Recommended fix: keep only if a broader event polymorphism plan exists. Otherwise remove the interface after public API migration and use `EventEnvelope` directly.

Candidate: `common-redis/src/main/java/com/example/common/redis/config/RedisAutoConfiguration.java`, method `redisEventListener`

- Severity: Medium.
- Why removable or incomplete: auto-config creates a bare `MessageListener` bean at `RedisAutoConfiguration.java:70-77`, but common Redis does not register it with a `RedisMessageListenerContainer` or define subscriptions. Service configs create/register listeners directly, for example chat config registers a new `RedisEventListener` with `PatternTopic` in `ChatRedisListenerConfig.java:38-40`.
- References found: service listener configs use common listener manually.
- Recommended fix: either remove the auto-configured bare listener and document service-owned subscription registration, or add a common subscription registrar/container strategy.

Candidate: `KafkaEventRoutingContext` and `RedisEventRoutingContext`

- Severity: Low.
- Why partially removable: both extract eventType, correlationId, and sourceService from an envelope. Kafka adds topic/key; Redis adds channel.
- References found: Kafka/Redis loggers and tests.
- Recommended fix: keep transport-specific records but factor shared metadata extraction into a small helper if duplication grows. Do not move dispatch logic into `common-events`.

Candidate: duplicate registry tests in `KafkaContractTest`

- Severity: Low.
- Why removable: `KafkaContractTest.java:213-275` retests shared catalog and `DefaultEventPayloadRegistry` behavior already owned by `common-events` contract tests.
- References found: test-only.
- Recommended fix: keep Kafka tests focused on Kafka behavior; move registry coverage entirely to `common-events`.

No explicit deprecated annotations or production TODO/FIXME markers were found in the scoped common modules.

## 9. Freeze Blockers

### High

Issue H1: Kafka public API uses publisher terminology.

- Severity: High.
- Exact file/class: `common-kafka/.../producer/KafkaEventPublisher.java`, class `KafkaEventPublisher`; `DefaultKafkaEventPublisher.java`, class `DefaultKafkaEventPublisher`; `KafkaEventObserver.java`, interface `KafkaEventObserver`.
- Why problematic: architecture rule says Kafka must use producer/consumer terminology. The public API is `KafkaEventPublisher.publish(...)` at `KafkaEventPublisher.java:10-11`, implemented by `DefaultKafkaEventPublisher` at `DefaultKafkaEventPublisher.java:23-29`; observer method is `logPublish` at `KafkaEventObserver.java:16`.
- Exact recommended fix: rename to `KafkaEventProducer`, `DefaultKafkaEventProducer`, and `produce` or `send`. Rename observer methods to `logProduceSuccess` / `logProduceError`, or keep compatibility adapters temporarily and deprecate them after services migrate.

Issue H2: Kafka lacks common serializer/deserializer and `ErrorHandlingDeserializer`.

- Severity: High.
- Exact file/class: `common-kafka/.../config/KafkaAutoConfiguration.java`, class `KafkaAutoConfiguration`.
- Why problematic: auto-config only creates observer, producer, and dispatcher beans at `KafkaAutoConfiguration.java:21-43`. There is no envelope-aware serializer/deserializer, consumer factory, trusted package policy, or `ErrorHandlingDeserializer`. Durable consumers will deserialize inconsistently service by service.
- Exact recommended fix: add `serialization` package with envelope JSON serde, configure Spring Kafka producer/consumer factories or documented customizers, and provide `ErrorHandlingDeserializer` defaults for value deserialization.

Issue H3: Kafka retry/DLQ strategy is not implemented.

- Severity: High.
- Exact file/class: `common-kafka/.../topic/KafkaTopics.java`, class `KafkaTopics`; `KafkaAutoConfiguration.java`, class `KafkaAutoConfiguration`.
- Why problematic: `TOPIC_SYSTEM_DEAD_LETTER` and `TOPIC_SYSTEM_RETRY` exist at `KafkaTopics.java:28-29`, but no `DefaultErrorHandler`, backoff policy, `DeadLetterPublishingRecoverer`, retry classifier, or DLQ routing exists in common Kafka.
- Exact recommended fix: add common retry/DLQ configuration with backoff, exception classification, DLQ topic resolver, DLQ headers, and tests. Remove the constants if strategy remains service-owned.

Issue H4: Kafka dispatcher silently drops unknown event types.

- Severity: High.
- Exact file/class: `common-kafka/.../consumer/KafkaEventDispatcher.java`, class `KafkaEventDispatcher`.
- Why problematic: unknown events log and return at `KafkaEventDispatcher.java:68-70`. In Kafka, this can cause durable records to be acknowledged and lost if a service listener delegates to the dispatcher.
- Exact recommended fix: make Kafka unknown handler behavior configurable with default `FAIL`. Throw a `KafkaMessagingException` or `UnknownKafkaEventTypeException` so the listener error handler can retry/DLQ. Only allow drop/ignore with explicit opt-in.

Issue H5: Current common API is not consumer-compatible.

- Severity: High.
- Exact file/class/package: `common-kafka/.../topic/KafkaTopics.java`, class `KafkaTopics`; missing package `com.example.common.integration.kafka.event`; missing package `com.example.common.redis.message`; missing type `RedisEventSubscriber`; missing package `com.example.common.kafka.api`.
- Why problematic: dependency-direction search found services referencing absent constants and types, for example `KafkaTopics.ACCOUNT_CREATED`, `KafkaTopics.CHAT_MESSAGE_SENT`, `com.example.common.integration.kafka.event.ChatMessageSentEvent`, `com.example.common.redis.message.RedisMessage`, and `RedisEventSubscriber`. This means the current common modules can pass their own tests but still fail service compilation or migration.
- Exact recommended fix: either restore compatibility wrappers/aliases for one release or migrate all service references to `EventEnvelope`, payload classes, `KafkaEventProducer`, and `RedisEventSubscriber` before freeze. Add consumer-compatibility compile checks.

### Medium

Issue M1: Module/package naming is not freeze-normalized.

- Severity: Medium.
- Exact file/class: `settings.gradle`; `common-redis/build.gradle`; packages under `com.example.common.redis`.
- Why problematic: actual modules are `common-events` and `common-redis` at `settings.gradle:8-9`, while the architecture names are `common-event` and `common-redis-pubsub`. `common-redis` is ambiguous beside `common-redis-cache` at `settings.gradle:11`.
- Exact recommended fix: choose final names before freeze. Preferred: module `common-event`, module `common-redis-pubsub`, package `com.example.common.redis.pubsub`. If not renaming modules, at least rename packages/descriptions to PubSub.

Issue M2: Event metadata lacks versioning and richer tracing.

- Severity: Medium.
- Exact file/class: `common-events/.../event/EventMetadata.java`, class `EventMetadata`; `EventEnvelope.java`, record `EventEnvelope`.
- Why problematic: metadata has only eventId/eventType/sourceService/createdAt/correlationId at `EventMetadata.java:26-30`. There is no schema version, envelope version, causationId, traceId/spanId, or extension attributes. Versioning after freeze becomes breaking.
- Exact recommended fix: add `schemaVersion` or `eventVersion` and optional `traceId`, `spanId`, `causationId`, and `attributes` before freeze, with backward-compatible JSON defaults.

Issue M3: Event name syntax is not consistently enforced.

- Severity: Medium.
- Exact file/class: `EventMetadata.java`, class `EventMetadata`; `JsonRedisEventSerializer.java`, class `JsonRedisEventSerializer`; `EventContractValidator.java`, class `EventContractValidator`.
- Why problematic: `EventMetadata` validates presence but not pattern at `EventMetadata.java:48-62`; Redis deserialize extracts eventType at `JsonRedisEventSerializer.java:72-76` but does not call `EventContractValidator.validateEventNameOrThrow`.
- Exact recommended fix: validate eventType pattern in `EventMetadata` or make all deserializers call the validator. Add tests for invalid eventType during Redis deserialization and envelope construction.

Issue M4: Shared catalog mixes durable and realtime event semantics.

- Severity: Medium.
- Exact file/class: `common-events/.../event/SharedEventCatalog.java`, class `SharedEventCatalog`.
- Why problematic: durable-looking account/friendship/notification events and realtime-looking presence/chat fanout events are all listed together at `SharedEventCatalog.java:74-114`. Kafka and Redis publishers accept any envelope, so the boundary is documented but not enforceable.
- Exact recommended fix: add transport-neutral event semantics classification or transport-specific route policies. For example, Kafka route policy allows `DURABLE` events and Redis route policy allows `REALTIME_FANOUT` events.

Issue M5: Kafka metadata is not propagated as Kafka headers.

- Severity: Medium.
- Exact file/class: `common-kafka/.../producer/DefaultKafkaEventPublisher.java`, class `DefaultKafkaEventPublisher`.
- Why problematic: `kafkaTemplate.send(topic, key, envelope)` at `DefaultKafkaEventPublisher.java:49` writes no headers. DLQ, retries, tracing, and filtering lose cheap access to event metadata.
- Exact recommended fix: send a `ProducerRecord` or Spring `Message` with headers for eventId, eventType, correlationId, sourceService, createdAt, schemaVersion, and content type.

Issue M6: Kafka idempotency and ordering support is incomplete.

- Severity: Medium.
- Exact file/class: `EventMetadata.java`, `DefaultKafkaEventPublisher.java`, `KafkaEventRoutingContext.java`.
- Why problematic: eventId exists, but there is no idempotency key contract, duplicate-consumption helper, processed-event store abstraction, or aggregate key resolver. Ordering depends entirely on callers passing correct keys.
- Exact recommended fix: add `KafkaEventKeyResolver` guidance/helpers, document partition-key rules per event type, and provide optional `ProcessedEventStore` / `IdempotencyGuard` interfaces for consumers.

Issue M7: Redis subscriber terminology and subscription design are inconsistent.

- Severity: Medium.
- Exact file/class: `common-redis/.../subscriber/RedisEventHandler.java`, class `RedisEventHandler`; `RedisAutoConfiguration.java`, class `RedisAutoConfiguration`.
- Why problematic: Redis rule requires publisher/subscriber terminology, but the main callback type is `RedisEventHandler` at `RedisEventHandler.java:11-20`. Auto-config creates a bare listener at `RedisAutoConfiguration.java:70-77` without registering channels or patterns.
- Exact recommended fix: introduce `RedisEventSubscriber<T>` with `onMessage(EventEnvelope<T>)`, rename dispatcher map to subscriber map, and either own `RedisMessageListenerContainer` subscription registration or document service-owned registration and remove the auto listener bean.

Issue M8: Redis unknown handler and error observability are incomplete.

- Severity: Medium.
- Exact file/class: `RedisEventDispatcher.java`, class `RedisEventDispatcher`; `RedisEventListener.java`, class `RedisEventListener`; `RedisPubSubObserver.java`, interface `RedisPubSubObserver`.
- Why problematic: dispatcher unknown handler only logs with class logger at `RedisEventDispatcher.java:39-41`, and listener catches dispatch errors and logs at `RedisEventListener.java:36-41`. There is no observer method for unknown handler/drop.
- Exact recommended fix: add `logUnknownEvent` / `logDropped` hooks and counters. Keep drop-on-unknown for realtime, but make it observable.

Issue M9: Registry validation gaps and duplicate registry abstraction.

- Severity: Medium.
- Exact file/class: `DefaultEventPayloadRegistry.java`, class `DefaultEventPayloadRegistry`; `RedisEventRegistry.java`; `DefaultRedisEventRegistry.java`.
- Why problematic: registry `register` does not validate blank eventType or null payloadClass at `DefaultEventPayloadRegistry.java:16-18`; Redis-specific registry adds no behavior at `RedisEventRegistry.java:16-17`.
- Exact recommended fix: validate eventType and payloadClass in `DefaultEventPayloadRegistry`, reuse `EventContractValidator`, and replace Redis-specific registry with a named `EventPayloadRegistry` bean or keep it only as temporary compatibility facade.

Issue M10: Observability is logs-only and naming is inconsistent.

- Severity: Medium.
- Exact file/class: `KafkaEventObserver.java`, `Slf4jKafkaEventLogger.java`, `RedisPubSubObserver.java`, `Slf4jRedisPubSubLogger.java`.
- Why problematic: structured logs exist, but no metrics, no tracing, no MDC, no Kafka partition/offset, no Redis subscriber count, and Kafka observer still uses publish naming.
- Exact recommended fix: add Micrometer counters/timers and tracing hooks. Normalize method names per transport terminology. Add metadata fields from headers where available.

Issue M11: Deep application payload schemas live in common-events.

- Severity: Medium.
- Exact file/class: `ChatMessagePayload.java`, class `ChatMessagePayload`; `SharedEventCatalog.java`, class `SharedEventCatalog`.
- Why problematic: `ChatMessagePayload` carries detailed chat message fields at `ChatMessagePayload.java:19-57`, and `SharedEventCatalog` imports multiple account/chat/friendship/notification/presence payloads at `SharedEventCatalog.java:3-28`. This is acceptable for a shared integration-contract module, but it is business/domain leakage if common modules are meant to stay infrastructure-only.
- Exact recommended fix: explicitly document `common-events` as shared integration contracts, or move domain-specific contracts into per-domain contract modules such as `chat-event-contracts`, `presence-event-contracts`, etc.

### Low

Issue L1: `Event` interface is low-value duplicate abstraction.

- Severity: Low.
- Exact file/class: `common-events/.../event/Event.java`, interface `Event`.
- Why problematic: it duplicates `EventEnvelope` accessors and has only one production implementation.
- Exact recommended fix: remove after migration or document future polymorphic use.

Issue L2: Kafka and Redis routing context extraction is duplicated.

- Severity: Low.
- Exact file/class: `KafkaEventRoutingContext.java`, record `KafkaEventRoutingContext`; `RedisEventRoutingContext.java`, record `RedisEventRoutingContext`.
- Why problematic: both extract eventType/correlationId/sourceService from the envelope.
- Exact recommended fix: keep transport-specific records but factor common extraction if more transports appear.

Issue L3: Common-event registry behavior is tested again in Kafka tests.

- Severity: Low.
- Exact file/class: `KafkaContractTest.java`, class `KafkaContractTest`.
- Why problematic: `KafkaContractTest.java:213-275` duplicates registry/catalog tests owned by `common-events`.
- Exact recommended fix: keep registry tests in `common-events`; keep Kafka tests focused on Kafka producer/consumer behavior.

Issue L4: Redis publisher ignores subscriber count.

- Severity: Low.
- Exact file/class: `DefaultRedisEventPublisher.java`, class `DefaultRedisEventPublisher`.
- Why problematic: `redisTemplate.convertAndSend(channel, payload)` at `DefaultRedisEventPublisher.java:43` returns subscriber count, but it is ignored and success is logged.
- Exact recommended fix: capture return value, add it to observer/logs/metrics, and optionally warn on zero subscribers if useful.

## 10. Safe Refactor Plan

Phase 1: Cleanup and compatibility inventory

- Decide whether to preserve or remove legacy APIs referenced by services.
- Add temporary compatibility aliases for missing `KafkaTopics` constants if services cannot migrate immediately.
- Remove or wire unused `TOPIC_SYSTEM_RETRY` and `TOPIC_SYSTEM_DEAD_LETTER`.
- Move duplicate registry tests out of Kafka tests.

Phase 2: Naming consistency

- Rename Kafka API from publisher to producer terminology.
- Introduce `KafkaEventProducer` while keeping `KafkaEventPublisher` as deprecated compatibility facade if needed.
- Rename Redis callback type to `RedisEventSubscriber`.
- Decide module/package final names: `common-event` and `common-redis-pubsub` preferred, or package-level `com.example.common.redis.pubsub` if module rename is too disruptive.

Phase 3: Package normalization

- Move Redis PubSub classes under `com.example.common.redis.pubsub.*`.
- Keep cache-related Redis APIs in `common-redis-cache` only.
- Group common event contracts under `com.example.common.event.envelope`, `catalog`, `validation`, and `payload.<domain>` if package churn is acceptable.

Phase 4: Envelope standardization

- Add event/schema version fields before freeze.
- Add optional trace/causation fields or an extension metadata map.
- Validate eventType syntax uniformly at construction and deserialization.
- Add a common `EventType` interface or test helper so catalog completeness does not require manual enum enumeration.

Phase 5: Kafka hardening

- Add envelope JSON serializer/deserializer.
- Configure `ErrorHandlingDeserializer`.
- Add common `DefaultErrorHandler`, backoff policy, DLQ recoverer, and DLQ header policy.
- Add metadata headers on produce.
- Make unknown event dispatch fail by default.
- Add key resolver/idempotency extension points.

Phase 6: Redis hardening

- Finalize `RedisEventSubscriber` and dispatcher API.
- Add common listener subscription registrar or remove bare listener auto-configuration.
- Add observer hooks for unknown handler/drop and subscriber count.
- Validate event names during deserialization.
- Add duplicate channel subscription tests if common owns subscriptions.

Phase 7: Freeze validation

- Run scoped common tests.
- Run compile checks for services that consume common modules.
- Add serialization compatibility tests with known event JSON fixtures.
- Add retry/DLQ integration tests or slice tests.
- Produce a freeze checklist with API names, public packages, and migration aliases.

## 11. Recommended Final Structure

Recommended `common-event`:

```text
common-event
  src/main/java/com/example/common/event
    envelope/
      EventEnvelope.java
      EventMetadata.java
      EventVersion.java
    catalog/
      SharedEventCatalog.java
      EventPayloadRegistry.java
      DefaultEventPayloadRegistry.java
      EventDeliverySemantics.java
    validation/
      EventContractValidator.java
    payload/
      account/
      chat/
      friendship/
      notification/
      presence/
      user/
    type/
      EventType.java
      AccountEventType.java
      ChatEventType.java
      ...
```

Recommended `common-kafka`:

```text
common-kafka
  src/main/java/com/example/common/kafka
    config/
      KafkaAutoConfiguration.java
      KafkaProducerAutoConfiguration.java
      KafkaConsumerAutoConfiguration.java
      KafkaRetryDlqAutoConfiguration.java
    producer/
      KafkaEventProducer.java
      DefaultKafkaEventProducer.java
      KafkaProduceResult.java
      KafkaEventKeyResolver.java
    consumer/
      KafkaEventConsumer.java
      KafkaEventHandler.java
      KafkaEventDispatcher.java
      UnknownKafkaEventPolicy.java
    serialization/
      EventEnvelopeKafkaSerializer.java
      EventEnvelopeKafkaDeserializer.java
      KafkaDeserializerErrorHandler.java
    topic/
      KafkaTopics.java
      KafkaTopicResolver.java
    retry/
      KafkaRetryProperties.java
      KafkaDlqPublisher.java
      KafkaDeadLetterHeaders.java
    observability/
      KafkaEventObserver.java
      Slf4jKafkaEventLogger.java
      MicrometerKafkaEventObserver.java
    idempotency/
      KafkaEventIdempotencyGuard.java
```

Recommended `common-redis-pubsub`:

```text
common-redis-pubsub
  src/main/java/com/example/common/redis/pubsub
    config/
      RedisPubSubAutoConfiguration.java
      RedisPubSubListenerAutoConfiguration.java
    publisher/
      RedisEventPublisher.java
      DefaultRedisEventPublisher.java
    subscriber/
      RedisEventSubscriber.java
      RedisEventDispatcher.java
      UnknownRedisEventPolicy.java
    listener/
      RedisPubSubMessageListener.java
      RedisPubSubSubscriptionRegistrar.java
    channel/
      RedisPubSubChannels.java
      RedisChannelResolver.java
    registry/
      RedisPubSubPayloadRegistry.java
    serialization/
      RedisEventSerializer.java
      JsonRedisEventSerializer.java
    observability/
      RedisPubSubObserver.java
      Slf4jRedisPubSubLogger.java
      MicrometerRedisPubSubObserver.java
```

## 12. Required Tests Before Freeze

Common event unit tests:

- `EventMetadata` rejects invalid eventType pattern if validation is moved into constructor.
- `EventEnvelope` roundtrips with version fields.
- `SharedEventCatalog` classifies durable vs realtime semantics if added.
- All event type enums implement a common interface or are discovered by catalog completeness tests.

Serializer tests:

- Kafka serializer/deserializer roundtrip for `EventEnvelope<ChatMessagePayload>`.
- Kafka deserializer uses payload registry and rejects unknown event types correctly.
- Kafka `ErrorHandlingDeserializer` path produces recoverable errors.
- Redis serializer rejects invalid eventType pattern on deserialize.
- Redis serializer preserves payload-less null payloads and rejects unexpected payloads.
- JSON fixtures remain backward compatible across version additions.

Dispatcher tests:

- Kafka unknown event throws by default and routes to configured policy.
- Kafka duplicate handler registration still fails.
- Kafka observer sees dispatch success/failure with topic, partition, offset where available.
- Redis unknown handler logs/drops through observer.
- Redis duplicate subscriber eventType registration fails.
- Redis handler exception is logged and does not crash listener thread.

Retry tests:

- Kafka retryable exception retries with configured backoff.
- Kafka non-retryable exception goes directly to DLQ.
- Kafka deserialization error goes to DLQ or configured error path.

DLQ tests:

- DLQ record includes original topic, partition, offset, exception class/message, eventId, eventType, and correlationId.
- DLQ topic resolver is deterministic.
- DLQ publish failure is observable.

Duplicate registration tests:

- `DefaultEventPayloadRegistry.register(null, ...)` and blank eventType fail clearly.
- `DefaultEventPayloadRegistry.register(eventType, null)` fails clearly.
- Redis subscriber duplicate eventType registration fails.
- Duplicate channel/pattern subscription prevention if common owns subscriptions.

Unknown event tests:

- Redis unknown event type during deserialization is logged and dropped by listener.
- Redis known eventType with no subscriber is observable.
- Kafka known eventType with no consumer handler follows configured unknown handler policy.

Routing tests:

- Kafka topic resolver maps event type to durable topic.
- Kafka producer writes metadata headers.
- Kafka key resolver uses stable aggregate keys.
- Redis channel resolver maps chat room/user/presence events to realtime channels only.
- Redis does not accept durable-only event types if route policy is added.

## 13. Final Verdict

Freeze now: no.

Minimum required changes before freeze:

- Rename or compatibility-wrap Kafka publisher terminology to producer terminology.
- Add Kafka serializer/deserializer and `ErrorHandlingDeserializer` configuration.
- Add or explicitly service-own Kafka retry/DLQ; do not freeze with unused DLQ/retry constants pretending strategy exists.
- Change Kafka unknown-event dispatch to fail by default.
- Resolve public API drift with current service consumers.
- Normalize Redis PubSub naming and subscriber contract.
- Add event versioning before contracts become hard to change.
- Add missing serializer, dispatcher, unknown-event, retry, DLQ, and routing tests.

Optional later improvements:

- Add Micrometer observers and tracing/MDC integration.
- Add idempotency store implementations.
- Split deep domain payload contracts into per-domain contract modules if `common-events` should stay infrastructure-only.
- Factor shared routing-context metadata extraction if more transports are added.

The base architecture direction is right: `common-events` is transport-neutral, Kafka and Redis boundaries are separated, and Redis channel naming points to realtime fanout. The freeze risk is not the foundation. The risk is that the public API and operational hardening are not yet stable enough for durable Kafka semantics or a long-lived common contract.
