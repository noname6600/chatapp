# Common Messaging Review

## 1. Scope Reviewed

Reviewed only the current working-tree source under:

- `chatappBE/common/common-events`
- `chatappBE/common/common-kafka`
- `chatappBE/common/common-redis`

Limited external checks were used only to confirm dependency direction and actual usage of common abstractions:

- `chatappBE/chat-service/src/main/java/com/example/chat/config/ChatRedisEventConfig.java`
- `chatappBE/presence-service/src/main/java/com/example/presence/configuration/PresenceRedisRegistryConfig.java`
- repository-wide `rg` usage scans for common Kafka/Redis registry, dispatcher, legacy alias, and payload usage

Verification run:

- `.\gradlew.bat :common:common-events:test :common:common-kafka:test :common:common-redis:test`
- Result: `BUILD SUCCESSFUL`

Important working-tree note: many old Redis/Kafka classes are currently deleted in git status and the envelope-based replacements are untracked. This review treats the current working tree as the code under review and calls out old deleted classes that should stay deleted.

## 2. Current Design Summary

`common-events` is the intended contract layer. It owns:

- `com.example.common.event.Event`
- `com.example.common.event.EventEnvelope<T>`
- `com.example.common.event.EventMetadata`
- `com.example.common.event.EventPayloadRegistry`
- `com.example.common.event.DefaultEventPayloadRegistry`
- `com.example.common.event.validation.EventContractValidator`
- domain event type enums under `com.example.common.integration.*`
- shared payload DTOs under `com.example.common.integration.*`

`common-kafka` is a transport module. It owns:

- publishing API: `KafkaEventProducer`, `KafkaEventPublisher`, `DefaultKafkaEventProducer`
- consumer dispatch API: `KafkaEventHandler`, `KafkaEventDispatcher`
- routing context: `KafkaEventRoutingContext`
- observer/logger: `KafkaEventObserver`, `Slf4jKafkaEventLogger`
- registry wrappers: `KafkaEventRegistry`, `DefaultKafkaEventRegistry`
- topic constants: `KafkaTopics`
- Spring auto config: `KafkaAutoConfiguration`

`common-redis` is a Redis pub/sub transport module. It owns:

- publishing API: `RedisEventPublisher`, `DefaultRedisEventPublisher`
- listener and dispatch flow: `RedisEventListener`, `RedisEventDispatcher`, `RedisEventHandler`
- serializer/deserializer: `RedisEventSerializer`, `JsonRedisEventSerializer`
- registry wrappers: `RedisEventRegistry`, `DefaultRedisEventRegistry`
- channel constants: `RedisChannels`
- routing context: `RedisEventRoutingContext`
- observer/logger: `RedisPubSubObserver`, `Slf4jRedisPubSubLogger`
- Spring auto config: `RedisAutoConfiguration`

Current flow:

- Kafka publish: service builds `EventEnvelope<?>` -> `DefaultKafkaEventProducer.publish(topic, key, envelope)` -> validates envelope metadata -> `KafkaTemplate.send(topic, key, envelope)` -> logs publish or wraps failure in `KafkaPubSubException`.
- Kafka consume: common module provides `KafkaEventDispatcher`, but current usage scans show services mostly use direct `@KafkaListener` methods and do not implement `KafkaEventHandler`.
- Redis publish: service builds `EventEnvelope<?>` -> `DefaultRedisEventPublisher.publish(channel, envelope)` -> validates envelope metadata -> `JsonRedisEventSerializer.serialize(envelope)` -> `StringRedisTemplate.convertAndSend(channel, payload)` -> logs publish or wraps failure in `RedisPubSubException`.
- Redis consume: service config wires a `RedisMessageListenerContainer` -> common `RedisEventListener` -> `JsonRedisEventSerializer.deserialize(payload)` using `RedisEventRegistry` -> `RedisEventDispatcher.dispatch(envelope)` -> `RedisEventHandler.onEvent(envelope)`.

## 3. Problems Found

### High

- `common-events` is not complete enough as the shared contract layer. `ChatEventType.MESSAGE_PINNED` and `ChatEventType.MESSAGE_UNPINNED` exist in `com.example.common.integration.chat.ChatEventType`, but no matching payload exists in `common-events`. Confirmed usage maps those event types to service-local `com.example.chat.modules.room.dto.RoomMessagePinEventPayload` in `ChatRedisEventConfig`. A common event type must not require a service-owned payload class to deserialize a shared event.

