# Common Redis / Kafka / Event Review - Pass 2

## 1. Executive Summary

- Current overall quality: the common Redis/Kafka/Event architecture is not coherent end to end. The scoped common modules compile by themselves, but connected service flows do not compile against the current common contracts.
- The design is fragmented, not merely untidy. `common-events` now exposes an `EventEnvelope<T>` model; `common-kafka` mostly points at that model; `common-redis` also exposes that model; but service Kafka and Redis flows still reference deleted wrapper/message contracts.
- Biggest structural problems:
  - Kafka wrapper contracts under `com.example.common.integration.kafka.event.*` are gone, while services still import them.
  - Redis message contracts under `com.example.common.redis.message.RedisMessage` and `com.example.common.redis.api.IRedisMessage` are gone, while services still publish/subscribe with them.
  - `RedisEventSubscriber<T>` now requires `EventEnvelope<T>`, but service subscribers implement `onMessage(RedisMessage<T>)`.
  - Event names are duplicated and contradictory across `EventType`, domain-specific enums, and `KafkaTopics`.
  - Contract packages such as `com.example.common.integration.contract` and `com.example.common.integration.realtime` were removed before their service/test imports were removed.
- Previous cleanup is not enough. Some cleanup went in the right direction, especially moving toward `EventEnvelope<T>`, but compatibility was removed too early. The architecture is currently between two designs and should not be accepted for long-term growth.
- Validation performed:
  - `./gradlew :common:common-events:compileJava :common:common-kafka:compileJava :common:common-redis:compileJava :common:common-redis-cache:compileJava` passed.
  - `./gradlew :chat-service:compileJava` failed on missing `com.example.common.integration.kafka.event.*` and missing `com.example.common.redis.message.RedisMessage`.
  - `./gradlew :auth-service:compileJava :user-service:compileJava :friendship-service:compileJava` failed first at `auth-service` on missing `com.example.common.integration.kafka.event.AccountCreatedEvent`.

## 2. Current Dependency Map

- `common-events`
  - Gradle dependencies: Jackson, Lombok.
  - Does not depend on `common-kafka`, `common-redis`, or `common-redis-cache`.
  - Correct direction: yes. This module is the contract/base module.

- `common-kafka`
  - Gradle dependency: `implementation project(':common:common-events')`.
  - Code imports `EventEnvelope`, `EventMetadata`, and `EventContractValidator`.
  - Correct direction: yes for transport depending on event model.
  - Violation: it owns `KafkaTopics`, which currently mixes transport route names with event-name-like constants.
  - Broken connected dependency: services depend on `common-kafka` but still import deleted `com.example.common.integration.kafka.event.*` classes.

- `common-redis`
  - Gradle dependency: `implementation project(':common:common-events')`.
  - Code imports `EventEnvelope`, `EventMetadata`, and `EventContractValidator`.
  - Correct direction: yes for Redis pub/sub depending on event model.
  - Violation: the module is effectively Redis Pub/Sub but its package root is generic `com.example.common.redis.*`, which collides conceptually with `common-redis-cache`.
  - Broken connected dependency: services depend on `common-redis` but still import deleted `com.example.common.redis.message.RedisMessage` and `com.example.common.redis.api.IRedisMessage`.

- `common-redis-cache`
  - Gradle dependency: Spring Redis/Jackson/Lombok only. It no longer depends on `common-events`.
  - Correct direction: yes. Cache is independent from event contracts.
  - Violation: all classes live under `com.example.common.redis.*`, the same root as Redis Pub/Sub. The module split is invisible from imports.

- `common-websocket`
  - Gradle dependency: `implementation project(':common:common-events')`.
  - Connected contract: `com.example.common.websocket.protocol.RealtimeWsEvent`.
  - Correct direction: partly. WebSocket can depend on shared event payloads, but its wire DTO should not become a Redis Pub/Sub message model.

- `common-web`
  - Connected package: `com.example.common.realtime.policy.*`.
  - Violation: `RealtimeFlowId`, `RealtimeFlowType`, and `RealtimeFlowClassificationPolicy` are realtime/event delivery policy, not web infrastructure.

- Deleted or missing contract packages still referenced:
  - `com.example.common.integration.kafka.event.*`
  - `com.example.common.redis.message.*`
  - `com.example.common.redis.api.IRedisMessage`
  - `com.example.common.integration.contract.*`
  - `com.example.common.integration.realtime.*`
  - These are hard violations because connected service/test code still imports them.

## 3. End-to-End Flow Review

- Domain event creation:
  - Services create domain payloads from local aggregates, for example `AccountCreatedPayload`, `ChatMessagePayload`, `ReactionPayload`, `FriendshipPayload`, `FriendRequestEvent`, `NotificationRequestedPayload`, and presence payloads.
  - This responsibility should stay in services. Common modules should not know how to read service entities such as `Account`, `Friendship`, `ChatMessage`, or `Notification`.

- Event contract ownership:
  - Payload DTOs live in `common-events/src/main/java/com/example/common/integration/<domain>`.
  - The generic envelope lives in `common-events/src/main/java/com/example/common/event/EventEnvelope.java` and `EventMetadata.java`.
  - This is the right target ownership.
  - Current violation: event-name ownership is split between `EventType`, domain enums such as `ChatEventType`, `PresenceEventType`, `FriendshipEventType`, and transport constants in `KafkaTopics`.

- Kafka publishing:
  - Current service code tries to create deleted wrapper classes such as `AccountCreatedEvent`, `ChatMessageSentEvent`, `ChatReactionUpdatedEvent`, `FriendshipEvent`, `FriendRequestKafkaEvent`, and `NotificationRequestedEvent`.
  - `KafkaEventProducer.publish(String topic, String key, EventEnvelope<?> envelope)` only accepts `EventEnvelope<?>`.
  - Actual flow is broken: the wrapper package does not exist and the publisher no longer supports that old wrapper model.
  - Target responsibility: services should build `EventEnvelope<Payload>` directly or through a small service-local/common `EventEnvelopeFactory`; `common-kafka` should only route the envelope to Kafka.

