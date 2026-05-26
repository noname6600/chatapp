# Common Redis / Kafka / Event Review

Scope reviewed:
- `chatappBE/common/common-events`
- `chatappBE/common/common-kafka`
- `chatappBE/common/common-redis`
- `chatappBE/common/common-redis-cache`
- Connected shared realtime/event contract utilities referenced by those modules, especially `com.example.common.realtime.policy` under `common-web`
- Service usage was sampled only to verify actual publish/consume flow and whether the common contracts are usable.

## 1. Summary

### What each module currently contains

`common-events` currently contains two different contract layers:
- Generic event model: `Event`, `EventEnvelope`, `EventMetadata`, `EventType`, `EventContractValidator`, and `EventRegistry`.
- Domain payload and enum packages under `com.example.common.integration.*`: `account`, `chat`, `friendship`, `notification`, `presence`, `user`, and shared `enums`.

`common-kafka` currently contains Kafka infrastructure:
- Producer API and implementation: `KafkaEventProducer`, `DefaultKafkaEventProducer`.
- Consumer-side routing contracts: `KafkaEventHandler`, `KafkaEventConsumer`, `KafkaEventDispatcher`.
- Registry contracts: `KafkaEventRegistry`, `DefaultKafkaEventRegistry`.
- Serializer contracts only: `KafkaEventSerializer`, `KafkaEventDeserializer`.
- Topic constants: `KafkaTopics`.
- Logging, routing, error, and auto-configuration classes.

`common-redis` currently contains Redis Pub/Sub infrastructure:
- Publisher API and implementation: `RedisEventPublisher`, `DefaultRedisEventPublisher`.
- Subscriber and dispatcher: `RedisEventSubscriber`, `RedisEventDispatcher`, `RedisEventListener`.
- Redis message compatibility model: deprecated `IRedisMessage`, `AbstractRedisMessage`, `RedisMessage`.
- Registry and serializer: `RedisEventRegistry`, `DefaultRedisEventRegistry`, `RedisEventSerializer`, `JsonRedisEventSerializer`.
- Redis channel constants: `RedisChannels`.
- Logging, routing, error, field constants, and configuration classes.

`common-redis-cache` contains Redis cache extensions:
- `ITimeRedisCache`, `ITimeRedisCacheManager`.
- `TimeRedisCache`, `TimeRedisCacheManager`.
- `CreateCacheException`.
- It does not use event contracts at runtime, but its Gradle file still depends on `common-events`.

### How Redis, Kafka, and Event modules currently interact

The intended direction is:
- `common-events` is the shared contract base.
- `common-kafka` depends on `common-events`.
- `common-redis` depends on `common-events`.
- Application services depend on the transport module plus `common-events`.

The actual direction mostly follows that at the Gradle level, but the model is not consistent:
- Kafka infrastructure publishes `EventEnvelope<?>`.
- Redis publisher also claims to publish `EventEnvelope<?>`.
- Redis listener and dispatcher still consume deprecated `IRedisMessage` / `RedisMessage`.
- Service Redis publishers still build `RedisMessage<T>` and pass it into `RedisEventPublisher`, whose current method signature requires `EventEnvelope<?>`. Example mismatch: `ChatRedisPublisher` calls `redisPublisher.publish(channel, redisMessageFactory.create(...))`, but `RedisEventPublisher.publish` accepts `EventEnvelope<?>`.
- Service Kafka producers and consumers import `com.example.common.integration.kafka.event.*`, but that package does not exist in the current source tree. The old `common-kafka/src/main/java/com/example/common/kafka/event/*` files are deleted.

### Whether the current structure is consistent

No. The current structure is a mid-migration state with broken edges:
- Kafka moved toward the shared `EventEnvelope` model.
- Redis is split between shared `EventEnvelope` and deprecated `IRedisMessage`.
- Event naming is fragmented across `EventType`, domain-specific enums, and `KafkaTopics`.
- Redis Pub/Sub has a reusable common abstraction, but notification Redis Pub/Sub bypasses it entirely.
- Auto-configuration classes exist but are not reliably discoverable.
- Several "contract" packages referenced by services do not exist in the current common source tree.

## 2. Current Architecture

### Dependency direction between modules

Current Gradle dependencies:
- `common-kafka` has `implementation project(':common:common-events')`.
- `common-redis` has `implementation project(':common:common-events')`.
- `common-redis-cache` has `implementation project(':common:common-events')`, but the cache source does not use `common-events`. This dependency should be removed.
- `common-events` has no dependency on Kafka or Redis.

Code-level dependencies:
- `common-kafka` imports `EventEnvelope`, `EventMetadata`, and `EventContractValidator`.
- `common-redis` imports `EventEnvelope`, `EventMetadata`, and `EventContractValidator`, while also retaining `IRedisMessage`.
- `common-events` does not import Redis or Kafka classes, so it is mostly transport-agnostic at code level.

Transport concerns still leak conceptually into `common-events`:
- `EventMetadata` Javadoc explicitly frames metadata as shared across "Kafka, Redis, etc.".
- `EventRegistry` Javadoc says implementations may vary by Kafka and Redis.
- `EventRegistry` itself is unused by the transports, because Kafka and Redis each define their own registry.

### Actual flow of event publishing and consumption

Kafka flow as currently written:
- Services build event wrapper classes such as `AccountCreatedEvent`, `ChatMessageSentEvent`, `FriendshipEvent`, `FriendRequestKafkaEvent`, and `NotificationRequestedEvent`.
- Those wrappers are imported from `com.example.common.integration.kafka.event.*`.
- That package is absent from the current source tree, and the old `common-kafka/src/main/java/com/example/common/kafka/event/*` files are deleted.
- `KafkaEventProducer.publish(topic, key, EventEnvelope<?>)` expects a shared envelope.
- `DefaultKafkaEventProducer` validates both topic and `metadata.eventType`, sends through `KafkaTemplate<String, Object>`, logs, and blocks on `.join()`.
- Consumers generally use service-local `@KafkaListener` methods with the missing wrapper classes. They do not use `KafkaEventDispatcher` or `KafkaEventHandler`.