- `KafkaTopics` duplicates and partially contradicts `common-events` event type enums. Examples: `KafkaTopics.TOPIC_CHAT_MESSAGE_SENT = "chat.message.sent"` duplicates `ChatEventType.MESSAGE_SENT.value()`, and `KafkaTopics.TOPIC_ACCOUNT_CREATED = "account.account.created"` duplicates `AccountEventType.ACCOUNT_CREATED.value()`. But `KafkaTopics.TOPIC_FRIENDSHIP_EVENTS = "friendship.events"` and `TOPIC_FRIENDSHIP_REQUEST_EVENTS = "friendship.request.events"` are route names, not event types. This mixes semantic event names and transport route names in one class.

- Account event naming is inconsistent. `AccountEventType.ACCOUNT_CREATED` uses `account.account.created`, while `SharedEventModelContractTest` and registry tests use `account.created`. The validator accepts both patterns, but `EventContractValidator.isRegisteredEventType("account.created")` would be false. Pick one canonical event name. Recommended target: `account.created`, `account.deleted`, `account.disabled`.

- `FriendRequestEvent` is misplaced and misnamed. It lives in `com.example.common.integration.friendship`, but it is a payload carried inside `EventEnvelope`, not an envelope event. It also carries `FriendRequestEvent.Type`, duplicating `FriendshipEventType` and allowing contradictory state, for example envelope event type `friend.request.accepted` with payload type `SENT`. Rename to `FriendRequestPayload` and remove the nested `Type`, or split into explicit payloads.

- Redis dispatch failures are logged as deserialization failures. `RedisEventListener.onMessage` wraps `serializer.deserialize`, `logger.logReceive`, and `dispatcher.dispatch` in one `try/catch`; any handler failure is reported through `logDeserializeError(channel, payload, ex)`. This hides real subscriber failures and makes operations/debugging misleading.

- `NotificationRequestedPayload` is not safely deserializable through the shared Redis deserializer. It has `@AllArgsConstructor` only, mutable fields, no no-args constructor, and no `@JsonCreator`. `JsonRedisEventSerializer.deserialize` uses `mapper.treeToValue(payloadNode, payloadClass)`, so this payload does not follow the same Jackson contract as the final immutable payload classes.

- Kafka exposes `KafkaEventRegistry` and auto-configures `DefaultKafkaEventRegistry`, but the registry is not used by any Kafka serializer, deserializer, dispatcher, or producer in `common-kafka`. It is a dead abstraction today. Redis needs a registry because it owns deserialization; Kafka currently does not.

### Medium

- `RedisEventRegistry` and `KafkaEventRegistry` duplicate `EventPayloadRegistry` without adding transport-specific behavior. They create separate payload catalogs per transport even though payload resolution is a contract-layer concern. If a registry is needed, it should be one shared `EventPayloadRegistry` / `EventCatalog` in `common-events`.

- `EventContractValidator` mixes three responsibilities: event name syntax, metadata required-field validation, and registered-event lookup across all domain enums. It also has inconsistent APIs: static throwing methods, instance boolean-returning methods that throw, and `isValidEventType(null)` throwing `NullPointerException`.

- `EventMetadata` and `EventEnvelope` allow invalid objects. Validation is only performed in Redis/Kafka publishers, not in constructors or factories. Consumers can deserialize invalid metadata unless transport code explicitly validates it.

- `DefaultEventPayloadRegistry.register` uses `containsKey` followed by `put`, which is not atomic. Duplicate registration can slip through under concurrent startup. Use `putIfAbsent`.

- Observability contracts are inconsistent. `RedisPubSubObserver` has publish, receive, forward, error, and deserialize-error methods. `KafkaEventObserver` has publish and error only. `KafkaEventDispatcher` does not report handler success/failure. `RedisPubSubObserver.logForward` is unused.

- `Slf4jKafkaEventLogger`, `Slf4jRedisPubSubLogger`, and `DefaultRedisEventRegistry` are annotated with `@Component`, while the modules also provide beans from auto-configuration. In apps that component-scan `com.example`, this can create duplicate or order-dependent beans. Library defaults should be auto-configuration beans, not component-scanned classes.

