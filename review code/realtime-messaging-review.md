## 1. Summary

The current realtime/messaging architecture has the right ingredients, but the boundaries are not clean enough for production scale. Kafka is used for durable cross-service events, Redis pub/sub is used for local realtime fanout, Redis cache is used for query/state acceleration, `common-events` stores shared payloads, and WebSocket handlers deliver client-facing realtime messages. That direction is reasonable.

The implementation is not structurally consistent. Kafka event wrappers live in `common-kafka` but use `com.example.common.integration.kafka.event` packages; Redis cache and Redis pub/sub share the `com.example.common.redis` namespace; WebSocket envelopes are split between `WsEvent` and `WsOutgoingMessage`; services sometimes publish Kafka, sometimes Redis pub/sub, and sometimes WebSocket directly from application services.

Naming and structure are only partially aligned. Chat and presence use dot-case event names such as `chat.message.sent` and `presence.user.online`, while notification and friendship WebSocket events use screaming snake case such as `NOTIFICATION_NEW` and `FRIEND_REQUEST_RECEIVED`. Redis channels are also inconsistent: chat/presence use `ws.*` prefixes, notification uses `notification:*`.

The biggest architectural issue is responsibility leakage. Some service/application classes directly perform cache eviction, Redis state mutation, WebSocket broadcasting, and event publication. The realtime flow is therefore coupled to transport details and harder to reason about, test, replay, or scale horizontally.

## 2. Findings by Area

### Kafka

Current responsibilities:

- `common-kafka` provides `KafkaEvent`, `KafkaEventPublisher`, `DefaultKafkaEventPublisher`, `KafkaTopics`, and transport-specific Kafka event wrappers.
- `auth-service`, `chat-service`, `friendship-service`, and `notification-service` publish and consume domain events.
- Chat uses Kafka as the durable event bus, then consumes its own events back into Redis pub/sub for WebSocket fanout.
- Notification consumes chat/friendship/account Kafka events to create notification records.

Problems:

- `common-kafka` mixes infrastructure and shared business contracts. Files under `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/*` are not Kafka plumbing only; they are domain event contracts such as `ChatMessageSentEvent`, `FriendshipEvent`, and `NotificationRequestedEvent`.
- Kafka event wrappers are transport-specific duplicates around payloads already stored in `common-events`. Example: `ChatMessageSentEvent` wraps `ChatMessagePayload`; `FriendshipEvent` wraps `FriendshipPayload`; `NotificationRequestedEvent` wraps `NotificationRequestedPayload`.
- Some consumers exist only as legacy/empty paths. `notification-service/.../kafka/ChatMessageEventConsumer.java` listens to `KafkaTopics.CHAT_MESSAGE_SENT` and intentionally ignores it because `MessageCreatedEventConsumer` does the real work.
- Topic usage is inconsistent. `MessageCreatedEventConsumer.java` hardcodes `@KafkaListener(topics = "chat.message.sent", groupId = "notification-service")`, while other consumers use `KafkaTopics.CHAT_MESSAGE_SENT`.
- Consumer group IDs are inconsistent or missing. Some listeners specify explicit groups, while others rely on defaults: `FriendshipEventConsumer`, `FriendshipRequestEventConsumer`, `AccountCreatedEventConsumer`, and `ChatMessageEventConsumer`.
- Error handling and retry/DLQ policy are not standardized. `KafkaTopics.DEAD_LETTER` and `KafkaTopics.RETRY` exist, but the inspected consumers do not use a consistent retry/dead-letter abstraction.
- `DefaultKafkaEventPublisher.publish(...)` is asynchronous through `KafkaTemplate.send(...).whenComplete(...)`, but the API returns `void`, so callers cannot observe failure or decide whether publication is part of command success.

Naming issues:

- `FriendRequestKafkaEvent` includes the transport name in the domain event class, but `FriendshipEvent`, `ChatMessageSentEvent`, and `NotificationRequestedEvent` do not.
- `KafkaConfiguration`, `KafkaConsumerConfig`, and `KafkaAutoConfiguration` are all used for related setup with different naming conventions.
- `KafkaPubSubException` is a confusing name. Kafka is not used as "pub/sub" in this codebase; it is the durable event log/broker.
- Kafka event package names do not match directory names. Files are under `common-kafka/.../com/example/common/kafka/event`, but declare `package com.example.common.integration.kafka.event`.

Structural issues:

- `KafkaTopics` lives in `common-kafka` but declares `package com.example.common.integration.kafka`. Topic contracts are integration contracts, not Kafka publisher infrastructure.
- `KafkaChatMessageEventPublisher` is in `chat.modules.message.infrastructure.kafka`, but it queries `RoomMemberRepository` and `RoomRepository` to compute recipients and direct-room state. That is application/domain enrichment, not pure Kafka transport.
- `FriendshipEventProducer` calls `UserClient` to enrich sender display name before publishing. That couples a Kafka publisher to cross-service HTTP enrichment.
- `notification-service/.../kafka/MessageCreatedEventConsumer.java` performs notification policy, repository checks, idempotency, and command execution directly inside the transport consumer.

Optimization issues:

- Chat message delivery takes a multi-hop path: command -> Kafka -> chat-service Kafka consumer -> Redis pub/sub -> WebSocket. For same-service realtime fanout this is expensive unless the explicit goal is "only publish after Kafka accepts the event."
- `KafkaChatMessageEventConsumer` consumes the same service's message event only to republish to Redis. This is a valid outbox/fanout pattern if deliberate, but it needs documented ordering/idempotency guarantees and consistent consumer groups.
- No central idempotency strategy is visible for Kafka consumers. Redis has `RealtimeEventDedupeGuard` for some realtime events, but Kafka consumers that create durable records rely on ad hoc repository checks.

### Redis Cache

Current responsibilities:

- `common-redis-cache` provides `TimeRedisCache`, `TimeRedisCacheManager`, `ITimeRedisCache`, and `ITimeRedisCacheManager`.
- User, chat room, and presence services use Redis cache/state for profile lookup, room lists, presence TTL state, and room/user membership sets.
- Chat also uses Redis atomics for message sequence generation through `RedisMessageSequenceService`.

Problems:

- Redis cache and Redis pub/sub share `com.example.common.redis.*` packages even though they are separate concerns.
- `common-redis-cache` classes live under `com.example.common.redis.api`, `com.example.common.redis.core`, and `com.example.common.redis.exception`, which makes them look like part of `common-redis` pub/sub.
- `PresenceService` mixes cache, Redis set state, and pub/sub publishing in one service.
- `RoomService` and `RoomQueryService` use `ITimeRedisCacheManager` directly, so business services know Redis-specific TTL/cache details.
- `UserProfileService` uses a cache object directly through `ITimeRedisCache`, while chat services mostly use `ITimeRedisCacheManager`. The abstraction usage is not consistent.
- `MessageCacheService` is not annotated with `@Service` even though it imports `org.springframework.stereotype.Service`; it is likely dead or manually instantiated code.

Naming issues:

- `ITimeRedisCache` and `ITimeRedisCacheManager` are awkward names. The concern is TTL cache, not "time Redis".
- `CreateCacheException` sounds like a construction failure, but it is used for operational cache access/eviction failures.
- `CacheNames.PROFILES` lives in chat message infrastructure but names a user/profile cache.
- Cache key styles vary: `user-profile`, `rooms:user:{id}`, `presence::user:{id}`, `chat:room:seq:{id}`, `room:messages:{id}`.

Structural issues:

- `common-redis-cache` should not share the same root package as Redis pub/sub. Suggested package: `com.example.common.cache.redis`.
- `RedisAtomicConfig` exists separately from Redis cache and Redis pub/sub config, but still sits in generic `chat.config`.
- Presence uses Redis as both cache and primary distributed state store. Those responsibilities should be explicit: `presence.infrastructure.cache` versus `presence.infrastructure.state.redis`.
- `MessageCacheService` belongs either in `chat.modules.message.infrastructure.cache` as a real bean with usage, or should be deleted.

Misuse of caching:

- Presence online/room membership sets are not cache in the usual sense; they are distributed ephemeral state. They should not be discussed or abstracted as `ITimeRedisCacheManager`.
- Cache eviction logic is embedded in domain/application services instead of behind cache ports such as `RoomCache`, `UserProfileCache`, or `PresenceStateStore`.
- Room cache eviction is manual and scattered. `RoomService` evicts per user in several methods, making it easy to miss invalidation paths.

Optimization issues:

- `RoomQueryService.roomsOfUser(...)` caches a `List` and then casts entries back to `RoomResponse`; with JSON Redis serializers this can become fragile depending on serializer behavior.
- `RedisMessageSequenceService.nextSeq(...)` performs `get`, maybe `setIfAbsent`, then `increment`. It is acceptable, but a Lua script or atomic initialization strategy would be cleaner under cold concurrent rooms.
- `TimeRedisCacheManager.cacheAvailable` is global and mutable; one cache failure can mark the whole manager unavailable without a clear recovery policy.

### Redis Pub/Sub

Current responsibilities:

- `common-redis` provides a generic Redis pub/sub message envelope (`RedisMessage<T>`), serializer, registry, dispatcher, listener, and publisher.
- Chat and presence use this shared abstraction.
- Notification bypasses the shared abstraction and implements its own Redis pub/sub format inside `notification.websocket.redis`.

Problems:

- Notification duplicates Redis pub/sub infrastructure instead of using `IRedisPublisher`, `IRedisSubscriber`, `RedisMessage`, `DefaultRedisMessageListener`, and `RedisMessageDispatcher`.
- Chat/presence dispatch by `eventType`, while notification dispatches by channel/user ID and deserializes directly into `WsOutgoingMessage`.
- Redis pub/sub messages are sometimes integration events and sometimes already-final WebSocket messages. Notification publishes `WsOutgoingMessage` directly into Redis, while chat/presence publish `RedisMessage<Payload>` and convert to WebSocket in subscribers.
- The common Redis listener dispatches only by event type and ignores channel semantics except for logging. This makes per-room/per-user channel patterns external to the subscriber contract.
- `RedisMessageDispatcher` allows only one subscriber per `eventType`, which blocks legitimate multi-handler cases unless every service defines unique event types.