Redis Pub/Sub flow as intended:
- Services register event type to payload class in a service-local registry config, for example `ChatRedisEventConfig` and `PresenceRedisRegistryConfig`.
- A service-local listener container wires `RedisEventListener` to Redis channel patterns.
- `RedisEventListener` deserializes the raw payload via `RedisEventSerializer.deserialize`.
- `JsonRedisEventSerializer.deserialize` reads flat fields: top-level `eventType` or legacy `type`, and top-level `payload` or legacy `data`.
- It resolves a payload class from `RedisEventRegistry`.
- It returns `IRedisMessage`, specifically a `RedisMessage<Object>`.
- `RedisEventListener` converts that message to `EventEnvelope<?>` only for logging.
- `RedisEventDispatcher` dispatches the original `IRedisMessage` to `RedisEventSubscriber<T extends IRedisMessage>`.

Redis Pub/Sub flow as currently broken:
- `RedisEventPublisher.publish` accepts `EventEnvelope<?>`.
- `DefaultRedisEventPublisher` serializes `EventEnvelope<?>` using `RedisEventSerializer.serialize(EventEnvelope<?>)`.
- `JsonRedisEventSerializer.serialize(EventEnvelope<?>)` writes nested envelope JSON: `{"metadata": {...}, "payload": ...}`.
- `JsonRedisEventSerializer.deserialize` cannot read that canonical envelope format because it only looks for top-level `eventType` or `type`.
- Existing service publishers still build `RedisMessage<T>`, which does have top-level `eventType`, but those objects no longer match the `RedisEventPublisher.publish` signature.

Notification Redis flow:
- `notification-service` bypasses `common-redis` Pub/Sub abstractions.
- `RedisNotificationPublisher` uses `StringRedisTemplate` directly and serializes `RealtimeWsEvent`.
- `RedisNotificationSubscriber` implements Spring's `MessageListener` directly and deserializes `RealtimeWsEvent`.
- It reuses only `RedisChannels` through `NotificationRedisChannels`.

Cache flow:
- `common-redis-cache` is independent from event publishing.
- User, chat, and presence services use `TimeRedisCacheManager` / `ITimeRedisCacheManager` for cache access.
- Cache code is not involved in event flow and should not depend on `common-events`.

### Where responsibilities begin and end

Current intended boundaries:
- `common-events`: event envelope, metadata, domain payload DTOs, event names.
- `common-kafka`: Kafka transport publication, topic naming, Kafka-side dispatch helpers.
- `common-redis`: Redis Pub/Sub publication, channel naming, Redis-side dispatch helpers.
- `common-redis-cache`: Redis cache manager extension.

Actual boundaries are blurred:
- `common-kafka/topic/KafkaTopics.java` owns both Kafka topic names and domain event names.
- `common-redis/channel/RedisChannels.java` owns domain-specific realtime channel names.
- `common-events/EventType.java` duplicates event names also found in domain enums and topic constants.
- `common-redis` still owns an event DTO model through `IRedisMessage` and `RedisMessage`, even though `common-events` now owns `EventEnvelope`.
- `common-web` owns `com.example.common.realtime.policy.*`, which is an event/realtime flow contract, not web infrastructure.

## 3. Problems

### High

1. `common-redis` has a compile-breaking API/model mismatch.
   - `RedisEventPublisher.publish(String, EventEnvelope<?>)` requires `EventEnvelope`.
   - Existing service publishers call it with `RedisMessage<T>`.
   - Files involved:
     - `common/common-redis/src/main/java/com/example/common/redis/publisher/RedisEventPublisher.java`
     - `common/common-redis/src/main/java/com/example/common/redis/publisher/DefaultRedisEventPublisher.java`
     - `chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatRedisPublisher.java`
     - `presence-service/src/main/java/com/example/presence/redis/PresenceRedisPublisher.java`
   - This is not just style drift. The public Redis publisher contract and its primary callers do not agree.

2. Redis serialization writes one shape and reads another.
   - `JsonRedisEventSerializer.serialize(EventEnvelope<?>)` writes `metadata.eventType`.
   - `JsonRedisEventSerializer.deserialize(String)` reads top-level `eventType` or `type`.
   - A message emitted by `DefaultRedisEventPublisher` cannot be consumed by `RedisEventListener` using the canonical envelope path.
   - Files involved:
     - `common/common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisEventSerializer.java`
     - `common/common-redis/src/main/java/com/example/common/redis/listener/RedisEventListener.java`

3. Kafka contract wrappers were removed from common source but services still import them.
   - Current source tree has no `com.example.common.integration.kafka.event` package.
   - Current source tree has no `common/common-kafka/src/main/java/com/example/common/kafka/event/*` package.
   - Services still import:
     - `com.example.common.integration.kafka.event.AccountCreatedEvent`
     - `com.example.common.integration.kafka.event.ChatMessageSentEvent`
     - `com.example.common.integration.kafka.event.ChatMessageEditedEvent`
     - `com.example.common.integration.kafka.event.ChatMessageDeletedEvent`
     - `com.example.common.integration.kafka.event.ChatReactionUpdatedEvent`
     - `com.example.common.integration.kafka.event.FriendshipEvent`
     - `com.example.common.integration.kafka.event.FriendRequestKafkaEvent`
     - `com.example.common.integration.kafka.event.NotificationRequestedEvent`
   - Import sites include:
     - `auth-service/src/main/java/com/example/auth/kafka/AccountCreatedEventProducer.java`
     - `user-service/src/main/java/com/example/user/kafka/AccountCreatedConsumer.java`
     - `chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java`
     - `chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventConsumer.java`
     - `notification-service/src/main/java/com/example/notification/kafka/NotificationEventProducer.java`
     - `friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java`
   - The codebase is between two architectures: wrapper events are gone, but call sites were not migrated to `EventEnvelope`.