- Publisher error messages use the wrong lifecycle wording. `DefaultKafkaEventProducer` throws `KafkaPubSubException(..., "Failed at Kafka lifecycle stage DISPATCH", ...)` from the producer path. `DefaultRedisEventPublisher` throws `"Failed at Redis lifecycle stage DISPATCH"` from publish. Use `VALIDATE`, `SERIALIZE`, `PUBLISH`, or `SEND` instead.

- Kafka serialization/deserialization is not defined in `common-kafka`. Redis owns explicit envelope serialization through `RedisEventSerializer`; Kafka relies on external Spring Kafka configuration. That may be acceptable, but then `KafkaEventRegistry` should not exist. If common Kafka owns event deserialization, it needs a real serializer/deserializer that uses the shared registry.

- `KafkaEventProducer` and `KafkaEventPublisher` are unnecessary two-layer aliases. The comment says `KafkaEventProducer` is a backward-compatible alias, but `KafkaEventPublisher extends KafkaEventProducer`, so the naming direction is muddy. Keep one canonical interface.

- `KafkaPubSubException` uses Redis-style `PubSub` terminology. Kafka code should use `KafkaMessagingException`, `KafkaEventPublishException`, or a specific exception hierarchy.

- Payload DTO style in `common-events` is inconsistent. Most payloads are final immutable classes with `@JsonCreator`, `@JsonProperty`, `@JsonIgnoreProperties`, and sometimes `@Builder`; `UserPresencePayload` and `NotificationRequestedPayload` are mutable Lombok DTOs; `AccountCreatedPayload` is immutable but lacks `@JsonIgnoreProperties`.

- `NotificationEventType` only contains `REQUESTED`, while `common-events` also has `NotificationCreatedPayload` and deprecated `NotificationEvent`, and `KafkaTopics` has `TOPIC_NOTIFICATION_SENT`. The notification contract is incomplete and uses three names for related concepts: requested, created, sent.

- `PresenceEventType` contains compatibility alias logic for underscored names even though `EventContractValidator` rejects underscores. The compatibility layer is understandable during migration, but it should have a removal target and should not remain as part of the clean contract.

### Low

- `RedisPubSubObserver` default methods dereference `context.channel()` without null checks, while logger implementations are otherwise null-safe.

- `JsonRedisEventSerializer` throws `RedisPubSubException` with channel `"unknown"` because the serializer does not know the channel. Channel context belongs in the listener or publisher, not inside the serializer exception.

- `DefaultRedisEventPublisher` formatting is slightly inconsistent around the wrapped exception constructor call.

- `KafkaEventDispatcher` Javadoc references old names `RedisMessageDispatcher` and `IRedisSubscriber`; those classes are no longer part of the current common Redis source.

- `GlobalOnlineUsersPayload` imports `AllArgsConstructor` and `NoArgsConstructor` but does not use them. `FriendshipPayload` and `RoomOnlineUsersPayload` use wildcard Lombok imports.

- Common Kafka and Redis tests contain mojibake in comment separators. This does not affect runtime, but it is avoidable churn in shared modules.

## 4. Inconsistencies Between Redis, Kafka, and Events

- Package shape is similar but not equivalent. Kafka has `topic`; Redis has `channel`. Kafka has no serialization package; Redis has `serialization`. Both have `registry`, but only Redis uses it.

- Interface naming differs for the same concept. Kafka uses `KafkaEventHandler.handle(EventEnvelope<T>)`; Redis uses `RedisEventHandler.onEvent(EventEnvelope<T>)`. If these remain separate, method names should still match.

- Publisher naming differs. Redis has one interface, `RedisEventPublisher`. Kafka has two, `KafkaEventProducer` and `KafkaEventPublisher`, with one extending the other.

- Event names are owned in more than one place. Semantic names live in `common-events` enums and are duplicated as Kafka topic strings in `KafkaTopics`. Redis channel strings correctly remain transport route names and do not duplicate event type values.

- Redis has explicit deserialization with `JsonRedisEventSerializer` plus `RedisEventRegistry`; Kafka does not have an equivalent common deserializer. This is a real design choice, but the package structure pretends both transports have registries.