Naming issues:

- `DefaultRedisPublisher` and `IRedisPublisher` are too broad; they are Redis pub/sub publishers, not general Redis publishers.
- `IRedisMessage` uses `getMessageId()`, while Kafka uses `getEventId()`. For cross-transport tracing, one consistent field name should be chosen.
- `PresenceRedisRegistryConfig` uses `register()`, while `ChatRedisEventConfig` uses `registerEvents()`.
- `ChatRedisChannels.CHAT_ROOM = "ws.chat.room."`; `PresenceRedisChannels.PRESENCE_ROOM = "ws.presence.room."`; `NotificationRedisChannels.NOTIFICATION_USER_PREFIX = "notification:"`. Prefix strategy is inconsistent.

Structural issues:

- `notification.websocket.redis.RedisNotificationPublisher` and `RedisNotificationSubscriber` place Redis infrastructure under a WebSocket package, which mixes transport layers.
- `PresenceRedisPublisher` and all presence subscribers are directly under `com.example.presence.redis`, while chat puts Redis publishing under `modules.message.infrastructure.redis` and subscribers under `realtime.subscriber`.
- Channel constants are service-local, which is fine, but naming and pattern construction should be standardized.

Event delivery issues:

- Redis pub/sub has no persistence. Using it for WebSocket fanout is fine, but not for durable business events. Notification currently publishes already-final WebSocket messages through Redis; if no instance is listening, the client update is lost.
- Chat has `RealtimeEventDedupeGuard`, but not all subscriber flows shown use a shared dedupe policy. Presence and notification do not appear to have equivalent dedupe/ordering guards.
- Notification channel-to-user parsing couples delivery identity to the channel string. Chat/presence keep identity in the payload and channel.

Optimization issues:

- One class per Redis subscriber (`ChatMessageSentRedisSubscriber`, `ChatMessageEditedRedisSubscriber`, `UserOnlineSubscriber`, etc.) is readable but repetitive. A standard event-to-broadcaster router could reduce duplication.
- Redis listener container config is repeated in chat, presence, and notification instead of having a common configurable listener-container factory.
- Notification serializes/deserializes WebSocket envelopes manually despite common Redis serialization existing.

### common-event

Current responsibilities:

- `common-events` contains integration payloads/enums for account, chat, friendship, notification, presence, user, and websocket.
- It is used as the shared model catalog for Kafka payloads, Redis pub/sub payloads, and WebSocket payloads.

Problems:

- `common-events` is not purely generic. It contains domain-specific event payloads, client transport envelope `WsEvent`, and enums used directly by frontend protocols.
- `WsEvent` should not live in `common-events`. It is a WebSocket transport envelope, not a cross-service integration event contract.
- Domain contracts and client realtime contracts are blurred. Example: `ChatEventType.MESSAGE_SENT` is used as Kafka event type, Redis pub/sub event type, and WebSocket client event type.
- Some contracts are likely unused or overlapping: `NotificationEvent`, `NotificationRequestedPayload`, and service-local notification DTOs overlap conceptually.
- `FriendRequestEvent` contains sender display name, which is presentation/enrichment data, not just an integration fact.

Naming issues:

- Module is named `common-events`, package is `com.example.common.integration.*`, but it also contains `integration.websocket.WsEvent`.
- `FriendRequestEvent` and `FriendshipEventType` coexist with Kafka wrapper `FriendRequestKafkaEvent` and `FriendshipEvent`, creating a confusing event hierarchy.
- `NotificationEventType` only contains `REQUESTED`, but notification WebSocket types are constants in `NotificationWebSocketPublisher`, not the shared enum.
- Presence uses `ROOM_TYPING` in backend but frontend aliases it as `USER_TYPING`.

Structural issues:

- Shared contracts should be split by purpose: durable integration contracts, realtime client contracts, and transport envelopes.
- `common-events/integration/enums` holds chat-domain enums (`ReactionAction`, `MessageType`, `AttachmentType`) outside the chat package. That may be acceptable for integration payloads, but the package name should communicate "shared chat contract enums".
- `common-events/integration/user/UserPresencePayload.java` overlaps with `integration.presence.*`.

Genericity violations:

- `common-events/integration/websocket/WsEvent.java` is infrastructure/transport-specific.
- `common-events/integration/notification/NotificationEvent.java` includes client-facing notification presentation fields such as `senderName` and `preview`.
- `FriendRequestEvent.senderDisplayName` is presentation enrichment inside a shared event fact.

Infrastructure coupling:

- `WsEvent` couples the common event contract module to WebSocket delivery shape.
- Kafka wrapper events are outside `common-events`, but use `com.example.common.integration.kafka.event` package names from inside `common-kafka`, creating a fake integration namespace inside an infrastructure module.

Domain coupling:

- The module is heavily domain-specific. That is acceptable if it is renamed/conceptualized as `common-contracts`, but not if it is expected to be a generic event library.

### WebSocket

Current responsibilities:

- `common-websocket` provides handshake/JWT support, `WsOutgoingMessage`, broadcaster interfaces, and `AbstractWebSocketBroadcaster`.
- Chat, notification, presence, and friendship each define their own WebSocket handler, session registry, and broadcaster classes.
- Chat and presence support client commands over WebSocket.
- Notification and friendship primarily push server-side updates to connected users.

Problems:

- There are two shared WebSocket envelopes: `common-events/.../websocket/WsEvent` and `common-websocket/.../dto/WsOutgoingMessage`.
- `WsEvent` and `WsOutgoingMessage` both contain `type` and `payload`, and both use `@JsonAlias("data")`. This does not standardize output; `@JsonAlias` helps deserialization, not serialization.
- Frontend notification expects `{ type, data }` in `NotificationWsEvent`, while backend `WsOutgoingMessage` serializes `{ type, payload }`. This is a likely runtime contract mismatch.
- Session registry and broadcaster implementations are duplicated across chat, presence, notification, and friendship.
- Application services sometimes call WebSocket broadcasters directly. Example: `RoomService` sends room member events via `IRoomBroadcaster`.
- Presence WebSocket handler directly calls `PresenceRedisPublisher` for typing events and `PresenceService` for state changes, mixing command handling, state management, pub/sub, and direct snapshot delivery.

Naming issues:

- `WsEvent` versus `WsOutgoingMessage` is not a meaningful distinction.
- `FriendshipWebSocketPublisher` is not a publisher in the same sense as Kafka/Redis publishers; it is a WebSocket delivery adapter/router.
- `WebSocketFriendshipBroadcaster`, `WebSocketUserBroadcaster`, `WebSocketRoomBroadcaster`, and `WebSocketGlobalBroadcaster` are named by implementation technology rather than role.
- WebSocket configs are named `WebSocketConfig` in chat/notification/presence, but `FriendshipWebSocketConfig` in friendship.

Structural issues:

- Notification Redis pub/sub classes are placed inside `notification.websocket.redis`, mixing Redis infrastructure under WebSocket transport.
- Chat uses `chat.realtime.websocket.*` and `chat.realtime.subscriber.*`, while notification/friendship use top-level `websocket` and `kafka` packages. The same concerns are arranged differently per service.
- `common-websocket.dto.WsOutgoingMessage` should be under `common.websocket.protocol` or `common.websocket.message`, not `dto`.

Realtime delivery issues:

- Chat WebSocket room membership is per instance. Redis pub/sub fans out to all service instances, which is correct, but room membership state itself is local and must be treated as instance-local.
- Direct WebSocket broadcasts from services such as `RoomService` only reach the current instance unless they go through Redis pub/sub. This is a major scaling risk.
- Presence sends snapshots directly with hardcoded strings `presence.global.snapshot` and `presence.room.snapshot`, while other presence events use `PresenceEventType`.
- Notification delivery goes through Redis user channels, but because it sends final WebSocket envelopes, it cannot be reused for non-WebSocket consumers.

Optimization issues:

- WebSocket send is synchronized per session in `AbstractWebSocketBroadcaster`, which is safe for basic usage, but there is no send queue/backpressure/drop policy.
- `AbstractWebSocketBroadcaster.handleDeadSession(...)` closes sessions but does not tell service registries to unregister them, so stale registry entries may remain until explicit cleanup.
- Four service-specific registries duplicate the same maps and lifecycle behavior.

## 3. Naming Review

Inconsistent class names:

- `WsEvent` and `WsOutgoingMessage` represent the same envelope concept.
- `KafkaConfiguration`, `KafkaConsumerConfig`, and `KafkaAutoConfiguration` use three naming styles for Kafka setup.
- `RedisNotificationPublisher` bypasses `IRedisPublisher` but has an almost identical responsibility.
- `PresenceRedisPublisher` and `ChatRedisPublisher` publish Redis pub/sub events, while `NotificationWebSocketPublisher` publishes WebSocket messages through Redis.
- `FriendRequestEvent`, `FriendRequestKafkaEvent`, and `FriendshipEvent` overlap in meaning.
- `MessageCreatedEventConsumer` consumes `ChatMessageSentEvent`; the class name says "created" while the topic/event says "sent".
- `ChatMessageEventConsumer` is a legacy no-op next to `MessageCreatedEventConsumer`.

Inconsistent package names:

- `common-kafka/.../com/example/common/kafka/event/*` files declare `com.example.common.integration.kafka.event`.
- `common-redis-cache` uses `com.example.common.redis.*`, same as `common-redis`.
- Chat uses `com.example.chat.realtime.websocket` and `com.example.chat.realtime.subscriber`; notification uses `com.example.notification.websocket.redis`; presence uses `com.example.presence.redis` and `com.example.presence.websocket`.
- Kafka lives under top-level `kafka` in some services and under `modules.message.infrastructure.kafka` in chat.
- Redis pub/sub subscribers live under `realtime.subscriber` in chat, `redis` in presence, and `websocket.redis` in notification.

Inconsistent event names:

- Chat: dot-case (`chat.message.sent`, `chat.room.member.joined`).
- Presence: dot-case with underscores in final segment (`presence.user.status_changed`, `presence.room.online_users`).
- Notification WebSocket: screaming snake case (`NOTIFICATION_NEW`, `UNREAD_COUNT_UPDATE`).
- Friendship WebSocket: screaming snake case (`FRIEND_REQUEST_RECEIVED`, `FRIEND_STATUS_CHANGED`).
- Kafka `FriendRequestKafkaEvent` uses `eventType = "friend.request.event"` instead of a value from `FriendshipEventType`.

Inconsistent topic/channel names:

- Kafka topics are mostly dot-case and centralized in `KafkaTopics`, but `MessageCreatedEventConsumer` hardcodes `"chat.message.sent"`.
- Chat Redis channels use `ws.chat.room.{roomId}`.
- Presence Redis channels use `ws.presence.user`, `ws.presence.room.{roomId}`, `ws.presence.global`.
- Notification Redis channels use `notification:{userId}`.
- Redis channel names mix WebSocket intent (`ws.*`) with service intent (`notification:*`).

Inconsistent method/service naming:

- `PresenceRedisPublisher.online(...)`, `offline(...)`, `typing(...)` use action names; `ChatRedisPublisher.publishMessageSent(...)` uses publish-event names.
- `IRedisSubscriber.eventType()` returns an event name, while Redis delivery is channel-based underneath.
- `NotificationWebSocketPublisher.publishNotificationNew(...)` and `publishUnreadCountUpdate(...)` are final client delivery methods, not event publication methods.

Suggested unified naming strategy:

- Use `*Event` only for durable or shared semantic events.
- Use `*Payload` for data inside an event/envelope.
- Use `*Envelope` for transport wrappers: `IntegrationEventEnvelope<T>`, `RedisPubSubEnvelope<T>`, `WebSocketEnvelope<T>`.
- Use dot-case event names everywhere for backend and frontend realtime contracts.
- Use `KafkaTopicNames`, `RedisChannelNames`, and `WebSocketEventTypes` as separate constant catalogs.
- Rename `IRedisPublisher` to `RedisPubSubPublisher` and `IRedisSubscriber` to `RedisPubSubHandler`.
- Rename `WsEvent`/`WsOutgoingMessage` to one `WebSocketMessage<T>` or `WebSocketEnvelope<T>`.
- Avoid `I` prefixes in Java interfaces unless the entire codebase standardizes on them.

## 4. Folder / Package Structure Review

Misplaced files/classes:

- `common-kafka/src/main/java/com/example/common/kafka/event/*`: domain-specific integration event wrappers are in an infrastructure module and path/package mismatch.
- `common-kafka/src/main/java/com/example/common/integration/kafka/KafkaTopics.java`: integration topic contract lives inside `common-kafka`, not a contracts module.
- `common-events/src/main/java/com/example/common/integration/websocket/WsEvent.java`: WebSocket transport envelope is in integration events.
- `notification-service/src/main/java/com/example/notification/websocket/redis/*`: Redis pub/sub classes are under WebSocket.
- `chat-service/src/main/java/com/example/chat/modules/message/infrastructure/cache/CacheNames.java`: generic room/profile cache names live under message infrastructure.
- `chat-service/src/main/java/com/example/chat/modules/room/dto/RoomMessagePinEventPayload.java`: event payload lives in `dto` instead of an event/realtime contract package.
- `chat-service/src/main/java/com/example/chat/modules/message/infrastructure/cache/MessageCacheService.java`: appears unused/unregistered and should be validated or removed.

Packages that are too mixed:

- `common-events`: contains domain contracts plus WebSocket transport envelope.
- `common-kafka`: contains Kafka infrastructure plus domain event wrappers/topic contracts.
- `common-redis` and `common-redis-cache`: separate modules but same `com.example.common.redis` namespace.
- `chat.config`: contains WebSocket config, Redis cache config, Redis atomic config, Redis listener config, Redis event config, and unrelated app config.
- `presence.redis`: contains pub/sub publisher, subscribers, and key-expiry listener, but presence Redis state access is in `PresenceService`.

Packages that should be split:

- Split `common-events` into shared contracts and client realtime protocol, or rename it to `common-contracts` and explicitly separate subpackages.
- Split Redis cache from Redis pub/sub at package level.
- Split WebSocket protocol/envelope from handshake/security.
- Split service application ports from infrastructure adapters.

Packages that should be merged:

- Merge `WsEvent` and `WsOutgoingMessage` into one shared WebSocket envelope.
- Merge duplicate session registry/broadcaster patterns into reusable common-websocket base classes or composition helpers.
- Merge notification's custom Redis pub/sub path into the shared `common-redis` abstraction.

Suggested package structure:

```text
common-contracts
  integration
    account
    chat
    friendship
    notification
    presence
  messaging
    kafka
      KafkaTopicNames
      IntegrationEventEnvelope
  realtime
    WebSocketEventTypes
    WebSocketEnvelope

common-kafka
  publisher
  config
  observability
  error

common-redis-pubsub
  publisher
  subscriber
  dispatcher
  serialization
  registry
  channel

common-redis-cache
  com.example.common.cache.redis
    RedisTtlCache
    RedisTtlCacheManager

common-websocket
  protocol
  security.jwt
  session
  broadcaster

chat-service
  modules.message
    application.port
    infrastructure.messaging.kafka
    infrastructure.messaging.redis
    infrastructure.cache
  realtime.websocket
  realtime.redis

notification-service
  application
  infrastructure.messaging.kafka
  infrastructure.messaging.redis
  realtime.websocket
```