- Kafka consumption:
  - Current consumers use direct `@KafkaListener` methods with deleted wrapper parameter types, for example `AccountCreatedConsumer.listen(AccountCreatedEvent)` and `KafkaChatMessageEventConsumer.onMessageSent(ChatMessageSentEvent)`.
  - `KafkaEventHandler`, `KafkaEventDispatcher`, and `KafkaEventConsumer` exist in `common-kafka`, but connected services are not using them.
  - Target responsibility: `common-kafka` should provide one inbound adapter standard: either `@KafkaListener EventEnvelope<?> -> KafkaEventDispatcher`, or typed `@KafkaListener EventEnvelope<Payload>` per topic. Avoid Kafka-specific wrapper DTOs.

- Redis publishing:
  - Current service code builds deleted `RedisMessage<T>` objects, for example in `ChatRedisPublisher`, `RedisMessageFactory`, and `PresenceRedisPublisher`.
  - `RedisEventPublisher.publish(String channel, EventEnvelope<?> eventEnvelope)` only accepts `EventEnvelope<?>`.
  - Actual flow is broken: service publishers still use a deleted Redis-specific message model.
  - Target responsibility: services should publish `EventEnvelope<Payload>` to a Redis channel; `common-redis` should serialize and send it.

- Redis subscription:
  - `RedisEventListener` deserializes Redis JSON into `EventEnvelope<?>` and dispatches it.
  - `RedisEventSubscriber<T>` requires `onMessage(EventEnvelope<T> message)`.
  - Service subscribers still implement methods like `onMessage(RedisMessage<ChatMessagePayload> message)`.
  - Actual flow is broken at compile time and conceptually split between envelope and deleted Redis-specific message DTOs.
  - Target responsibility: Redis subscribers should be renamed or standardized as event handlers receiving `EventEnvelope<T>`; transport listener code should remain in `common-redis`.

- WebSocket or downstream delivery:
  - Chat and presence Redis subscribers convert incoming events into `RealtimeWsEvent`.
  - Notification Redis bypasses `common-redis` entirely: `RedisNotificationPublisher` serializes `RealtimeWsEvent` directly with `StringRedisTemplate`, and `RedisNotificationSubscriber` implements Spring `MessageListener` directly.
  - Boundary violation: `RealtimeWsEvent` is a WebSocket wire DTO. It should not be the common Redis Pub/Sub contract.
  - Target responsibility: Redis carries `EventEnvelope<T>`; WebSocket adapters translate envelope/payload into `RealtimeWsEvent` only at the edge.

## 4. Problems

### High

1. Missing Kafka wrapper package still used by services.
   - Exact files/classes/packages:
     - Deleted package: `com.example.common.integration.kafka.event`
     - Deleted old source package: `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/*`
     - Import sites: `chatappBE/auth-service/src/main/java/com/example/auth/kafka/AccountCreatedEventProducer.java`
     - `chatappBE/user-service/src/main/java/com/example/user/kafka/AccountCreatedConsumer.java`
     - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java`
     - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventConsumer.java`
     - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventPublisher.java`
     - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventConsumer.java`
     - `chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java`
     - `chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventConsumer.java`
     - `chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipRequestEventConsumer.java`
     - `chatappBE/notification-service/src/main/java/com/example/notification/kafka/NotificationEventProducer.java`
     - `chatappBE/notification-service/src/main/java/com/example/notification/kafka/*Consumer.java`
   - Why it is a problem: services compile against contracts that no longer exist.
   - Architectural impact: Kafka is neither old-wrapper-based nor fully envelope-based. It is a broken transition state.
   - Recommended fix: migrate all service Kafka publishers/listeners to `EventEnvelope<T>` and domain payload DTOs. Restore `com.example.common.integration.kafka.event.*` only as a short-lived compatibility bridge if an incremental migration is required.