4. Event naming has multiple contradictory sources of truth.
   - `EventType.ACCOUNT_CREATED` is `account.created`.
   - `AccountEventType.ACCOUNT_CREATED` is `account.account.created`.
   - `KafkaTopics.ACCOUNT_CREATED` is `account.account.created`.
   - `EventType.FRIENDSHIP_REQUEST_SENT` is `friendship.request.sent`.
   - `FriendshipEventType.FRIEND_REQUEST_SENT` is `friend.request.sent`.
   - `KafkaTopics.FRIENDSHIP_EVENTS` is a stream-like topic, while `KafkaTopics.CHAT_MESSAGE_SENT` is an event-type-like topic.
   - Files involved:
     - `common/common-events/src/main/java/com/example/common/event/EventType.java`
     - `common/common-events/src/main/java/com/example/common/integration/account/AccountEventType.java`
     - `common/common-events/src/main/java/com/example/common/integration/friendship/FriendshipEventType.java`
     - `common/common-kafka/src/main/java/com/example/common/kafka/topic/KafkaTopics.java`

5. `DefaultKafkaEventProducer` conflates Kafka topic naming with event type naming.
   - It calls `EventContractValidator.validateEventNameOrThrow(topic)`.
   - Kafka topics are transport routes and should not be forced to share the event type grammar.
   - This blocks valid Kafka naming schemes and encourages topics to masquerade as event names.
   - File: `common/common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventProducer.java`.