- Redis observes receive and deserialize failures; Kafka observes only publish and publish errors. Kafka consumer/dispatcher flow has no comparable observability.

- Redis listener is common and transport-owned; Kafka listener infrastructure is service-owned. This makes `KafkaEventDispatcher` optional and currently mostly unused, while Redis dispatch is part of the expected common path.

- Redis channel constants are transport names such as `realtime.chat.room.*`; Kafka topic constants sometimes are semantic event names such as `chat.message.sent` and sometimes aggregate routes such as `friendship.events`.

- Exception names differ semantically. Redis `RedisPubSubException` fits Redis pub/sub. Kafka `KafkaPubSubException` imports Redis terminology into Kafka.

- Spring configuration is inconsistent. Redis uses `@ConditionalOnClass(StringRedisTemplate.class)`; Kafka auto-configuration has no equivalent `@ConditionalOnClass(KafkaTemplate.class)`.

## 5. Dependency Direction Review

Build-level direction is mostly correct:

- `common-kafka` declares `api project(':common:common-events')`.
- `common-redis` declares `api project(':common:common-events')`.
- `common-events` does not import `common-kafka`, `common-redis`, Spring Kafka, or Spring Redis.

Architectural direction is weaker than the build graph:

- `common-events` is clean from direct transport imports, but its `EventPayloadRegistry` Javadoc names `RedisEventRegistry` and `KafkaEventRegistry`. That is documentation coupling, not compile coupling, but the contract layer should not describe itself through transport wrappers.

- `common-events` does not define a complete event catalog mapping event type to payload class. Because of that, Redis services must manually register payload types and one chat event maps to a chat-service class outside `common-events`.

- `common-kafka` leaks contract ownership by duplicating semantic event names in `KafkaTopics`. Transport route constants belong in Kafka; semantic event names belong only in `common-events`.

- Redis depends on common events correctly for envelope, metadata, payload registry, and validator. The leak is not dependency direction; it is duplicated registry wrappers and transport-specific registry ownership.

- Kafka depends on common events correctly for envelope and metadata, but its registry layer is currently ornamental. Either Kafka should own a real envelope serde, or the Kafka registry classes should be removed.

Verdict on dependency direction: the compile-time direction is clean, but `common-events` is too weak as the contract layer and `common-kafka` currently owns too much semantic naming.

## 6. Misplaced / Deprecated / Duplicate Code

Misplaced:

- `com.example.common.integration.friendship.FriendRequestEvent`: should be `FriendRequestPayload`, or split by event type. It is not an envelope event.
- `com.example.common.integration.user.UserPresencePayload`: presence payload in the `user` package, with no apparent match to `UserEventType.PROFILE_CREATED` or `PROFILE_UPDATED`. Move to `presence` if still needed, otherwise delete.
- `KafkaTopics.TOPIC_*` constants whose values equal event types: the transport package should not duplicate event type constants.
- `com.example.common.kafka.registry.KafkaEventRegistry` and `DefaultKafkaEventRegistry`: misplaced unless Kafka owns a common deserializer.
- Service-local `RoomMessagePinEventPayload` used for common event types: move a shared `MessagePinnedPayload` / `MessagePinPayload` into `common-events`.

Deprecated or legacy:

- `com.example.common.integration.notification.NotificationEvent` is explicitly deprecated and duplicates `NotificationCreatedPayload`.
- Deprecated aliases in `KafkaTopics`: `ACCOUNT_CREATED`, `CHAT_MESSAGE_SENT`, `NOTIFICATION_REQUESTED`, etc. All should be removed after usage migrates to canonical route constants or after topic routing is redesigned.
- `PresenceEventType.DEPRECATED_ALIASES`, `LEGACY_BY_NORMALIZED`, `normalize`, `isDeprecatedAlias`, and `legacyAliasOf` preserve underscored event names rejected by the validator.
- Working-tree deleted Kafka classes should stay deleted: `common-kafka/src/main/java/com/example/common/kafka/api/KafkaEvent.java`, `common-kafka/src/main/java/com/example/common/kafka/api/KafkaEventPublisher.java`, `common-kafka/src/main/java/com/example/common/kafka/core/DefaultKafkaEventPublisher.java`, and all typed classes under `common-kafka/src/main/java/com/example/common/kafka/event/`.
- Working-tree deleted Redis classes should stay deleted: `IRedisMessage`, `IRedisPublisher`, `IRedisSubscriber`, `RedisMessage`, `AbstractRedisMessage`, `RedisMessageDispatcher`, `DefaultRedisMessageListener`, `DefaultRedisPublisher`, `DefaultRedisMessageRegistry`, `IRedisMessageRegistry`, `IRedisMessageSerializer`, `JsonRedisMessageSerializer`, `IRedisPubSubLogger`, and `RedisPubSubLogger`.
- Working-tree deleted `common-events/src/main/java/com/example/common/integration/websocket/WsEvent.java` should stay deleted; WebSocket protocol belongs in `common-websocket`.