2. Missing Redis message package still used by services.
   - Exact files/classes/packages:
     - Deleted package: `com.example.common.redis.message`
     - Deleted class: `RedisMessage`
     - Deleted class/interface: `com.example.common.redis.api.IRedisMessage`
     - Import sites: `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/RedisMessageFactory.java`
     - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatRedisPublisher.java`
     - `chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/*RedisSubscriber.java`
     - `chatappBE/presence-service/src/main/java/com/example/presence/redis/PresenceRedisPublisher.java`
     - `chatappBE/presence-service/src/main/java/com/example/presence/redis/*Subscriber.java`
     - `chatappBE/chat-service/src/test/java/com/example/chat/realtime/contract/RealtimeMessagingAlignmentTest.java`
     - `chatappBE/chat-service/src/test/java/com/example/chat/realtime/contract/RealtimeContractValidatorTest.java`
   - Why it is a problem: service Redis Pub/Sub code cannot compile and still models Redis messages separately from shared events.
   - Architectural impact: Redis is not standardized on `EventEnvelope<T>` despite the common API requiring it.
   - Recommended fix: remove `RedisMessageFactory`, replace Redis publish calls with an envelope factory, and update every Redis subscriber to `onMessage(EventEnvelope<Payload>)`.

3. `RedisEventSubscriber<T>` signature is incompatible with service subscriber implementations.
   - Exact file/class: `chatappBE/common/common-redis/src/main/java/com/example/common/redis/subscriber/RedisEventSubscriber.java`
   - Exact service examples:
     - `ChatMessageSentRedisSubscriber implements RedisEventSubscriber<RedisMessage<ChatMessagePayload>>` but declares `onMessage(RedisMessage<ChatMessagePayload>)`.
     - `UserOnlineSubscriber implements RedisEventSubscriber<RedisMessage<PresenceUserOnlinePayload>>` but declares `onMessage(RedisMessage<PresenceUserOnlinePayload>)`.
   - Why it is a problem: the interface requires `void onMessage(EventEnvelope<T> message)`. Existing subscribers do not override it.
   - Architectural impact: common Redis inbound architecture is unusable by current services.
   - Recommended fix: choose `EventEnvelope<T>` as the subscriber parameter and migrate all service subscribers, or temporarily reintroduce the old Redis message interfaces until migration is complete.

4. Event names have multiple contradictory sources of truth.
   - Exact files/classes:
     - `chatappBE/common/common-events/src/main/java/com/example/common/event/EventType.java`
     - `chatappBE/common/common-events/src/main/java/com/example/common/integration/account/AccountEventType.java`
     - `chatappBE/common/common-events/src/main/java/com/example/common/integration/friendship/FriendshipEventType.java`
     - `chatappBE/common/common-events/src/main/java/com/example/common/integration/chat/ChatEventType.java`
     - `chatappBE/common/common-events/src/main/java/com/example/common/integration/presence/PresenceEventType.java`
     - `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/topic/KafkaTopics.java`
   - Why it is a problem:
     - `EventType.ACCOUNT_CREATED` is `account.created`, while `AccountEventType.ACCOUNT_CREATED` and `KafkaTopics.ACCOUNT_CREATED` are `account.account.created`.
     - `EventType.FRIENDSHIP_REQUEST_SENT` is `friendship.request.sent`, while `FriendshipEventType.FRIEND_REQUEST_SENT` is `friend.request.sent`.
     - `EventType` omits active values such as `chat.message.pinned`, `chat.message.unpinned`, `presence.room.join`, and `presence.room.leave`.
   - Architectural impact: validators, registries, topics, and handlers cannot reliably agree on event identity.
   - Recommended fix: delete the global `EventType` as the canonical source and standardize on domain event-name catalogs, or make `EventType` complete and delete domain enums. Prefer domain catalogs because services already use them.

5. Contract packages were removed before imports were removed.
   - Exact missing packages:
     - `com.example.common.integration.contract`
     - `com.example.common.integration.realtime`
   - Exact remaining imports:
     - `chatappBE/chat-service/src/test/java/com/example/chat/realtime/contract/RealtimeContractValidatorTest.java`
     - `chatappBE/chat-service/src/test/java/com/example/chat/realtime/contract/RealtimeContractBaselineTest.java`
     - `chatappBE/presence-service/src/test/java/com/example/presence/contract/PresenceRealtimeContractBaselineTest.java`
     - `chatappBE/notification-service/src/test/java/com/example/notification/contract/NotificationRealtimeContractBaselineTest.java`
   - Why it is a problem: tests still describe a contract API that the codebase no longer provides.
   - Architectural impact: the contract layer has no stable migration story.
   - Recommended fix: do not keep vague `integration.contract` long term. Temporarily restore only required bridge classes if needed, then move version/validator concerns to explicit `common.event.validation` or `common.realtime.contract` packages.

6. Notification Redis Pub/Sub bypasses the common Redis standard.
   - Exact files/classes:
     - `chatappBE/notification-service/src/main/java/com/example/notification/websocket/redis/RedisNotificationPublisher.java`
     - `chatappBE/notification-service/src/main/java/com/example/notification/websocket/redis/RedisNotificationSubscriber.java`
     - `chatappBE/notification-service/src/main/java/com/example/notification/configuration/NotificationRedisListenerConfig.java`
   - Why it is a problem: notification serializes `RealtimeWsEvent` directly with `StringRedisTemplate`, while chat/presence are supposed to use `RedisEventPublisher`, `RedisEventListener`, registry, dispatcher, and serializer.
   - Architectural impact: Redis Pub/Sub has two incompatible standards.
   - Recommended fix: migrate notification Redis fanout to `EventEnvelope<NotificationPayload>` and the common Redis listener/dispatcher, then translate to `RealtimeWsEvent` at WebSocket delivery.

### Medium

1. `common-events` is transport-agnostic in imports but not coherent in contracts.
   - Exact files/classes:
     - `EventEnvelope.java`
     - `EventMetadata.java`
     - `EventType.java`
     - `EventRegistry.java`
     - `EventContractValidator.java`
   - Why it is a problem: `EventRegistry` talks about Kafka/Redis implementations, but Kafka/Redis each define separate registries. `EventType` claims to be complete but is not.
   - Architectural impact: the shared model looks central but is not actually the single source of truth.
   - Recommended fix: keep `EventEnvelope` and `EventMetadata`; remove or replace `EventType` and `EventRegistry` with one real `EventName`/`EventPayloadRegistry` strategy.

2. Three registry abstractions overlap.
   - Exact files/classes:
     - `common-events/.../event/registry/EventRegistry.java`
     - `common-kafka/.../kafka/registry/KafkaEventRegistry.java`
     - `common-redis/.../redis/registry/RedisEventRegistry.java`
   - Why it is a problem: all map event type to payload class with inconsistent method names and behavior.
   - Architectural impact: payload resolution differs by transport even though the event model is shared.
   - Recommended fix: merge into one `EventPayloadRegistry` in `common-events`, or keep registries service-local and delete the common base.

3. `KafkaEventSerializer` and `KafkaEventDeserializer` are dead-end contracts.
   - Exact files/classes:
     - `common-kafka/.../serialization/KafkaEventSerializer.java`
     - `common-kafka/.../serialization/KafkaEventDeserializer.java`
   - Why it is a problem: there is no concrete JSON implementation and `DefaultKafkaEventProducer` does not use them.
   - Architectural impact: the API suggests a standard serialization layer that does not exist.
   - Recommended fix: either implement and wire `JsonKafkaEventSerializer`/`JsonKafkaEventDeserializer`, or delete the interfaces.

4. Kafka and Redis use different naming for equivalent roles.
   - Exact files/classes:
     - Kafka: `producer`, `consumer`, `handler`, `logging`, `error`, `topic`
     - Redis: `publisher`, `subscriber`, `listener`, `observability`, `exception`, `channel`
   - Why it is a problem: the same architecture is represented by different vocabulary.
   - Architectural impact: future additions will keep diverging.
   - Recommended fix: standardize on transport listener + event handler + event dispatcher + event publisher. Use `exception` and `observability` in both modules.

5. Auto-configuration is discoverable only because services broadly component-scan `com.example.common`.
   - Exact files/classes:
     - `common-kafka/.../config/KafkaAutoConfiguration.java`
     - `common-redis/.../config/RedisAutoConfiguration.java`
     - service apps with `@ComponentScan(basePackages = {"com.example.common", ...})`
   - Why it is a problem: these are common libraries but have no `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
   - Architectural impact: wiring works by broad scanning, not by module-level auto-configuration. This is fragile and can create accidental beans.
   - Recommended fix: add proper Spring Boot auto-configuration imports, or rename these to regular configuration and require explicit service imports.

6. Auto-configuration depends on concrete logger classes.
   - Exact files/classes:
     - `KafkaAutoConfiguration.kafkaEventProducer(..., KafkaEventLogger logger)`
     - `RedisAutoConfiguration.redisEventListener(..., RedisPubSubLogger logger)`
   - Why it is a problem: implementations already depend on interfaces (`IKafkaEventLogger`, `IRedisPubSubLogger`), but configuration narrows back to concrete classes.
   - Architectural impact: custom logger beans cannot replace the default cleanly.
   - Recommended fix: inject `IKafkaEventLogger` and `IRedisPubSubLogger` in configuration methods.

7. Redis cache and Redis Pub/Sub share the same Java package root.
   - Exact files/classes:
     - `common-redis-cache/.../com/example/common/redis/api/ITimeRedisCache.java`
     - `common-redis-cache/.../com/example/common/redis/core/TimeRedisCacheManager.java`
     - `common-redis/.../com/example/common/redis/publisher/RedisEventPublisher.java`
   - Why it is a problem: import paths do not reveal whether a class belongs to cache or pub/sub.
   - Architectural impact: module boundaries are blurred even when Gradle dependencies are separate.
   - Recommended fix: move cache code to `com.example.common.redis.cache.*` or merge cache into a clearly partitioned `common-redis` module.

8. Realtime flow policy is in the wrong module.
   - Exact files/classes:
     - `common-web/src/main/java/com/example/common/realtime/policy/RealtimeFlowId.java`
     - `RealtimeFlowType.java`
     - `RealtimeFlowClassificationPolicy.java`
   - Why it is a problem: this is event/realtime delivery policy used by messaging flows, not web infrastructure.
   - Architectural impact: event flow contracts depend conceptually on a web module.
   - Recommended fix: move to `common-events` under `com.example.common.realtime.policy` or create a dedicated `common-realtime-contract`.

### Low

1. Stable code still contains phase/migration wording and mojibake.
   - Exact files/classes:
     - `KafkaAutoConfiguration.java`
     - `KafkaContractTest.java`
     - `RedisContractTest.java`
   - Why it is a problem: comments such as "Phase 8" and corrupted box-drawing characters are implementation-history noise.
   - Architectural impact: low, but it makes common contracts look temporary.
   - Recommended fix: remove phase comments and replace corrupted comments with plain ASCII.

2. Interface prefix naming is inconsistent.
   - Exact files/classes:
     - `IKafkaEventLogger`
     - `IRedisPubSubLogger`
     - `ITimeRedisCache`
     - `ITimeRedisCacheManager`
     - `KafkaEventProducer`
     - `RedisEventPublisher`
   - Why it is a problem: some interfaces use `I` prefix and others do not.
   - Architectural impact: low, but it keeps the API visually inconsistent.
   - Recommended fix: remove `I` prefixes in new canonical APIs.

3. Build descriptions are misleading.
   - Exact files:
     - `common-redis-cache/build.gradle` has `description = 'Common Redis Library'`.
     - `common-websocket/build.gradle` has `description = 'Common Redis Library'`.
   - Why it is a problem: module metadata does not match module purpose.
   - Architectural impact: low, but it reinforces boundary confusion.
   - Recommended fix: rename descriptions to `Common Redis Cache Library` and `Common WebSocket Library`.

4. Redis logger assumes non-null metadata in success paths.
   - Exact file/class: `common-redis/.../observability/RedisPubSubLogger.java`
   - Why it is a problem: `logPublish`, `logReceive`, and `logForward` call `envelope.metadata()` directly.
   - Architectural impact: low because publishers validate metadata first, but listener deserialization or custom calls can still hit nulls.
   - Recommended fix: use the same null-safe style as `KafkaEventLogger`.

## 5. Inconsistency Audit

- Package naming:
  - Module is `common-events`, while the review scope and some naming imply `common-event`.
  - Generic model is `com.example.common.event.*`; domain contracts are `com.example.common.integration.*`.
  - Kafka current canonical package is `com.example.common.kafka.*`, but services still import missing `com.example.common.integration.kafka.event.*`.
  - Redis current canonical package is `com.example.common.redis.*`, but services still import missing `com.example.common.redis.message.*`.
  - Redis cache also uses `com.example.common.redis.*`, hiding its separate module.
  - Realtime flow policy is in `com.example.common.realtime.policy` under `common-web`, not under event/realtime contracts.

- Module naming:
  - `common-redis` is Redis Pub/Sub but the module name does not say pubsub.
  - `common-redis-cache` is separate but not reflected in package names.
  - There is no actual `redis-pubsub` module despite the architecture discussing Redis Pub/Sub as a separate concern.

- Class naming:
  - `FriendRequestEvent` and `NotificationEvent` are payload DTOs, not envelopes.
  - `KafkaTopics` contains values that are event names (`chat.message.sent`) and stream names (`friendship.events`) in one class.
  - `RealtimeWsEvent` uses `type` + `payload`, while the event model uses `metadata.eventType` + `payload`.

- Interface naming:
  - `IKafkaEventLogger` and `IRedisPubSubLogger` use `I` prefix.
  - `KafkaEventProducer`, `RedisEventPublisher`, `KafkaEventRegistry`, and `RedisEventRegistry` do not.
  - `ITimeRedisCacheManager` uses `I` prefix and returns concrete `TimeRedisCache` in its API.

- Event naming:
  - `account.created` vs `account.account.created`.
  - `friendship.request.sent` vs `friend.request.sent`.
  - `friendship.request.rejected` vs `friend.request.declined`.
  - `chat.message.pinned` and `chat.message.unpinned` exist in `ChatEventType` but not in `EventType`.
  - `presence.room.join` and `presence.room.leave` exist in `PresenceEventType` but not in `EventType`.
  - Presence supports deprecated underscore aliases in `PresenceEventType`, but `EventContractValidator` rejects underscores globally.

- Topic naming:
  - `KafkaTopics.CHAT_MESSAGE_SENT = "chat.message.sent"` treats topic as event type.
  - `KafkaTopics.FRIENDSHIP_EVENTS = "friendship.events"` treats topic as stream.
  - `KafkaTopics.ACCOUNT_CREATED = "account.account.created"` conflicts with `EventType.ACCOUNT_CREATED = "account.created"`.

- Redis channel naming:
  - `RedisChannels.CHAT_ROOM_PREFIX = "realtime.chat.room."` and service `ChatRedisChannels.CHAT_ROOM` duplicate the same constants.
  - `PresenceRedisChannels` and `NotificationRedisChannels` are thin service wrappers over `RedisChannels`.
  - Channel names are consistently `realtime.*`, but ownership is mixed between common and service wrapper classes.

- DTO/envelope naming:
  - `EventEnvelope<T>` is canonical.
  - Deleted `RedisMessage<T>` is still the service-level assumed envelope.
  - Deleted Kafka wrapper events are still the service-level assumed envelope.
  - `RealtimeWsEvent` is a WebSocket DTO but notification uses it as Redis Pub/Sub payload.

- Serializer/deserializer naming:
  - Redis has `RedisEventSerializer` and concrete `JsonRedisEventSerializer`.
  - Kafka has `KafkaEventSerializer` and `KafkaEventDeserializer` interfaces only, no JSON implementation.
  - Redis serializer returns `String`; Kafka serializer returns `byte[]`. That is transport-acceptable, but the abstraction level should be documented consistently.

- Publisher/subscriber/consumer/listener/handler/dispatcher naming:
  - Kafka outgoing is `Producer`; Redis outgoing is `Publisher`.
  - Kafka inbound event callback is `Handler`; Redis inbound event callback is `Subscriber`.
  - Kafka has `Consumer` as a marker; Redis has `Listener` as Spring adapter.
  - Standard should be: transport `Listener`, event `Handler`, event `Dispatcher`, event `Publisher`.

- Registry/factory/resolver naming:
  - `EventRegistry.get`
  - `KafkaEventRegistry.resolvePayload` plus `contains`
  - `RedisEventRegistry.resolvePayload` without `contains`
  - Service `RedisMessageFactory` creates a deleted type and should be replaced with an envelope factory.

- Folder structure:
  - Kafka has `config`, `consumer`, `error`, `flow`, `logging`, `producer`, `registry`, `serialization`, `topic`.
  - Redis has `channel`, `config`, `dispatcher`, `exception`, `flow`, `listener`, `observability`, `publisher`, `registry`, `serialization`, `subscriber`.
  - Cache has `api`, `core`, `exception` under the same Redis package root.

- Abstraction depth:
  - `KafkaEventConsumer` is a marker with no current service usage.
  - `EventRegistry` is a base abstraction with no implementation usage.
  - `KafkaEventSerializer`/`Deserializer` are abstractions without implementations.
  - Redis has a complete listener/serializer/dispatcher stack but services are not aligned to it.

## 6. Duplicate or Overlapping Responsibilities

- Event envelope duplication:
  - Old Kafka wrappers: missing `com.example.common.integration.kafka.event.*`.
  - Old Redis DTO: missing `com.example.common.redis.message.RedisMessage`.
  - Current common model: `EventEnvelope<T>`.
  - Standard: `EventEnvelope<T>` should become the only event wire model.

- Event type catalogs:
  - `EventType`
  - `ChatEventType`
  - `PresenceEventType`
  - `FriendshipEventType`
  - `AccountEventType`
  - `NotificationEventType`
  - `UserEventType`
  - `KafkaTopics`
  - Standard: domain event-name catalogs should be canonical; remove or generate the global `EventType`.

- Registry abstractions:
  - `EventRegistry`
  - `KafkaEventRegistry`
  - `RedisEventRegistry`
  - Standard: one `EventPayloadRegistry` in `common-events`, used by transports, or service-local registration without transport-specific registry interfaces.

- Redis Pub/Sub implementations:
  - Common Redis stack: `RedisEventPublisher`, `RedisEventListener`, `RedisEventDispatcher`, `RedisEventSubscriber`.
  - Notification direct stack: `RedisNotificationPublisher`, `RedisNotificationSubscriber`.
  - Standard: all Redis Pub/Sub should use the common Redis stack and translate to WebSocket at the edge.

- WebSocket envelope vs event envelope:
  - `EventEnvelope<T>` for internal/event transport.
  - `RealtimeWsEvent` for client wire protocol.
  - Standard: Redis/Kafka carry `EventEnvelope<T>`; WebSocket sends `RealtimeWsEvent`.

- Topic/channel definitions:
  - Common `KafkaTopics` and service wrappers around `RedisChannels`.
  - Standard: transport modules own route constants; services may have local aliases only if they add domain-specific helper behavior, not simple copies.

- Handler/dispatcher patterns:
  - Kafka: `KafkaEventHandler` + `KafkaEventDispatcher`.
  - Redis: `RedisEventSubscriber` + `RedisEventDispatcher`.
  - Standard: use `EventHandler<T>` style for both; listener adapters should be transport-specific.

- Realtime flow policy ownership:
  - Current: `common-web`.
  - Conceptual: messaging/realtime contract.
  - Standard: move to `common-events` or dedicated `common-realtime-contract`.

## 7. Transport-Agnostic Boundary Check

- `common-events` is mostly pure at code-import level. It does not import Kafka, Redis, Spring Kafka, Spring Redis, WebSocket, or cache classes.

- What belongs in `common-events`:
  - `EventEnvelope<T>`
  - `EventMetadata`
  - Event naming rules
  - Canonical event-name catalogs
  - Shared domain payload DTOs
  - Shared enums that are part of those payload contracts
  - Optional payload registry abstraction if both transports use it

- What must not be in `common-events`:
  - Kafka wrapper events
  - Redis message DTOs
  - Kafka topic constants
  - Redis channel constants
  - WebSocket protocol DTOs
  - Spring listener/publisher/serializer implementations
  - Cache abstractions

- Kafka/Redis-specific leakage:
  - No current Kafka/Redis imports leak into `common-events`.
  - `EventRegistry` Javadoc explicitly talks about Kafka and Redis implementations, but the actual transports do not use it. This is conceptual leakage and dead abstraction.
  - `Event.java` and `EventMetadata.java` mention Kafka/Redis in Javadocs. This is not a code dependency, but stable docs should describe transports generically as "message transports."

- Metadata/envelope coupling:
  - `EventMetadata` fields are acceptable and transport-agnostic: `eventId`, `eventType`, `sourceService`, `createdAt`, `correlationId`.
  - It does not contain Kafka topic, partition, offset, Redis channel, or WebSocket destination. That is good.
  - The problem is not metadata shape; the problem is multiple event-name catalogs and deleted transport-specific envelopes still assumed by services.

- Verdict:
  - `common-events` is transport-agnostic enough in code.
  - It is not contract-coherent because `EventType`, domain event enums, and transport route constants disagree.
  - Keep it pure, but make it the single owner of envelope and event-name contracts.

## 8. Contract Package Verdict

- `com.example.common.integration.contract`
  - Why it existed: apparently as a realtime contract validator/conventions bridge used by tests such as `RealtimeContractValidatorTest`.
  - Current value: none in source because the package is missing.
  - Duplicate risk: high. A vague `integration.contract` package overlaps with `common.event.validation`, `common.integration.<domain>`, and realtime policy.
  - Verdict: do not keep long term. Temporarily restore only if needed to compile tests during migration, then replace with explicit `com.example.common.event.validation` or `com.example.common.realtime.contract`.

- `com.example.common.integration.realtime`
  - Why it existed: apparently for `RealtimeContractVersions` and older Redis channel/version bridges.
  - Current value: none in source because the package is missing while tests still import it.
  - Duplicate risk: high. Version constants should not sit beside domain payloads unless versioning is part of the event contract module.
  - Verdict: do not keep as a broad package. Move needed version constants to `com.example.common.realtime.contract` or delete if they only existed for historical tests.

- `com.example.common.integration.kafka.event`
  - Why it existed: Kafka-specific wrapper event contracts for shared payloads.
  - Current value: services still need it to compile, but it no longer exists.
  - Duplicate risk: very high. It duplicates `EventEnvelope<T>` and ties contracts to Kafka.
  - Verdict: should not remain as target architecture. Restore only as temporary migration compatibility if immediate service migration is too large.

- `com.example.common.integration.<domain>`
  - Why it exists: shared domain payload contracts used across services.
  - Current value: real value. `ChatMessagePayload`, `ReactionPayload`, `FriendshipPayload`, presence payloads, `AccountCreatedPayload`, and `NotificationRequestedPayload` are shared contracts.
  - Duplicate risk: medium due to naming and event enum duplication.
  - Verdict: keep, but standardize DTO style and event-name ownership. Optionally rename later to `com.example.common.event.contract.<domain>`.

- Replacement standard if vague contract packages are removed:
  - `com.example.common.event` or `com.example.common.event.model` for envelope/metadata.
  - `com.example.common.event.type` for canonical event names.
  - `com.example.common.event.validation` for validators.
  - `com.example.common.integration.<domain>` or `com.example.common.event.contract.<domain>` for payload DTOs.
  - `com.example.common.realtime.policy` or `com.example.common.realtime.contract` for delivery semantics/version policy.

## 9. Dead Code / Legacy / Deprecated Candidates

- `common-events/src/main/java/com/example/common/event/registry/EventRegistry.java`
  - Appears unused by production code.
  - Overly generic without real value because Kafka and Redis have their own registries.
  - Safe to delete after replacing any tests/imports or merging into a real `EventPayloadRegistry`.

- `common-events/src/main/java/com/example/common/event/EventType.java`
  - Not used by production services.
  - Incomplete and conflicts with domain enums.
  - Safe to delete after moving validation tests to domain event-name catalogs.

- `common-events/src/main/java/com/example/common/integration/account/AccountEventType.java`
  - No production references found.
  - Values conflict with `EventType`.
  - Safe to delete or replace with corrected domain event-name catalog.

- `common-events/src/main/java/com/example/common/integration/user/UserEventType.java`
  - No production references found.
  - Overly generic and not connected to current Kafka/Redis flows.
  - Safe to delete after search confirmation in tests.

- `common-events/src/main/java/com/example/common/integration/user/UserPresencePayload.java`
  - No production references found.
  - Duplicates presence payload concepts.
  - Safe to delete after test/import confirmation.

- `common-events/src/main/java/com/example/common/integration/notification/NotificationEvent.java`
  - No production references found in the scoped flow.
  - Duplicates `NotificationRequestedPayload` and notification service models.
  - Safe to delete or rename to a real payload if it becomes used.

- `common-events/src/main/java/com/example/common/integration/notification/NotificationEventType.java`
  - No production references found.
  - Duplicates event naming without being used.
  - Safe to delete after test/import confirmation.

- `common-kafka/src/main/java/com/example/common/kafka/serialization/KafkaEventSerializer.java`
  - Interface-only, unused by producer/consumers.
  - Wrapper-only abstraction until an implementation exists.
  - Delete or implement.

- `common-kafka/src/main/java/com/example/common/kafka/serialization/KafkaEventDeserializer.java`
  - Interface-only, unused by listener/consumer flow.
  - Delete or implement.

- `common-kafka/src/main/java/com/example/common/kafka/registry/KafkaEventRegistry.java`
  - No production service usage found.
  - Duplicates Redis/common registries.
  - Delete after deciding on shared registry.

- `common-kafka/src/main/java/com/example/common/kafka/registry/DefaultKafkaEventRegistry.java`
  - Auto-configured but no connected service usage found.
  - Delete if Kafka continues using typed `@KafkaListener EventEnvelope<T>` without runtime registry.

- `common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventConsumer.java`
  - Marker/grouping abstraction with no connected service usage.
  - Delete unless services adopt it.

- `common-redis/src/main/java/com/example/common/redis/config/RedisContractVersions.java`
  - No production references found.
  - Looks like leftover transport version metadata.
  - Delete or move to explicit realtime contract versioning if still required.

- Empty/missing contract directories under `common-events/src/main/java/com/example/common/integration/contract`, `realtime`, and `websocket`
  - These currently add no source.
  - Remove empty directories or restore explicit compatibility classes temporarily.

- Deleted but still referenced compatibility classes:
  - `com.example.common.integration.kafka.event.*`
  - `com.example.common.redis.message.RedisMessage`
  - `com.example.common.redis.api.IRedisMessage`
  - They are not dead from the service perspective; they are missing migration bridges. Restore temporarily only if service migration cannot happen immediately.

## 10. Target Standard Architecture

- Target `common-events` responsibilities:
  - Own `EventEnvelope<T>` and `EventMetadata`.
  - Own event-name validation rules.
  - Own canonical event names as domain catalogs.
  - Own shared domain payload DTOs.
  - Optionally own one `EventPayloadRegistry`.
  - Must not own transport routes, transport wrappers, Redis DTOs, Kafka DTOs, WebSocket DTOs, or Spring infrastructure.

- Target `common-events` package structure:
  - `com.example.common.event.model`
    - `EventEnvelope`
    - `EventMetadata`
  - `com.example.common.event.validation`
    - `EventNameValidator`
    - `EventContractValidator`
  - `com.example.common.event.type`
    - `AccountEventNames`
    - `ChatEventNames`
    - `FriendshipEventNames`
    - `NotificationEventNames`
    - `PresenceEventNames`
  - `com.example.common.event.registry`
    - `EventPayloadRegistry`
    - `DefaultEventPayloadRegistry` if runtime deserialization needs a shared registry
  - `com.example.common.integration.account`
  - `com.example.common.integration.chat`
  - `com.example.common.integration.friendship`
  - `com.example.common.integration.notification`
  - `com.example.common.integration.presence`

- Target `common-kafka` responsibilities:
  - Own Kafka topic constants.
  - Own Kafka publisher implementation.
  - Own Kafka listener/consumer adapters.
  - Own Kafka-specific serialization/deserialization if not delegated to Spring Kafka defaults.
  - Own Kafka observability and exceptions.
  - Must not own domain event wrapper classes.

- Target `common-kafka` package structure:
  - `com.example.common.kafka.config`
  - `com.example.common.kafka.publisher`
    - `KafkaEventPublisher`
    - `DefaultKafkaEventPublisher`
  - `com.example.common.kafka.listener`
    - `KafkaEventListenerAdapter`
  - `com.example.common.kafka.handler`
    - `KafkaEventHandler`
    - `KafkaEventDispatcher`
  - `com.example.common.kafka.topic`
    - `KafkaTopics`
  - `com.example.common.kafka.serialization`
    - `JsonKafkaEventSerializer`
    - `JsonKafkaEventDeserializer`
  - `com.example.common.kafka.observability`
  - `com.example.common.kafka.exception`

- Target `common-redis` responsibilities:
  - Own Redis Pub/Sub channels.
  - Own Redis Pub/Sub publisher.
  - Own Redis Pub/Sub listener adapter.
  - Own Redis event dispatcher/handler contracts.
  - Own Redis Pub/Sub serialization/deserialization.
  - Own Redis Pub/Sub observability and exceptions.
  - Must not own cache classes unless cache is deliberately merged into the same module under a separate package.

- Target `common-redis` package structure:
  - `com.example.common.redis.pubsub.config`
  - `com.example.common.redis.pubsub.publisher`
    - `RedisEventPublisher`
    - `DefaultRedisEventPublisher`
  - `com.example.common.redis.pubsub.listener`
    - `RedisEventListener`
  - `com.example.common.redis.pubsub.handler`
    - `RedisEventHandler`
    - `RedisEventDispatcher`
  - `com.example.common.redis.pubsub.channel`
    - `RedisChannels`
  - `com.example.common.redis.pubsub.serialization`
    - `RedisEventSerializer`
    - `JsonRedisEventSerializer`
  - `com.example.common.redis.pubsub.observability`
  - `com.example.common.redis.pubsub.exception`

- Target `common-redis-cache` responsibilities:
  - Own Redis cache manager/cache extensions only.
  - Package structure should be `com.example.common.redis.cache.*`.
  - No dependency on `common-events`.

- Naming standard:
  - Use `Publisher` for outgoing transport abstraction: `KafkaEventPublisher`, `RedisEventPublisher`.
  - Use `Listener` for transport adapters.
  - Use `Handler` for event-type business callbacks.
  - Use `Dispatcher` for eventType-to-handler routing.
  - Use `KafkaTopics` and `RedisChannels` only for transport routes.
  - Use domain event-name catalogs only for semantic event names.
  - Avoid `I` prefixes in new canonical interfaces.

- Ownership of contracts:
  - Payload contracts: `common-events`.
  - Envelope contract: `common-events`.
  - Event names: `common-events`.
  - Kafka topics: `common-kafka`.
  - Redis channels: `common-redis`.
  - WebSocket DTO: `common-websocket`.
  - Realtime flow classification: move out of `common-web` to `common-events` or `common-realtime-contract`.

- Ownership of serializers:
  - `common-events` should not own transport serializers.
  - `common-kafka` owns Kafka envelope serializer/deserializer if Spring Kafka defaults are insufficient.
  - `common-redis` owns Redis envelope serializer/deserializer.

- Ownership of publishers/subscribers/consumers:
  - `common-kafka` owns Kafka publisher/listener/handler/dispatcher infrastructure.
  - `common-redis` owns Redis publisher/listener/handler/dispatcher infrastructure.
  - Services own domain-specific event creation and business handling.

- Ownership of topic/channel definitions:
  - `KafkaTopics` stays in `common-kafka`, but values should be stream routes, not the canonical event-name catalog.
  - `RedisChannels` stays in `common-redis`, but service wrappers should only exist when they add meaningful helpers.

## 11. Refactor Order

1. Standardize the event-name catalog first.
   - Pick domain event-name catalogs as canonical.
   - Correct account names from `account.account.*` to `account.*` unless there is a deliberate external contract requiring the duplicated segment.
   - Correct friendship names to one namespace, preferably `friendship.*`, not mixed `friend.*` and `friendship.*`.
   - Add active chat/presence values missing from the canonical catalog.

2. Restore compile safety for migration compatibility.
   - Best path: migrate services directly to `EventEnvelope<T>` in the same refactor branch.
   - Compatibility path: temporarily restore `com.example.common.integration.kafka.event.*`, `RedisMessage`, and `IRedisMessage` as deprecated bridges only if a big-bang service migration is too risky.
   - Do not make restored bridges the target standard.

3. Migrate Kafka publishers.
   - Replace `AccountCreatedEvent.from(...)`, `ChatMessageSentEvent.from(...)`, `FriendshipEvent.of(...)`, and similar wrapper factories with `new EventEnvelope<>(metadata, payload)`.
   - Keep topic selection in service adapters using `KafkaTopics`.
   - Move event type into `EventMetadata.eventType`.

4. Migrate Kafka consumers.
   - Replace `@KafkaListener` wrapper parameters with `EventEnvelope<Payload>` or a common listener adapter that deserializes to envelope then dispatches.
   - If dispatching mixed event types from one topic, use `KafkaEventDispatcher`.
   - Delete wrapper imports only after all listeners compile.

5. Migrate Redis publishers.
   - Delete `RedisMessageFactory` or replace it with `EventEnvelopeFactory`.
   - Update `ChatRedisPublisher` and `PresenceRedisPublisher` to call `RedisEventPublisher.publish(channel, EventEnvelope<?>)`.
   - Move `messageId` semantics to `EventMetadata.eventId`; do not keep a separate Redis-only message ID unless it is transport metadata outside the envelope.

6. Migrate Redis subscribers.
   - Change `RedisEventSubscriber<T>` implementations to receive `EventEnvelope<T>`.
   - Update WebSocket translation to read `event.metadata().getEventType()` and `event.payload()`.
   - Remove all `RedisMessage<T>` imports.

7. Bring notification Redis onto the same standard.
   - Replace direct `StringRedisTemplate` Pub/Sub for notification realtime with `RedisEventPublisher` and `RedisEventListener`.
   - Keep `RealtimeWsEvent` only in the WebSocket delivery adapter.

8. Unify or delete registries.
   - If Redis/Kafka both need dynamic payload resolution, introduce `EventPayloadRegistry` in `common-events`.
   - Otherwise keep registration local to transport adapters and delete unused `EventRegistry`/`KafkaEventRegistry`.
   - Make method names and duplicate handling identical.

9. Implement or remove Kafka serializers.
   - If envelope deserialization is centralized, add `JsonKafkaEventSerializer` and `JsonKafkaEventDeserializer`.
   - If Spring Kafka handles it per service, delete the unused interfaces.

10. Move package boundaries.
   - Move Redis Pub/Sub to `com.example.common.redis.pubsub.*`.
   - Move Redis cache to `com.example.common.redis.cache.*`.
   - Move realtime flow policy out of `common-web`.
   - Keep old package bridges temporarily if service migration must be staged.

11. Standardize auto-configuration.
   - Add Boot auto-configuration imports for common Kafka/Redis modules or require explicit `@Import`.
   - Stop relying on broad `@ComponentScan("com.example.common")`.
   - Inject logger interfaces, not concrete logger classes.

12. Delete compatibility and dead abstractions last.
   - Delete restored `integration.kafka.event.*` wrappers.
   - Delete restored Redis message DTO/interfaces.
   - Delete `EventType` or the duplicate domain enums according to the chosen catalog.
   - Delete `KafkaEventConsumer`, `KafkaEventSerializer`, `KafkaEventDeserializer`, `RedisContractVersions`, and unused payload DTOs after reference checks pass.

## 12. Final Verdict

- What should stay:
  - `EventEnvelope<T>` and `EventMetadata`.
  - Actively used domain payload DTOs in `common-events`.
  - `KafkaTopics` as Kafka route constants after cleaning names.
  - `RedisChannels` as Redis route constants.
  - Redis/Kafka publisher, listener, dispatcher, handler concepts.
  - `TimeRedisCache` and `TimeRedisCacheManager`, but in cache-specific packages.
  - `RealtimeWsEvent` in `common-websocket` as the WebSocket client envelope only.

- What should move:
  - Redis cache classes to `com.example.common.redis.cache.*`.
  - Redis Pub/Sub classes to `com.example.common.redis.pubsub.*` if package churn is acceptable.
  - `RealtimeFlowId`, `RealtimeFlowType`, and `RealtimeFlowClassificationPolicy` out of `common-web`.
  - Contract version/validator classes, if still needed, to explicit event/realtime contract packages.

- What should merge:
  - `EventRegistry`, `KafkaEventRegistry`, and `RedisEventRegistry` into one payload registry, or delete the base and keep no shared registry.
  - Kafka and Redis inbound patterns into listener + handler + dispatcher.
  - Event-name catalogs into one canonical domain-based strategy.

- What should split:
  - Redis Pub/Sub and Redis cache package ownership.
  - WebSocket client protocol from Redis Pub/Sub event protocol.
  - Transport route constants from semantic event names.

- What should be removed:
  - Long term: `com.example.common.integration.kafka.event.*` wrappers.
  - Long term: `RedisMessage`, `IRedisMessage`, and Redis-specific event envelopes.
  - `EventType` if domain event catalogs become canonical.
  - `KafkaEventSerializer` and `KafkaEventDeserializer` unless implemented and wired.
  - `KafkaEventConsumer` unless services adopt it.
  - `RedisContractVersions` if no explicit versioning contract remains.
  - `AccountEventType`, `UserEventType`, `UserPresencePayload`, `NotificationEvent`, and `NotificationEventType` if reference checks stay clean.
  - Vague `common.integration.contract` packages after replacing their responsibilities with explicit contracts.

- Long-term verdict:
  - The current common architecture is not acceptable for long-term growth.
  - The correct standard is envelope-first, transport-thin, and domain-contract-owned:
    - `common-events` owns envelope, metadata, event names, and payloads.
    - `common-kafka` owns Kafka transport routes and adapters.
    - `common-redis` owns Redis Pub/Sub routes and adapters.
    - `common-redis-cache` owns cache behavior only.
  - Kafka is closer to the target than Redis, but Kafka is still broken because service code depends on deleted wrappers.
  - Redis must move to the same `EventEnvelope<T>` standard, and notification Redis must stop being a separate pattern.