6. Common auto-configuration is not wired as real auto-configuration.
   - `KafkaAutoConfiguration` is annotated with `@AutoConfiguration`, but no `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file exists in `common-kafka`.
   - `RedisAutoConfiguration` is a plain `@Configuration`, but common library packages are not automatically scanned by service applications unless explicitly imported or included in component scan.
   - Files involved:
     - `common/common-kafka/src/main/java/com/example/common/kafka/config/KafkaAutoConfiguration.java`
     - `common/common-redis/src/main/java/com/example/common/redis/config/RedisAutoConfiguration.java`
   - Result: these classes look reusable, but consumers cannot rely on them being loaded.

### Medium

1. Redis and Kafka implementations solve the same handler routing problem differently.
   - Kafka: `KafkaEventHandler<T>.handle(EventEnvelope<T>)`.
   - Redis: `RedisEventSubscriber<T extends IRedisMessage>.onMessage(T)`.
   - Kafka has `KafkaEventConsumer` marker; Redis does not.
   - Redis uses deprecated message DTOs; Kafka uses shared envelopes.

2. Three registry abstractions duplicate the same responsibility.
   - `common.event.registry.EventRegistry<T>`
   - `common.kafka.registry.KafkaEventRegistry`
   - `common.redis.registry.RedisEventRegistry`
   - Method names are inconsistent: `get` vs `resolvePayload`, `contains` exists in common/Kafka but not Redis.

3. `common-redis-cache` is packaged as if it were the same module as `common-redis`.
   - Both modules publish classes under `com.example.common.redis.*`.
   - `common-redis-cache` has `com.example.common.redis.api.ITimeRedisCacheManager`, while `common-redis` has `com.example.common.redis.api.IRedisMessage`.
   - This makes module ownership invisible from imports.

4. `common-redis-cache` depends on `common-events` unnecessarily.
   - Its source only uses Spring cache/Redis classes.
   - File: `common/common-redis-cache/build.gradle`.

5. Notification Redis Pub/Sub duplicates responsibility instead of using `common-redis`.
   - `RedisNotificationPublisher` and `RedisNotificationSubscriber` use `StringRedisTemplate` and `MessageListener` directly.
   - They do not use `RedisEventPublisher`, `RedisEventListener`, `RedisEventDispatcher`, `RedisEventRegistry`, or `RedisEventSubscriber`.
   - Files:
     - `notification-service/src/main/java/com/example/notification/websocket/redis/RedisNotificationPublisher.java`
     - `notification-service/src/main/java/com/example/notification/websocket/redis/RedisNotificationSubscriber.java`

6. `common-events` payload DTO styles are inconsistent.
   - Most chat/presence payloads are immutable final classes with `@Builder` and `@JsonCreator`.
   - `NotificationRequestedPayload` is mutable, non-final, and uses `@AllArgsConstructor`.
   - `UserPresencePayload` is mutable, non-final, has no package usage outside its own file, and duplicates presence concepts.
   - Files:
     - `common/common-events/src/main/java/com/example/common/integration/notification/NotificationRequestedPayload.java`
     - `common/common-events/src/main/java/com/example/common/integration/user/UserPresencePayload.java`

7. Realtime delivery policy lives in the wrong common module.
   - `RealtimeFlowClassificationPolicy`, `RealtimeFlowId`, and `RealtimeFlowType` are under `common-web`.
   - They are used by chat, presence, notification, and friendship realtime flows.
   - This is not web-specific; it is event/realtime contract policy.
   - Files:
     - `common/common-web/src/main/java/com/example/common/realtime/policy/RealtimeFlowClassificationPolicy.java`
     - `common/common-web/src/main/java/com/example/common/realtime/policy/RealtimeFlowId.java`
     - `common/common-web/src/main/java/com/example/common/realtime/policy/RealtimeFlowType.java`

8. `KafkaEventSerializer` and `KafkaEventDeserializer` are contracts without implementations or wiring.
   - They are not used by `DefaultKafkaEventProducer`.
   - They are not used by the service `@KafkaListener` methods.
   - Files:
     - `common/common-kafka/src/main/java/com/example/common/kafka/serialization/KafkaEventSerializer.java`
     - `common/common-kafka/src/main/java/com/example/common/kafka/serialization/KafkaEventDeserializer.java`

### Low

1. Naming uses inconsistent interface prefixes.
   - `IRedisMessage`, `IRedisPubSubLogger`, `ITimeRedisCache`, `ITimeRedisCacheManager`, `IKafkaEventLogger`.
   - But `RedisEventPublisher`, `KafkaEventProducer`, `RedisEventRegistry`, and `KafkaEventRegistry` do not use `I`.

2. Package naming differs for the same concern.
   - Kafka uses `logging` and `error`.
   - Redis uses `observability` and `exception`.
   - Kafka uses `topic`.
   - Redis uses `channel`.
   - Redis cache uses `core`, while Redis Pub/Sub no longer has a `pubsub` package marker.

3. Source comments contain migration/phase language and mojibake.
   - `KafkaAutoConfiguration` contains a corrupted box-drawing comment.
   - Several classes say "introduced in Phase X"; those notes do not belong in stable common contracts.

4. `RedisPubSubLogger` assumes non-null metadata in success paths.
   - `logPublish`, `logReceive`, and `logForward` call `envelope.metadata()` without guarding.
   - File: `common/common-redis/src/main/java/com/example/common/redis/observability/RedisPubSubLogger.java`.

## 4. Inconsistencies

### Package structure

- `common-events` package root is `com.example.common.event` for generic event model and `com.example.common.integration.*` for payloads. That is acceptable, but the split is not reflected in class responsibilities: `EventType` tries to be a global catalog while each domain package also has its own event enum.
- `common-kafka` source now uses `com.example.common.kafka.*`, but service code imports missing `com.example.common.integration.kafka.event.*`.
- Deleted old Kafka event wrapper files under `common/common-kafka/src/main/java/com/example/common/kafka/event/*` are still conceptually referenced by services through renamed imports.
- `common-redis-cache` uses the same `com.example.common.redis.*` root as `common-redis`; cache and Pub/Sub cannot be distinguished from imports.
- `common.realtime.policy` is in `common-web`, not `common-events` or a realtime/integration common module.

### Class naming

- `EventEnvelope` is generic and transport-agnostic.
- `RedisMessage` is transport-specific but still carries domain event metadata and payload, duplicating `EventEnvelope`.
- `NotificationEvent` in `common-events` is named like an envelope/event but is actually a notification payload DTO.
- `FriendRequestEvent` in `common-events` is a payload DTO, not an envelope; it is later wrapped by missing `FriendRequestKafkaEvent`.
- `KafkaEventProducer` vs `RedisEventPublisher` use different nouns for the same role.
- `KafkaPubSubException` and `RedisPubSubException` are equivalent, but Kafka puts it under `error` and Redis under `exception`.

### Interface naming

- `IRedisMessage`, `IRedisPubSubLogger`, `ITimeRedisCache`, `ITimeRedisCacheManager`, and `IKafkaEventLogger` use an `I` prefix.
- `KafkaEventProducer`, `RedisEventPublisher`, `KafkaEventRegistry`, `RedisEventRegistry`, `KafkaEventHandler`, and `RedisEventSubscriber` do not.
- `RedisEventSubscriber.onEvent` is only a default alias for `onMessage`; it does not add behavior.
- `KafkaEventConsumer.handlers()` is not used by `KafkaAutoConfiguration`; only `KafkaEventHandler` beans are collected directly.

### Event naming

- Account:
  - `EventType.ACCOUNT_CREATED`: `account.created`
  - `AccountEventType.ACCOUNT_CREATED`: `account.account.created`
  - `KafkaTopics.ACCOUNT_CREATED`: `account.account.created`
- Friendship:
  - `EventType.FRIENDSHIP_REQUEST_SENT`: `friendship.request.sent`
  - `FriendshipEventType.FRIEND_REQUEST_SENT`: `friend.request.sent`
  - WebSocket-facing friendship constants use `friendship.request.*` in service code.
- Presence:
  - `PresenceEventType` uses hyphenated canonical names and includes deprecated underscore aliases.
  - `EventType` includes only a subset and misses `presence.user.heartbeat`, `presence.room.join`, and `presence.room.leave`.
- Chat:
  - `ChatEventType` includes `chat.message.pinned`, `chat.message.unpinned`, and room membership events.
  - `EventType` does not include those chat event names.
- Notification:
  - `NotificationEventType` only has `notification.requested`.
  - `EventType` also has `notification.sent`.

### Topic/channel naming

- `KafkaTopics.ACCOUNT_CREATED`, `CHAT_MESSAGE_SENT`, `CHAT_MESSAGE_EDITED`, `CHAT_MESSAGE_DELETED`, `CHAT_REACTION_UPDATED`, `NOTIFICATION_REQUESTED`, and `NOTIFICATION_SENT` are event-type-like topic names.
- `KafkaTopics.FRIENDSHIP_EVENTS` and `KafkaTopics.FRIENDSHIP_REQUEST_EVENTS` are stream-like topic names.
- `KafkaTopics.DEAD_LETTER` and `KafkaTopics.RETRY` are operational/system names.
- This means `KafkaTopics` is simultaneously topic catalog, event type catalog, and operational routing catalog.
- `RedisChannels` uses `realtime.*` names and hardcodes chat, notification, and presence channels in the transport module.
- `RedisContractVersions` declares version constants for chat, notification, and presence fanout but is not referenced anywhere.

### Serializer/deserializer naming

- Redis has `RedisEventSerializer` with both `serialize(IRedisMessage)` and `serialize(EventEnvelope<?>)`.
- Redis has one concrete serializer, `JsonRedisEventSerializer`.
- Kafka has `KafkaEventSerializer` and `KafkaEventDeserializer`, but no JSON implementation and no producer/listener wiring.
- Redis uses `RedisMessageFields` for `eventType` and `payload`; Kafka has no equivalent shared field constants.

### Publisher/subscriber/consumer/handler naming

- Kafka publishing: `KafkaEventProducer.publish`.
- Redis publishing: `RedisEventPublisher.publish`.
- Kafka handling: `KafkaEventHandler.handle(EventEnvelope<T>)`.
- Redis handling: `RedisEventSubscriber.onMessage(T extends IRedisMessage)`.
- Kafka service listeners are direct `@KafkaListener` methods and do not use the common dispatcher.
- Redis service listeners are mostly routed through `RedisEventListener` and `RedisEventDispatcher`, except notification Redis Pub/Sub, which bypasses the common Redis layer.

### Folder organization

- `common-kafka` folders are transport-oriented but incomplete: `producer`, `consumer`, `serialization`, `registry`, `topic`, `logging`, `flow`, `error`, `config`.
- `common-redis` folders are transport-oriented but still carry a DTO model: `api`, `message`, `publisher`, `subscriber`, `listener`, `dispatcher`, `serialization`, `registry`, `channel`, `observability`, `flow`, `exception`, `constants`, `config`.
- `common-redis-cache` has `api`, `core`, `exception`, but those packages collide with Pub/Sub packages.
- There is no `pubsub` package boundary even though `common-redis` is effectively a Redis Pub/Sub module.

### Abstraction style

- Kafka is moving to envelope-first architecture.
- Redis remains message-first internally.
- Common-event has an unused generic registry abstraction.
- Kafka and Redis each own their own registry instead of sharing or extending the common one.
- Transport routing contexts duplicate metadata extraction instead of using `EventMetadata` directly plus transport route fields.

## 5. Violations

### Duplicate responsibility

- `common/common-events/src/main/java/com/example/common/event/registry/EventRegistry.java`
  - Duplicates `KafkaEventRegistry` and `RedisEventRegistry`.
  - Currently appears only in tests and docs, not in the transport implementations.

- `common/common-kafka/src/main/java/com/example/common/kafka/registry/KafkaEventRegistry.java`
  - Duplicates the same event type to payload class mapping as `RedisEventRegistry`.

- `common/common-redis/src/main/java/com/example/common/redis/registry/RedisEventRegistry.java`
  - Duplicates the same event type to payload class mapping as `KafkaEventRegistry`, but with fewer methods.

- `common/common-events/src/main/java/com/example/common/event/EventType.java`
  - Duplicates domain-specific enums such as `ChatEventType`, `FriendshipEventType`, `PresenceEventType`, `AccountEventType`, and `NotificationEventType`.

- `common/common-kafka/src/main/java/com/example/common/kafka/topic/KafkaTopics.java`
  - Duplicates event names from `EventType` and domain enums while also claiming to be a topic catalog.

- `common/common-redis/src/main/java/com/example/common/redis/message/RedisMessage.java`
  - Duplicates `EventEnvelope` by carrying event identity, event type, source service, correlation id, creation time, and payload.

### Misplaced files/classes

- `common/common-web/src/main/java/com/example/common/realtime/policy/RealtimeFlowClassificationPolicy.java`
  - Event/realtime delivery policy, not web infrastructure.
  - Move to `common-events` under `com.example.common.realtime.policy` or a dedicated `common-realtime-contracts` module.

- `common/common-web/src/main/java/com/example/common/realtime/policy/RealtimeFlowId.java`
  - Same issue.

- `common/common-web/src/main/java/com/example/common/realtime/policy/RealtimeFlowType.java`
  - Same issue.

- `common/common-redis/src/main/java/com/example/common/redis/config/RedisContractVersions.java`
  - Version contract constants are not Redis configuration.
  - It is also unused.

- `common/common-kafka/src/main/java/com/example/common/kafka/topic/KafkaTopics.java`
  - If it contains real Kafka topics, it belongs in `common-kafka`.
  - The event-type constants inside it should move to the event contract catalog or be removed in favor of domain event enums.

- `common/common-events/src/main/java/com/example/common/integration/notification/NotificationEvent.java`
  - This looks like an event envelope but is a domain payload shape and appears unused.
  - Rename to a payload-specific name if kept, otherwise remove.

### Transport concerns leaking into `common-events`

- `common/common-events/src/main/java/com/example/common/event/EventMetadata.java`
  - No hard dependency leak, but Javadoc frames the model around Kafka/Redis.
  - Metadata itself is fine; docs should describe event identity independent of transport.

- `common/common-events/src/main/java/com/example/common/event/registry/EventRegistry.java`
  - The abstraction exists because deserializers need payload resolution.
  - Its Javadoc explicitly names Kafka and Redis, but transport modules do not use it.
  - Either promote it to the one true `EventPayloadRegistry` used by all transports, or remove it from `common-events`.

### Event contracts coupled to Kafka or Redis too early

- `KafkaTopics.ACCOUNT_CREATED = "account.account.created"` couples account event naming to Kafka topic naming.
- `KafkaTopics.CHAT_MESSAGE_SENT = "chat.message.sent"` makes the topic indistinguishable from event type.
- `RedisChannels` hardcodes chat, notification, and presence realtime channel names in the Redis transport module. This is acceptable only if those channels are considered shared transport contracts; otherwise move channel ownership closer to each service's realtime adapter.
- Missing `com.example.common.integration.kafka.event.*` wrappers show the previous design coupled event contracts to Kafka too early. The cleaner replacement is `EventEnvelope<T>` plus payload DTOs, not transport-specific wrapper classes.

### Should be removed, merged, or moved

- Remove after migration: `IRedisMessage`, `AbstractRedisMessage`, `RedisMessage`.
- Merge into one shared registry or remove unused base: `EventRegistry`, `KafkaEventRegistry`, `RedisEventRegistry`.
- Remove unless implemented and wired: `KafkaEventSerializer`, `KafkaEventDeserializer`.
- Remove unless used: `RedisContractVersions`, `RedisMessageFields`.
- Move from `common-web`: `RealtimeFlowClassificationPolicy`, `RealtimeFlowId`, `RealtimeFlowType`.
- Normalize or remove: `AccountEventType`, `UserEventType`, `NotificationEventType` if `EventType` remains global.

## 6. Dead Code / Deprecated / Removable

Appears deprecated and should be migrated away:
- `common/common-redis/src/main/java/com/example/common/redis/api/IRedisMessage.java`
  - Marked `@Deprecated(forRemoval = false, since = "2.0")`.
  - Still anchors Redis listener, dispatcher, subscriber, serializer deserialization, and service subscriber types.

Replaceable compatibility model:
- `common/common-redis/src/main/java/com/example/common/redis/message/AbstractRedisMessage.java`
- `common/common-redis/src/main/java/com/example/common/redis/message/RedisMessage.java`
  - Replace with `EventEnvelope<T>` everywhere.

Unused or redundant abstractions:
- `common/common-events/src/main/java/com/example/common/event/registry/EventRegistry.java`
  - Referenced by test import only. Not implemented by Kafka or Redis registries.

- `common/common-kafka/src/main/java/com/example/common/kafka/serialization/KafkaEventSerializer.java`
- `common/common-kafka/src/main/java/com/example/common/kafka/serialization/KafkaEventDeserializer.java`
  - No implementation and no wiring in producer/consumer flow.

- `common/common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventConsumer.java`
  - Marker/grouping interface is optional and not used by the auto-configuration.
  - If handler dispatch is desired, `KafkaEventHandler` plus `KafkaEventDispatcher` is enough.

- `common/common-redis/src/main/java/com/example/common/redis/config/RedisContractVersions.java`
  - No external references found.

- `common/common-redis/src/main/java/com/example/common/redis/constants/RedisMessageFields.java`
  - Only used by `JsonRedisEventSerializer`.
  - If envelope JSON becomes canonical, these flat field constants should disappear.

Unused or likely obsolete event DTOs/enums:
- `common/common-events/src/main/java/com/example/common/integration/user/UserPresencePayload.java`
  - No references found outside its own file.
  - Overlaps with `common.integration.presence.*`.

- `common/common-events/src/main/java/com/example/common/integration/user/UserEventType.java`
  - No production references found.

- `common/common-events/src/main/java/com/example/common/integration/notification/NotificationEvent.java`
  - No references found outside its own file.
  - Overlaps with notification payload/event concepts elsewhere.

- `common/common-events/src/main/java/com/example/common/integration/account/AccountEventType.java`
  - No production references found; its values also conflict with `EventType`.

Deleted too early:
- Old Kafka wrapper classes under `common/common-kafka/src/main/java/com/example/common/kafka/event/*` are deleted, but services still reference replacement package names that do not exist.
- Do not restore these as the target architecture. Either restore temporarily to unblock compile, or migrate all services to `EventEnvelope<T>` directly.

## 7. Module-by-Module Review

### common-event

What belongs here:
- `EventEnvelope<T>`.
- `EventMetadata`.
- Event name validation rules.
- Domain payload DTOs used across services.
- A single event name catalog strategy.
- A single payload registry abstraction, if runtime payload resolution is part of the shared contract.

What should not be here:
- Kafka wrapper events such as `ChatMessageSentEvent` or `FriendRequestKafkaEvent`.
- Redis message DTOs.
- Transport topic/channel constants.
- WebSocket protocol DTOs.
- Realtime flow policy should not be in `common-web`; it can live here only if this module is treated as the system integration contract module.

Whether contracts are too broad or too narrow:
- Too broad: `EventType` tries to be a global event catalog but is incomplete and conflicts with domain enums.
- Too narrow: domain-specific enums are closer to actual service usage, but they are isolated and not connected to a single validator/registry.
- Too broad: `ChatMessagePayload` includes notification fanout fields such as `recipientUserIds`, `mentionedUserIds`, `senderDisplayName`, `preview`, `isDirect`, and reply metadata. This may be acceptable for a cross-service integration event, but it is no longer a pure chat domain event.
- Too narrow: `NotificationRequestedPayload` has only five mutable fields and does not follow the immutable/Jackson style of other payloads.
- Misleading: `FriendRequestEvent` and `NotificationEvent` are payloads, not envelope event classes.

Verdict:
- `common-events` is mostly transport-agnostic at import level.
- It leaks transport concerns through docs and through the unused `EventRegistry` abstraction.
- Its main architectural issue is not Kafka/Redis leakage. The main issue is inconsistent event modeling and naming.

### common-kafka

Whether producer/consumer abstractions are clean:
- `KafkaEventProducer.publish(String topic, String key, EventEnvelope<?> envelope)` is the right direction.
- `DefaultKafkaEventProducer` should not validate Kafka topic names using event type validation.
- Blocking on `.join()` makes the producer synchronous despite using `KafkaTemplate.send`. That may be intentional, but the interface does not make the delivery behavior explicit.
- `KafkaEventDispatcher` is clean enough as a simple dispatch-by-event-type helper, but it is unused by current service consumers.
- `KafkaEventConsumer` is unnecessary unless the auto-configuration actually reads `consumer.handlers()`.

Whether naming and packaging are consistent:
- Better than Redis overall, but still inconsistent:
  - `logging` vs Redis `observability`.
  - `error` vs Redis `exception`.
  - `producer` vs Redis `publisher`.
  - `KafkaTopics` mixes topic and event naming.

Whether it overlaps too much with event/common contracts:
- Yes, through `KafkaTopics` and `KafkaEventRegistry`.
- Topic ownership belongs in Kafka, but event type ownership belongs in `common-events`.
- If a registry remains, Kafka should use a shared `EventPayloadRegistry` interface instead of owning a duplicate contract.

Standard pattern to keep:
- Keep the Kafka side envelope-first.
- Standardize Redis to match it.
- Do not reintroduce Kafka-specific event wrapper DTOs as the long-term model.

### redis / redis-cache / redis-pubsub

Whether responsibilities are clearly separated:
- At module level, cache and Pub/Sub are separate folders/modules.
- At package level, they are not separated because both use `com.example.common.redis.*`.
- `common-redis` is really `redis-pubsub`, but package names do not say `pubsub`.
- `common-redis-cache` is unrelated to event flow but depends on `common-events`.

Whether Pub/Sub abstractions are clean:
- The desired pieces are present: publisher, serializer, registry, listener, dispatcher, subscriber, logger, channel catalog.
- The model is not clean because it is split between `EventEnvelope` and deprecated `IRedisMessage`.
- The serializer contract is especially problematic because it accepts both models but deserializes only one.
- The listener logs using envelopes but dispatches Redis messages, so common-event is only half-adopted.

Whether Redis cache and Redis Pub/Sub are mixed incorrectly:
- Runtime code is not mixed.
- Packaging and dependencies are mixed:
  - `common-redis-cache` should use `com.example.common.redis.cache.*`.
  - `common-redis-cache` should not depend on `common-events`.
  - Both modules should not expose unrelated classes under the same `com.example.common.redis.api` package.

## 8. Recommended Target Structure

### common-event / common-events

Recommended package structure:

```text
common-events
  com.example.common.event.model
    EventEnvelope
    EventMetadata

  com.example.common.event.validation
    EventNameValidator
    EventContractValidator

  com.example.common.event.registry
    EventPayloadRegistry
    DefaultEventPayloadRegistry

  com.example.common.event.type
    EventName
    ChatEventNames
    AccountEventNames
    FriendshipEventNames
    NotificationEventNames
    PresenceEventNames

  com.example.common.integration.account
    AccountCreatedPayload

  com.example.common.integration.chat
    ChatMessagePayload
    MessageUpdatedPayload
    MessageDeletedPayload
    ReactionPayload
    AttachmentPayload
    MessageBlockPayload
    RoomInvitePayload

  com.example.common.integration.friendship
    FriendshipPayload
    FriendRequestPayload

  com.example.common.integration.notification
    NotificationRequestedPayload

  com.example.common.integration.presence
    PresenceUserOnlinePayload
    PresenceUserOfflinePayload
    PresenceUserStatePayload
    PresenceTypingPayload
    PresenceStopTypingPayload
    PresenceRoomJoinPayload
    PresenceRoomLeavePayload
    RoomOnlineUsersPayload
    GlobalOnlineUsersPayload
    PresenceStatus
    PresenceMode

  com.example.common.realtime.policy
    RealtimeFlowId
    RealtimeFlowType
    RealtimeFlowClassificationPolicy
```

Naming rule:
- Pick one canonical source for event names.
- Recommended: keep domain event name classes/enums and remove the incomplete global `EventType`, or generate `EventType` from the domain catalogs.
- Use these canonical values:
  - Account: `account.created`, `account.deleted`, `account.disabled`.
  - Friendship: `friendship.request.sent`, `friendship.request.accepted`, `friendship.request.declined`, `friendship.request.cancelled`, `friendship.unfriended`, `friendship.blocked`, `friendship.unblocked`.
  - Chat: keep `chat.message.*`, `chat.reaction.updated`, and add the pinned/unpinned/member events already used by services.
  - Presence: keep hyphenated names; accept underscore aliases only at external boundaries.

### common-kafka

Recommended package structure:

```text
common-kafka
  com.example.common.kafka.config
    KafkaAutoConfiguration

  com.example.common.kafka.publisher
    KafkaEventPublisher
    DefaultKafkaEventPublisher

  com.example.common.kafka.listener
    KafkaEnvelopeListenerAdapter

  com.example.common.kafka.handler
    KafkaEventHandler
    KafkaEventDispatcher

  com.example.common.kafka.topic
    KafkaTopics

  com.example.common.kafka.serialization
    JsonKafkaEventSerializer
    JsonKafkaEventDeserializer

  com.example.common.kafka.observability
    KafkaEventLogger
    KafkaEventLogContext

  com.example.common.kafka.exception
    KafkaEventPublishException
    KafkaEventDeserializeException
```

Topic rule:
- Do not make topics equal event names by default.
- Prefer stream topics:
  - `account.events`
  - `chat.events`
  - `friendship.events`
  - `notification.events`
  - `presence.events` only if durable presence is ever needed
  - `system.dead-letter`
  - `system.retry`
- Keep event type in `EventMetadata.eventType`.
- If the project intentionally wants one topic per event type, keep that explicitly, but do not reuse `EventContractValidator` for topic validation.

### common-redis

Recommended package structure if Pub/Sub and cache remain one module:

```text
common-redis
  com.example.common.redis.pubsub.config
    RedisPubSubAutoConfiguration

  com.example.common.redis.pubsub.publisher
    RedisEventPublisher
    DefaultRedisEventPublisher

  com.example.common.redis.pubsub.listener
    RedisEventListener

  com.example.common.redis.pubsub.handler
    RedisEventHandler
    RedisEventDispatcher

  com.example.common.redis.pubsub.channel
    RedisChannels

  com.example.common.redis.pubsub.serialization
    JsonRedisEventSerializer

  com.example.common.redis.pubsub.observability
    RedisPubSubLogger
    RedisPubSubLogContext

  com.example.common.redis.pubsub.exception
    RedisPubSubException

  com.example.common.redis.cache
    TimeRedisCache
    TimeRedisCacheManager
    TimeRedisCacheManagerBuilder
    TimeRedisCacheException
```

Recommended package structure if cache stays separate:

```text
common-redis
  com.example.common.redis.pubsub.*

common-redis-cache
  com.example.common.redis.cache.*
```

Redis Pub/Sub model rule:
- Use `EventEnvelope<T>` as the only wire model.
- Delete `IRedisMessage`, `AbstractRedisMessage`, and `RedisMessage` after migration.
- `JsonRedisEventSerializer.deserialize` must read the same envelope JSON that `serialize` writes.
- Subscriber contracts should receive `EventEnvelope<T>` or `T payload + EventMetadata`, not `IRedisMessage`.

## 9. Refactor Plan

1. Stop the bleeding by choosing one event wire model.
   - Standardize on `EventEnvelope<T>`.
   - Treat Kafka-specific wrapper events and Redis-specific message DTOs as compatibility-only.

2. Restore compileability before deeper cleanup.
   - Either temporarily restore the deleted Kafka wrapper classes, or immediately migrate all service imports from `com.example.common.integration.kafka.event.*` to `EventEnvelope<T>`.
   - Fix Redis publisher call sites so they pass `EventEnvelope<?>`, not `RedisMessage`.

3. Fix Redis serializer symmetry.
   - Make `JsonRedisEventSerializer.serialize(EventEnvelope<?>)` and `deserialize(String)` use the same JSON shape.
   - Read `metadata.eventType`, not only top-level `eventType`.
   - Keep legacy flat `eventType` / `type` support only as an explicit compatibility branch.

4. Unify event name ownership.
   - Pick domain-specific event enums/classes as the source of truth, or make `EventType` complete and remove domain-specific event enums.
   - Recommended: keep domain event catalogs and remove the incomplete global `EventType`.
   - Normalize account and friendship names first because those are currently contradictory.

5. Separate Kafka topic names from event names.
   - Replace per-event topic constants with stream constants if acceptable.
   - At minimum, rename constants so the distinction is explicit.
   - Remove topic validation from `DefaultKafkaEventProducer`.

6. Collapse registry abstractions.
   - Create one `EventPayloadRegistry` in `common-events`.
   - Make Redis and Kafka adapters depend on that interface, or delete the common registry if runtime deserialization is fully service-local.
   - Remove `KafkaEventRegistry` and `RedisEventRegistry` duplicates after migration.

7. Make Redis match Kafka's architectural style.
   - Replace `RedisEventSubscriber<T extends IRedisMessage>` with `RedisEventHandler<T>` that handles `EventEnvelope<T>`.
   - Replace `RedisEventDispatcher.dispatch(IRedisMessage)` with `dispatch(EventEnvelope<?>)`.
   - Delete `IRedisMessage`, `AbstractRedisMessage`, and `RedisMessage`.

8. Move realtime policy out of `common-web`.
   - Move `RealtimeFlowClassificationPolicy`, `RealtimeFlowId`, and `RealtimeFlowType` to `common-events` or a dedicated `common-realtime-contracts`.
   - Remove non-existent references to `common.integration.contract` and `common.integration.realtime`, or create the package deliberately in the chosen contract module.

9. Fix module packaging.
   - Rename cache packages to `com.example.common.redis.cache.*`.
   - Remove `common-events` dependency from `common-redis-cache`.
   - Rename `logging`/`observability` and `error`/`exception` consistently across Kafka and Redis.

10. Decide on auto-configuration strategy.
   - If these modules are meant to self-register, add Boot auto-configuration imports.
   - If services should wire them manually, remove misleading auto-configuration classes.
   - Do not leave dead auto-config classes that look active but are not discoverable.

11. Delete dead wrappers and compatibility APIs.
   - Remove `KafkaEventSerializer` and `KafkaEventDeserializer` unless concrete JSON implementations are added.
   - Remove `KafkaEventConsumer` unless the dispatcher actually consumes its `handlers()`.
   - Remove `RedisContractVersions`, `RedisMessageFields`, `UserPresencePayload`, and unused notification/account event DTOs after verifying production references.

12. Add contract tests around actual wire shapes.
   - One test should publish an envelope through the Kafka producer serializer path and consume it as an envelope.
   - One test should serialize a Redis envelope and deserialize the same JSON through `RedisEventListener`.
   - One test should assert canonical event names for all domain catalogs.

## 10. Final Verdict

What should stay:
- `EventEnvelope` and `EventMetadata`.
- Domain payload DTOs that are actively used: chat payloads, friendship payloads, presence payloads, account created payload, notification requested payload.
- `KafkaEventProducer` concept, but rename to match chosen convention if desired.
- `RedisEventPublisher`, `RedisEventListener`, `RedisEventDispatcher`, and `RedisEventSubscriber` concepts, but migrate them to `EventEnvelope`.
- `RedisChannels` and `KafkaTopics` only as transport route catalogs, not event name catalogs.
- `TimeRedisCache` and `TimeRedisCacheManager`, but under a cache-specific package.

What should move:
- `RealtimeFlowClassificationPolicy`, `RealtimeFlowId`, and `RealtimeFlowType` should move out of `common-web`.
- Cache classes should move from `com.example.common.redis.api/core/exception` to `com.example.common.redis.cache.*`.
- Event version constants, if still needed, should move to a contract/version package, not Redis config.

What should merge:
- `EventRegistry`, `KafkaEventRegistry`, and `RedisEventRegistry` should merge into one shared `EventPayloadRegistry`, or the common `EventRegistry` should be removed.
- Kafka/Redis dispatch patterns should converge on handler contracts that consume `EventEnvelope<T>`.
- Event naming should merge into one canonical catalog strategy.

What should be deleted:
- Long term: `IRedisMessage`, `AbstractRedisMessage`, and `RedisMessage`.
- `KafkaEventSerializer` and `KafkaEventDeserializer` unless implemented and wired.
- `KafkaEventConsumer` unless the marker/grouping behavior is actually used.
- `RedisContractVersions` if no runtime or contract tests use it.
- `RedisMessageFields` after Redis envelope format becomes canonical.
- `UserPresencePayload`, `UserEventType`, `NotificationEvent`, and `AccountEventType` if they remain unused or conflict with the chosen event catalog.

Whether `common.integration.contract` or similar packages should remain:
- `common.integration.contract` should not remain as a vague catch-all package.
- The current source tree does not contain `com.example.common.integration.contract` or `com.example.common.integration.realtime`, but tests/services reference similar packages.
- Replace vague contract packages with explicit ownership:
  - `com.example.common.event.model`
  - `com.example.common.event.type`
  - `com.example.common.integration.<domain>`
  - `com.example.common.realtime.policy`
- Do not create `common.integration.kafka.event`. Kafka-specific event wrappers are the wrong long-term abstraction. Use `EventEnvelope<T>` plus domain payloads and transport route metadata instead.

Overall verdict:
- The target should be envelope-first everywhere.
- Kafka is closer to the target pattern.
- Redis should change to match Kafka, not the other way around.
- Event naming needs to be fixed before more wrappers or registries are added.
- The current structure is not consistent enough to preserve; it should be simplified around shared event contracts plus thin Kafka/Redis adapters.