Duplicate:

- `NotificationEvent` and `NotificationCreatedPayload` have the same fields and nested enum.
- `KafkaEventRegistry` and `RedisEventRegistry` duplicate `EventPayloadRegistry`.
- `DefaultKafkaEventRegistry` and `DefaultRedisEventRegistry` duplicate `DefaultEventPayloadRegistry`.
- `KafkaEventProducer` and `KafkaEventPublisher` duplicate the same outbound contract.
- `DefaultKafkaEventProducer` and `DefaultRedisEventPublisher` duplicate metadata validation logic.
- `KafkaEventDispatcher` and `RedisEventDispatcher` duplicate event-type map dispatch logic with only method-name differences.

## 7. Recommended Target Structure

`common-events` should own only transport-independent contracts:

- `EventEnvelope<T>`
- `EventMetadata`
- `EventType` / event type enums or a typed event catalog
- `EventPayloadRegistry` or preferably `EventCatalog` mapping event type -> payload class
- `EventContractValidator`, split into name validation, metadata validation, and catalog validation
- every payload used by a shared event type
- shared enums used inside payloads

`common-events` should not own:

- Kafka topics
- Redis channels
- listener/container abstractions
- WebSocket event protocol
- transport retry/dead-letter concepts, unless represented as semantic business events

`common-kafka` should own only Kafka transport concerns:

- Kafka route constants if routes are stable transport infrastructure
- one outbound interface: `KafkaEventPublisher`
- implementation: `DefaultKafkaEventPublisher`
- optional `KafkaEventDispatcher` only if services will actually use handler-based dispatch
- Kafka observer/logger for publish and consume paths
- Kafka serializer/deserializer only if the module commits to owning envelope serde

`common-kafka` should not own:

- semantic event type names
- payload class registry wrappers unless used by a Kafka serde
- per-domain typed event wrapper classes
- Redis-style `PubSub` naming

`common-redis` should own only Redis pub/sub transport concerns:

- Redis channel constants and channel builders
- `RedisEventPublisher`
- `RedisEventListener`
- `RedisEventSerializer` if Redis continues to use `StringRedisTemplate`
- `RedisEventDispatcher` only if the module owns listener-to-handler dispatch
- Redis observer/logger

`common-redis` should not own:

- generic event payload catalogs as Redis-specific registries
- old `IRedis*` interfaces
- old `RedisMessage` envelope classes
- WebSocket forwarding semantics in observer methods

Unify:

- Use one shared `EventPayloadRegistry` / `EventCatalog`; delete transport registry wrappers unless a transport adds behavior.
- Align handler method names: use `handle(EventEnvelope<T>)` for both Kafka and Redis.
- Align observer concepts: publish success/failure, receive success/failure, dispatch success/failure, deserialize failure where applicable.
- Align publisher validation through a shared helper or `EventEnvelope` factory.
- Keep route names transport-specific and event names contract-specific.

## 8. Concrete Refactor Actions

### Keep