## 5. Consistency Review

Sync vs async inconsistencies:

- Kafka publication is async internally but exposed as `void`, so application flows treat it like fire-and-forget.
- `PublishMessageEventStep` uses `CompletableFuture.runAsync(...)`, while other publish paths call publishers synchronously and rely on Kafka's async send.
- Redis pub/sub publish is synchronous from the caller perspective (`convertAndSend`) and throws on immediate serialization/publish failure.
- WebSocket handlers call command services synchronously on the WebSocket thread.
- Kafka listeners process business logic synchronously in listener threads with no consistent handoff to application services or executors.

Event publishing inconsistencies:

- Chat message events go Kafka first, then Redis pub/sub, then WebSocket.
- Chat room member events in `RoomService` go directly to WebSocket broadcaster, skipping Kafka and Redis pub/sub.
- Presence state changes go service -> Redis pub/sub -> subscriber -> WebSocket, but snapshots go handler -> direct WebSocket.
- Notification goes service -> `NotificationWebSocketPublisher` -> custom Redis pub/sub -> WebSocket.
- Friendship goes Kafka consumer -> `FriendshipWebSocketPublisher` -> direct WebSocket, without Redis pub/sub fanout.

Consumer/subscriber inconsistencies:

- Kafka consumers sometimes perform application logic directly (`MessageCreatedEventConsumer`, `ReactionEventConsumer`) and sometimes only bridge transports (`KafkaChatMessageEventConsumer`).
- Redis subscribers sometimes translate shared payloads to `WsEvent` and sometimes directly handle final WebSocket messages.
- Some Redis subscriber names include event (`ChatMessageSentRedisSubscriber`), some include actor/action (`UserOnlineSubscriber`), and notification has one catch-all `RedisNotificationSubscriber`.

Transport layer inconsistencies:

- WebSocket envelopes are split between `WsEvent` and `WsOutgoingMessage`.
- Redis messages use `RedisMessage<T>`, but notification Redis messages use raw serialized `WsOutgoingMessage`.
- Kafka events use `KafkaEvent<T>`, but event wrappers are transport-specific classes instead of one generic envelope.
- Event type names are reused across Kafka, Redis, and WebSocket without clear separation of semantic event name versus client event name.

Payload/model inconsistencies:

- Backend WebSocket envelopes serialize `payload`; notification frontend expects `data`.
- `WsEvent` has `@JsonAlias("data")`, and `WsOutgoingMessage` also has `@JsonAlias("data")`, but aliases do not make outbound JSON use `data`.
- `NotificationRequestedPayload` and `NotificationEvent` overlap but are not consistently used.
- `FriendRequestEvent` has nested enum `Type`, while other domains use top-level `*EventType` enums.

## 6. Architectural Violations

Files/classes that mix responsibilities:

- `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomService.java`: domain/application room behavior, cache eviction, system messages, and direct WebSocket broadcasting.
- `chatappBE/presence-service/src/main/java/com/example/presence/service/PresenceService.java`: presence domain state, Redis cache, Redis sets, and Redis pub/sub publishing.
- `chatappBE/presence-service/src/main/java/com/example/presence/websocket/handler/PresenceWebSocketHandler.java`: WebSocket command handling, presence state mutation, Redis pub/sub publishing, and direct snapshot delivery.
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java`: Kafka publisher plus recipient computation and room lookup.
- `chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java`: Kafka publishing plus cross-service user profile enrichment.
- `chatappBE/notification-service/src/main/java/com/example/notification/kafka/MessageCreatedEventConsumer.java`: Kafka transport consumer plus notification policy, persistence/idempotency, and command execution.
- `chatappBE/notification-service/src/main/java/com/example/notification/websocket/redis/RedisNotificationSubscriber.java`: Redis transport consumer plus WebSocket delivery.

Files/classes that use the wrong abstraction:

- `RedisNotificationPublisher` and `RedisNotificationSubscriber`: should use the shared Redis pub/sub abstraction or be moved under a clear `infrastructure.redis.pubsub` package.
- `WsEvent`: should not be in `common-events`.
- `WsOutgoingMessage`: should not coexist with `WsEvent`; choose one envelope.
- `ChatMessageEventConsumer`: no-op legacy consumer should not be active as a listener.
- `MessageCacheService`: not a bean and likely not part of the active cache architecture.

Files/classes that couple infrastructure with shared event models:

- `common-kafka/src/main/java/com/example/common/kafka/event/AbstractKafkaEvent.java`
- `common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageSentEvent.java`
- `common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageEditedEvent.java`
- `common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageDeletedEvent.java`
- `common-kafka/src/main/java/com/example/common/kafka/event/ChatReactionUpdatedEvent.java`
- `common-kafka/src/main/java/com/example/common/kafka/event/FriendRequestKafkaEvent.java`
- `common-kafka/src/main/java/com/example/common/kafka/event/FriendshipEvent.java`
- `common-kafka/src/main/java/com/example/common/kafka/event/NotificationRequestedEvent.java`

Files/classes that couple transport with event logic:

- `common-events/src/main/java/com/example/common/integration/websocket/WsEvent.java`
- `notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketPublisher.java`
- `friendship-service/src/main/java/com/example/friendship/websocket/FriendshipWebSocketPublisher.java`
- `chat-service/src/main/java/com/example/chat/realtime/subscriber/*RedisSubscriber.java`
- `presence-service/src/main/java/com/example/presence/redis/*Subscriber.java`

Files/classes that should not be in their current module/package:

- `KafkaTopics` should move from `common-kafka` to a contracts package such as `common-contracts.messaging.kafka`.
- Kafka wrapper event classes should move to contracts or be replaced by a generic Kafka envelope.
- `WsEvent` should move to `common-websocket.protocol` or be deleted in favor of `WsOutgoingMessage` renamed to `WebSocketEnvelope`.
- `RedisNotificationPublisher` and `RedisNotificationSubscriber` should move out of `websocket.redis`.
- `CacheNames` should move to `chat.infrastructure.cache` or module-specific cache packages.

## 7. Optimization Review

Unnecessary complexity:

- Two WebSocket envelope classes solve the same problem.
- Kafka event wrappers duplicate common payload contracts.
- Notification maintains a custom Redis pub/sub implementation despite common Redis pub/sub infrastructure.
- One subscriber class per event type creates repetitive boilerplate.

Redundant layers:

- Chat same-service realtime fanout uses Kafka and Redis. This may be deliberate, but if only realtime delivery is needed, Kafka -> Redis is a costly bridge.
- Notification's Redis pub/sub layer transports final WebSocket messages, while the common Redis layer transports typed events.
- `ChatMessageEventConsumer` is a redundant listener that only logs and ignores.

Duplicated logic:

- Session registry maps are repeated in `ChatSessionRegistry`, `NotificationSessionRegistry`, `FriendshipSessionRegistry`, and `PresenceSessionRegistry`.
- Broadcaster implementations are repeated across chat, notification, presence, and friendship.
- Redis listener-container configuration is repeated per service.
- Redis message creation is repeated in `RedisMessageFactory` and `PresenceRedisPublisher.msg(...)`.

Wasteful event hops:

- Chat command path: WebSocket/API command -> DB -> Kafka -> chat Kafka listener -> Redis pub/sub -> Redis subscriber -> WebSocket.
- Presence join path: WebSocket -> service -> Redis state -> Redis pub/sub -> subscriber -> WebSocket, plus direct snapshot delivery and room online-users republish.
- Notification path: Kafka -> database notification -> WebSocket publisher -> Redis pub/sub -> WebSocket.

Cache misuse:

- Presence TTL state and Redis sets are distributed state, not cache. Treat them as a `PresenceStateStore`.
- Manual cache eviction in application services is fragile and should be encapsulated.
- Cache names and keys are not centrally documented or consistently prefixed.

Pub/sub misuse:

- Notification sends final WebSocket envelopes through Redis pub/sub instead of typed internal realtime events.
- Redis pub/sub is used as a cluster fanout bus, which is fine, but direct WebSocket sends bypass it in room/friendship flows and will not scale to multiple instances.

Kafka misuse:

- Kafka topics are used both as durable cross-service facts and as a trigger for same-service realtime fanout without a clear outbox policy.
- Some listener group IDs are implicit, which is risky in multi-instance deployment.
- No visible standard error/retry/DLQ policy is applied.

WebSocket delivery inefficiencies:

- No send queue/backpressure policy.
- Dead sessions are closed but not consistently removed from registries by the broadcaster.
- Direct service-level WebSocket broadcasting prevents horizontal fanout unless every service instance has the same sessions.

## 8. Recommended Target Design

How Kafka should be used:

- Use Kafka only for durable cross-service integration facts.
- Standardize all topic names in one contract catalog.
- Use explicit consumer groups for every listener.
- Use one generic event envelope unless wrapper classes add real value.
- Move business handling out of Kafka listener classes into application services/handlers.
- Add a standard retry/DLQ/error handler policy.

How Redis cache should be used:

- Use Redis cache for derived/read-optimized data that can be rebuilt.
- Put cache access behind service-specific ports such as `RoomCache`, `UserProfileCache`, and `PresenceStateStore`.
- Rename `TimeRedisCache` to `RedisTtlCache`.
- Move cache package to `com.example.common.cache.redis`.
- Standardize cache names and key patterns.

How Redis pub/sub should be used:

- Use Redis pub/sub only for ephemeral cluster fanout to WebSocket instances.
- Standardize on `RedisMessage<T>` or rename it to `RedisPubSubEnvelope<T>`.
- Make notification use the same abstraction as chat/presence.
- Keep WebSocket envelopes out of Redis pub/sub. Redis should carry realtime events, and WebSocket adapters should render those events to client messages.
- Include channel and event type in subscriber routing where needed.

What `common-event` should contain:

- Shared cross-service payload contracts.
- Shared event type enums that are transport-neutral.
- Shared primitive enums only when they are part of public integration contracts.

What should be removed from `common-event`:

- `integration.websocket.WsEvent`.
- Presentation/client-only notification envelopes.
- Duplicated or unused presence/user payloads such as `UserPresencePayload` if superseded by `integration.presence.*`.
- Transport-specific Kafka concepts.

How WebSocket should be structured:

- Use one envelope: `WebSocketEnvelope<T> { type, payload }` or `{ type, data }`; do not support both except through versioned compatibility.
- Put inbound command models under service-specific `realtime.websocket.command`.
- Put outbound client event types under a shared realtime contract package if the frontend depends on them.
- Consolidate session registries and broadcasters in `common-websocket`.
- Route all cross-instance WebSocket delivery through Redis pub/sub, not direct service-local broadcasters.

How to make the whole flow cleaner and more consistent:

- Command handlers should update state and publish domain/integration events through application ports.
- Kafka adapters should publish durable integration events.
- Redis pub/sub adapters should publish ephemeral realtime fanout events.
- WebSocket adapters should only handle connection lifecycle, inbound commands, and final client delivery.
- Shared contract modules should contain payloads/event names, not transport implementations.
- Use one naming strategy across Kafka topics, Redis channels, and WebSocket event types.

## 9. Problems by Severity

High:

- Direct WebSocket broadcasting from application services (`RoomService`, friendship consumers via `FriendshipWebSocketPublisher`) bypasses Redis cluster fanout and is not horizontally safe.
- WebSocket envelope mismatch: backend `WsOutgoingMessage` serializes `payload`, while frontend notification socket expects `data`.
- `common-kafka` mixes infrastructure with domain integration contracts and has package/path mismatch for Kafka event classes.
- Notification custom Redis pub/sub bypasses shared Redis abstraction and sends final WebSocket envelopes through Redis.
- Kafka consumer group IDs and retry/DLQ behavior are not consistently defined.

Medium:

- `common-events` is polluted with WebSocket transport model `WsEvent`.
- Chat Kafka publisher performs room/member repository lookups instead of only adapting an application event to Kafka.
- Presence service mixes cache, Redis state, pub/sub publishing, and domain behavior.
- Redis cache and Redis pub/sub share the same Java namespace.
- Event names are inconsistent across chat, presence, notification, and friendship.
- Duplicate session registries and broadcasters increase maintenance cost.
- Chat realtime fanout path is expensive and undocumented.

Low:

- Formatting/indentation issues in several classes reduce readability.
- `MessageCacheService` appears unused/unregistered.
- `ChatMessageEventConsumer` is a no-op legacy listener.
- `@JsonAlias("data")` on outbound envelope fields creates a false sense of compatibility.
- `KafkaPubSubException` and `CreateCacheException` names are misleading.

## 10. Refactor Recommendations

What to rename:

- `WsEvent` / `WsOutgoingMessage` -> one `WebSocketEnvelope<T>`.
- `IRedisPublisher` -> `RedisPubSubPublisher`.
- `IRedisSubscriber` -> `RedisPubSubHandler`.
- `TimeRedisCache` -> `RedisTtlCache`.
- `TimeRedisCacheManager` -> `RedisTtlCacheManager`.
- `KafkaPubSubException` -> `KafkaPublishException`.
- `FriendRequestKafkaEvent` -> `FriendRequestEventEnvelope` or replace with generic envelope.
- `MessageCreatedEventConsumer` -> `ChatMessageSentNotificationConsumer`.

What to move:

- Move Kafka topic constants and event envelopes out of `common-kafka` into a contracts module.
- Move `WsEvent` out of `common-events`.
- Move notification Redis pub/sub classes out of `websocket.redis`.
- Move `CacheNames` out of message infrastructure.
- Move cache access behind service-specific infrastructure adapters.

What to split:

- Split durable integration events from realtime client events.
- Split Redis cache from Redis pub/sub packages.
- Split WebSocket security/handshake from WebSocket protocol/envelope.
- Split Kafka listener transport classes from application event handlers.

What to merge:

- Merge `WsEvent` and `WsOutgoingMessage`.
- Merge duplicated session registry/broadcaster patterns.
- Merge notification Redis pub/sub into common Redis pub/sub.
- Merge duplicate Redis message factory logic into a common `RedisPubSubEnvelopeFactory`.

What to delete:

- Delete or disable `ChatMessageEventConsumer` if it is only a legacy no-op.
- Delete `MessageCacheService` if unused.
- Delete unused/overlapping shared payloads after verifying references: likely candidates include `NotificationEvent` and `UserPresencePayload`.

What to standardize first:

- Standardize WebSocket envelope field name (`payload` recommended because chat/presence already use it).
- Standardize event names to dot-case across backend and frontend.
- Standardize Kafka listener group IDs and topic constants.
- Standardize Redis pub/sub envelopes and channel prefixes.
- Standardize package layout per service: `application.port`, `infrastructure.messaging.kafka`, `infrastructure.messaging.redis`, `infrastructure.cache`, `realtime.websocket`.
- Standardize async semantics: decide whether publish methods are fire-and-forget, return `CompletableFuture`, or participate in an outbox pattern.