- Keep `com.example.common.event.EventEnvelope<T>`.
- Keep `com.example.common.event.EventMetadata`, but add validation through constructor/factory or a mandatory builder.
- Keep `com.example.common.event.EventPayloadRegistry` if a runtime registry is still needed.
- Keep `com.example.common.event.DefaultEventPayloadRegistry`, but change duplicate registration to atomic `putIfAbsent`.
- Keep `com.example.common.redis.channel.RedisChannels`.
- Keep `com.example.common.redis.publisher.RedisEventPublisher`.
- Keep `com.example.common.redis.publisher.DefaultRedisEventPublisher`, after fixing stage names and shared validation.
- Keep `com.example.common.redis.serialization.RedisEventSerializer` and `JsonRedisEventSerializer` if Redis stays string-based.
- Keep `com.example.common.redis.listener.RedisEventListener`, after separating deserialize failure from dispatch failure.
- Keep `com.example.common.kafka.producer.DefaultKafkaEventProducer` behavior, but rename it if the canonical interface becomes `KafkaEventPublisher`.
- Keep the current common module tests, but clean encoding artifacts and expand contract coverage.

### Move

- Move the payload for `ChatEventType.MESSAGE_PINNED` and `MESSAGE_UNPINNED` into `common-events`, for example `com.example.common.integration.chat.MessagePinPayload`.
- Move or delete `com.example.common.integration.user.UserPresencePayload`; if retained, move it to `com.example.common.integration.presence`.
- Move semantic event-to-payload mapping into `common-events`, for example an `EventCatalog` that registers `ChatEventType.MESSAGE_SENT -> ChatMessagePayload.class`.
- Move duplicate metadata validation in `DefaultKafkaEventProducer` and `DefaultRedisEventPublisher` into `common-events`.
- Move any semantic constant currently represented only in `KafkaTopics` into the relevant event enum, especially notification sent/created.

### Merge

- Merge `KafkaEventProducer` and `KafkaEventPublisher` into one interface. Recommended name: `KafkaEventPublisher`.
- Merge `KafkaEventRegistry` and `RedisEventRegistry` into `EventPayloadRegistry` / `EventCatalog`.
- Merge `DefaultKafkaEventRegistry` and `DefaultRedisEventRegistry` into `DefaultEventPayloadRegistry` if no transport behavior is added.
- Merge duplicate dispatcher mechanics in `KafkaEventDispatcher` and `RedisEventDispatcher` behind a shared event-handler map helper, or at minimum align names and error behavior.
- Merge `NotificationEvent.NotificationType` and `NotificationCreatedPayload.NotificationType` into one shared enum or keep it only on the surviving payload.

### Rename

- Rename `DefaultKafkaEventProducer` to `DefaultKafkaEventPublisher`.
- Rename `KafkaPubSubException` to `KafkaMessagingException` or `KafkaEventPublishException`.
- Rename `RedisPubSubObserver` to `RedisEventObserver` if the rest of the module uses `Event` naming.
- Rename `Slf4jRedisPubSubLogger` to `Slf4jRedisEventLogger` if `RedisPubSubObserver` is renamed.
- Rename `RedisEventHandler.onEvent` to `handle`.
- Rename `FriendRequestEvent` to `FriendRequestPayload`.
- Rename account event values from `account.account.*` to `account.*`, with an explicit migration/alias window if deployed producers already use the old names.
- Rename notification event values so the enum and payloads agree, for example `notification.requested` and `notification.created` or `notification.sent`, not all three without clear semantics.

### Delete

- Delete `com.example.common.integration.notification.NotificationEvent` after all producers/consumers use `NotificationCreatedPayload`.
- Delete `com.example.common.kafka.registry.KafkaEventRegistry` unless a Kafka deserializer starts using it.
- Delete `com.example.common.kafka.registry.DefaultKafkaEventRegistry` unless a Kafka deserializer starts using it.
- Delete deprecated alias constants in `KafkaTopics` after migration: `ACCOUNT_CREATED`, `ACCOUNT_DELETED`, `ACCOUNT_DISABLED`, `USER_PROFILE_CREATED`, `USER_PROFILE_UPDATED`, `FRIENDSHIP_EVENTS`, `FRIENDSHIP_REQUEST_EVENTS`, `CHAT_MESSAGE_SENT`, `CHAT_MESSAGE_EDITED`, `CHAT_MESSAGE_DELETED`, `CHAT_REACTION_UPDATED`, `NOTIFICATION_REQUESTED`, `NOTIFICATION_SENT`, `DEAD_LETTER`, `RETRY`.
- Delete or time-box `PresenceEventType` legacy alias APIs: `DEPRECATED_ALIASES`, `LEGACY_BY_NORMALIZED`, `isDeprecatedAlias`, and `legacyAliasOf`.
- Delete `RedisPubSubObserver.logForward` unless a common Redis component actually calls it.
- Keep deleted old Kafka files deleted:
  - `common-kafka/src/main/java/com/example/common/integration/kafka/KafkaTopics.java`
  - `common-kafka/src/main/java/com/example/common/kafka/api/KafkaEvent.java`
  - `common-kafka/src/main/java/com/example/common/kafka/api/KafkaEventPublisher.java`
  - `common-kafka/src/main/java/com/example/common/kafka/core/DefaultKafkaEventPublisher.java`
  - `common-kafka/src/main/java/com/example/common/kafka/event/AbstractKafkaEvent.java`
  - `common-kafka/src/main/java/com/example/common/kafka/event/AccountCreatedEvent.java`
  - `common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageDeletedEvent.java`
  - `common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageEditedEvent.java`
  - `common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageSentEvent.java`
  - `common-kafka/src/main/java/com/example/common/kafka/event/ChatReactionUpdatedEvent.java`
  - `common-kafka/src/main/java/com/example/common/kafka/event/FriendRequestKafkaEvent.java`
  - `common-kafka/src/main/java/com/example/common/kafka/event/FriendshipEvent.java`
  - `common-kafka/src/main/java/com/example/common/kafka/event/NotificationRequestedEvent.java`
  - `common-kafka/src/main/java/com/example/common/kafka/observability/KafkaEventLogger.java`
- Keep deleted old Redis files deleted:
  - `common-redis/src/main/java/com/example/common/redis/api/IRedisMessage.java`
  - `common-redis/src/main/java/com/example/common/redis/api/IRedisPublisher.java`
  - `common-redis/src/main/java/com/example/common/redis/api/IRedisSubscriber.java`
  - `common-redis/src/main/java/com/example/common/redis/autoconfigure/RedisAutoConfiguration.java`
  - `common-redis/src/main/java/com/example/common/redis/constants/RedisMessageFields.java`
  - `common-redis/src/main/java/com/example/common/redis/dispatcher/RedisMessageDispatcher.java`
  - `common-redis/src/main/java/com/example/common/redis/listener/DefaultRedisMessageListener.java`
  - `common-redis/src/main/java/com/example/common/redis/message/AbstractRedisMessage.java`
  - `common-redis/src/main/java/com/example/common/redis/message/RedisMessage.java`
  - `common-redis/src/main/java/com/example/common/redis/observability/IRedisPubSubLogger.java`
  - `common-redis/src/main/java/com/example/common/redis/observability/RedisPubSubLogger.java`
  - `common-redis/src/main/java/com/example/common/redis/publisher/DefaultRedisPublisher.java`
  - `common-redis/src/main/java/com/example/common/redis/registry/DefaultRedisMessageRegistry.java`
  - `common-redis/src/main/java/com/example/common/redis/registry/IRedisMessageRegistry.java`
  - `common-redis/src/main/java/com/example/common/redis/serialization/IRedisMessageSerializer.java`
  - `common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisMessageSerializer.java`
- Keep deleted old event file deleted:
  - `common-events/src/main/java/com/example/common/integration/websocket/WsEvent.java`

## 9. Final Verdict

The direction is better than the old typed-wrapper model: `EventEnvelope`, `EventMetadata`, and transport modules depending on `common-events` are the right foundation.

It is not clean enough yet.

The biggest architectural issue is that `common-events` is not strong enough to be the source of truth. It owns event names and many payloads, but not all payloads, not a complete event catalog, and not consistent DTO construction rules. That weakness leaks outward: Redis needs per-service manual payload registration, Kafka duplicates event names as topic constants, and deprecated/legacy aliases remain in the common surface.

Target state should be:

- one contract layer in `common-events`
- one event name source
- one payload catalog
- transport-only Kafka topics
- transport-only Redis channels
- no old `RedisMessage` / typed Kafka event wrappers
- no duplicate transport registries
- one Kafka publisher interface
- aligned Redis/Kafka dispatch, error, and observability behavior

Strict verdict: keep the new envelope-based direction, but do another cleanup pass before treating these modules as the stable common messaging architecture.
