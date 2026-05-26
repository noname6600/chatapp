# 1. Executive Summary

- `common-kafka` currently provides a Kafka transport layer: publisher interfaces, a default producer, handler/dispatcher abstractions, registry abstractions, topic constants, logging, routing context, exception type, serializer/deserializer interfaces, auto-configuration, and a set of concrete business event wrapper classes.
- The Kafka layer is not purely transport infrastructure. It owns business/domain event wrappers such as `AccountCreatedEvent`, `ChatMessageSentEvent`, `FriendshipEvent`, and `NotificationRequestedEvent`, plus domain topic constants in `KafkaTopics`.
- Redis common code currently provides Redis pub/sub infrastructure: publisher interfaces, a default publisher, subscriber/dispatcher/listener abstractions, a JSON serializer/deserializer, registry abstractions, channel constants, logging, routing context, exception type, and compatibility aliases around older Redis message classes.
- Redis common code is closer to transport infrastructure than Kafka, but it still owns domain routing concepts in `RedisChannels` and leaks presence-specific normalization into generic Redis serialization, registry, and dispatch code.
- Kafka and Redis overlap significantly at the shared common layer. They both define event/message contracts, publishers, dispatchers, payload registries, routing contexts, lifecycle logging, validators, and exception wrappers.
- Kafka and Redis should remain separated at the transport adapter boundary because Kafka topics/keys and Redis pub/sub channels have different delivery semantics. They should not be forced into a generic `MessageBus` abstraction.
- Kafka and Redis should share one common event contract model. The existing `com.example.common.event.EventEnvelope` and `EventMetadata` are the best candidates because they are transport-neutral and already documented as the preferred service contract.
- `com.example.common.integration.contract` should not stay as-is. It is too thin to justify its own package, duplicates `common-event` validation and metadata concepts, contains deprecated WebSocket/realtime channel constants, and is referenced only by Kafka/Redis logging/publishing plus tests. It should be removed after moving any still-useful validation behavior into `com.example.common.event.validation`.

# 2. Current Responsibility Map

- Kafka layer responsibilities:
  - Publish Kafka messages through `com.example.common.kafka.producer.KafkaEventProducer`.
  - Implement synchronous publish behavior in `com.example.common.kafka.producer.DefaultKafkaEventProducer`.
  - Support both legacy `IKafkaEvent<?>` and neutral `EventEnvelope<?>` publishing.
  - Dispatch consumed events by `eventType` through `KafkaEventDispatcher` and `KafkaEventHandler`.
  - Register event type to payload mappings through `KafkaEventRegistry` and `DefaultKafkaEventRegistry`.
  - Expose Kafka topic names through `KafkaTopics`.
  - Define Kafka-specific event wrappers and base classes through `IKafkaEvent`, `KafkaEvent`, `AbstractKafkaEvent`, and concrete wrapper classes.
  - Define logging via `IKafkaEventLogger` and `KafkaEventLogger`.
  - Define Kafka publish exceptions through `KafkaPubSubException`.
  - Define serializer/deserializer contracts through `KafkaEventSerializer` and `KafkaEventDeserializer`.
  - Provide `KafkaAutoConfiguration`.

- Redis layer responsibilities:
  - Publish Redis pub/sub messages through `RedisEventPublisher`.
  - Implement publish behavior in `DefaultRedisEventPublisher`.
  - Support both legacy `IRedisMessage` and neutral `EventEnvelope<?>` publishing.
  - Serialize and deserialize Redis messages through `RedisEventSerializer`, `JsonRedisEventSerializer`, `IRedisMessageSerializer`, and `JsonRedisMessageSerializer`.
  - Listen to Redis pub/sub messages through `RedisEventListener` and `DefaultRedisMessageListener`.
  - Dispatch deserialized messages by `eventType` through `RedisEventDispatcher`, `RedisMessageDispatcher`, and `RedisEventSubscriber`.
  - Register event type to payload mappings through `RedisEventRegistry`, `DefaultRedisEventRegistry`, `IRedisMessageRegistry`, and `DefaultRedisMessageRegistry`.
  - Expose Redis channel names through `RedisChannels`.
  - Define Redis-specific message shapes through `IRedisMessage`, `RedisMessage`, and `AbstractRedisMessage`.
  - Define logging through `IRedisPubSubLogger` and `RedisPubSubLogger`.
  - Define Redis pub/sub exceptions through `RedisPubSubException`.
  - Provide `RedisAutoConfiguration`.

- `integration.contract` responsibilities:
  - Define shared realtime naming and field conventions in `RealtimeContractConventions`.
  - Validate event names and identity metadata in `RealtimeContractValidator`.
  - Define lifecycle phase strings for transport logs in `RealtimeEventLifecycleContract`.

- Unclear boundaries:
  - `common-kafka` is both a Kafka adapter and a business event contract module.
  - `common-redis` is both a Redis adapter and a domain route/channel module.
  - `common-events` has the neutral `EventEnvelope` model, but Kafka and Redis still maintain their own message/event contracts.
  - `common-events` contains `com.example.common.integration.realtime.RealtimeContractVersions`, which reaches toward Redis version constants by reflection.
  - Presence alias normalization is implemented inside Kafka and Redis infrastructure classes instead of in a domain/event compatibility layer.

# 3. Overlap / Duplication

**Exact duplication**

- Files/classes:
  - `com.example.common.kafka.producer.KafkaEventProducer`
  - `com.example.common.redis.publisher.RedisEventPublisher`
  - `com.example.common.kafka.producer.DefaultKafkaEventProducer`
  - `com.example.common.redis.publisher.DefaultRedisEventPublisher`
- Why it overlaps:
  - Both publisher interfaces expose a legacy transport message method and an `EventEnvelope<?>` method.
  - Both default implementations validate event metadata, publish through a transport client, log lifecycle events, and wrap failures in a transport exception.
- Impact:
  - The same publish contract migration is being implemented twice.
  - `EventEnvelope` is treated as an adapter input rather than the canonical model, so legacy transport contracts remain central.

- Files/classes:
  - `com.example.common.kafka.registry.KafkaEventRegistry`
  - `com.example.common.kafka.registry.DefaultKafkaEventRegistry`
  - `com.example.common.redis.registry.RedisEventRegistry`
  - `com.example.common.redis.registry.DefaultRedisEventRegistry`
  - `com.example.common.redis.registry.IRedisMessageRegistry`
  - `com.example.common.redis.registry.DefaultRedisMessageRegistry`
  - `com.example.common.event.registry.EventRegistry`
- Why it overlaps:
  - All of these are event type to payload class registries.
  - Redis has both old and new registry types, Kafka has its own registry, and `common-events` has a generic registry contract.
- Impact:
  - Payload registration behavior is fragmented.
  - Compatibility fixes such as presence alias normalization are duplicated in transport-specific registries.
  - `EventRegistry` in `common-events` is conceptually correct but not used as the common implementation.

- Files/classes:
  - `com.example.common.kafka.consumer.KafkaEventDispatcher`
  - `com.example.common.kafka.consumer.KafkaEventHandler`
  - `com.example.common.redis.dispatcher.RedisEventDispatcher`
  - `com.example.common.redis.dispatcher.RedisMessageDispatcher`
  - `com.example.common.redis.subscriber.RedisEventSubscriber`
- Why it overlaps:
  - Kafka and Redis both dispatch by `eventType` to a typed handler/subscriber.
  - The handler method names differ, but the flow is the same: resolve `eventType`, find handler, invoke callback.
- Impact:
  - Two different APIs teach the same concept with different names.
  - Service code must learn separate handler shapes even when the domain event handling semantics are identical.

- Files/classes:
  - `com.example.common.kafka.flow.KafkaEventRoutingContext`
  - `com.example.common.redis.flow.RedisEventRoutingContext`
- Why it overlaps:
  - Both records contain destination plus `eventType`, `correlationId`, and `sourceService`.
  - The only real difference is `topic/key` for Kafka versus `channel` for Redis.
- Impact:
  - The code duplicates transport-neutral event metadata in two transport-specific context records.
  - This reinforces separate metadata models even though `EventMetadata` already exists.

- Files/classes:
  - `com.example.common.kafka.logging.KafkaEventLogger`
  - `com.example.common.kafka.logging.IKafkaEventLogger`
  - `com.example.common.redis.observability.RedisPubSubLogger`
  - `com.example.common.redis.observability.IRedisPubSubLogger`
  - `com.example.common.integration.contract.RealtimeEventLifecycleContract`
- Why it overlaps:
  - Both loggers report publish/dispatch/error lifecycle states using the same string lifecycle constants.
  - The lifecycle constants are not a rich contract; they are shared log labels.
- Impact:
  - A package named `integration.contract` is kept alive mostly to share logging words.
  - Logging abstraction becomes a reason for architectural coupling.

- Files/classes:
  - `com.example.common.kafka.error.KafkaPubSubException`
  - `com.example.common.redis.exception.RedisPubSubException`
- Why it overlaps:
  - Both wrap transport publish/subscribe failures with similar purpose.
- Impact:
  - This duplication is acceptable because exceptions are transport-specific, but the naming should be consistent.

**Conceptual duplication**

- Files/classes:
  - `com.example.common.event.EventEnvelope`
  - `com.example.common.event.EventMetadata`
  - `com.example.common.kafka.api.IKafkaEvent`
  - `com.example.common.redis.api.IRedisMessage`
  - `com.example.common.redis.message.RedisMessage`
  - `com.example.common.redis.message.AbstractRedisMessage`
  - `com.example.common.kafka.event.AbstractKafkaEvent`
- Why it overlaps:
  - These all model the same logical concept: event identity, event type, source service, creation time, correlation id, and payload.
  - `IKafkaEvent` uses `UUID getEventId()`, Redis uses `String getMessageId()`, and `EventMetadata` uses `String eventId`.
- Impact:
  - The codebase has three event contract shapes.
  - Adapters such as `EnvelopeKafkaEventAdapter` and Redis `RedisMessage.builder()` conversions exist only because the common model is not canonical.
  - Metadata naming drifts between `createdAt`, `occurredAt`, `messageId`, and `eventId`.

- Files/classes:
  - `com.example.common.kafka.topic.KafkaTopics`
  - `com.example.common.event.EventType`
  - `com.example.common.integration.account.AccountEventType`
  - `com.example.common.integration.chat.ChatEventType`
  - `com.example.common.integration.friendship.FriendshipEventType`
  - `com.example.common.integration.notification.NotificationEventType`
  - `com.example.common.integration.presence.PresenceEventType`
  - `com.example.common.integration.user.UserEventType`
- Why it overlaps:
  - Event names are defined in several places.
  - `KafkaTopics.ACCOUNT_CREATED` is `account.account.created`, while `EventType.ACCOUNT_CREATED` is `account.created`.
  - `FriendshipEventType` uses `friend.request.sent`, while `EventType` uses `friendship.request.sent`.
  - `FriendshipEventType.FRIEND_REQUEST_DECLINED` uses `declined`, while `EventType.FRIENDSHIP_REQUEST_REJECTED` uses `rejected`.
  - `ChatEventType` contains values such as message pinned/unpinned and member joined/left/removed that are not present in `EventType`.
- Impact:
  - There is no single source of truth for event type names.
  - Transport routing constants, generic event constants, and domain event enums can drift independently.
  - Consumers may register one event name while producers publish another.

- Files/classes:
  - `com.example.common.integration.kafka.event.FriendRequestKafkaEvent`
  - `com.example.common.integration.friendship.FriendshipEventType`
  - `com.example.common.kafka.topic.KafkaTopics`
- Why it overlaps:
  - `FriendRequestKafkaEvent` hardcodes `EVENT_TYPE = "friend.request.event"`.
  - That value is not represented by `FriendshipEventType` and does not match the more specific friendship request event names.
- Impact:
  - The event name is vague and bypasses the domain enum.
  - It weakens the ability to validate or reason about friendship event contracts.

- Files/classes:
  - `com.example.common.integration.contract.RealtimeContractValidator`
  - `com.example.common.event.validation.EventContractValidator`
- Why it overlaps:
  - Both validate event naming and event metadata.
  - `RealtimeContractValidator` validates event name, event id, correlation id, source service, and occurred time.
  - `EventContractValidator` validates event type pattern and `EventMetadata`.
- Impact:
  - Validation rules can diverge.
  - Transport publishers import `integration.contract` instead of using the neutral event validation package.

**Naming inconsistency**

- Files/classes:
  - `com.example.common.kafka.api.IKafkaEvent`
  - `com.example.common.kafka.api.KafkaEvent`
  - `com.example.common.kafka.producer.KafkaEventProducer`
  - `com.example.common.kafka.api.IKafkaEventPublisher`
  - `com.example.common.kafka.api.KafkaEventPublisher`
- Why it overlaps:
  - Kafka exposes `Event`, `EventProducer`, `EventPublisher`, and `IKafkaEventPublisher` names for closely related concepts.
  - Several are deprecated aliases but still present in the public API.
- Impact:
  - It is unclear which API is canonical.
  - New callers may choose old aliases because they remain visible.

- Files/classes:
  - `com.example.common.redis.api.IRedisMessage`
  - `com.example.common.redis.message.RedisMessage`
  - `com.example.common.redis.publisher.RedisEventPublisher`
  - `com.example.common.redis.api.IRedisPublisher`
  - `com.example.common.redis.subscriber.RedisEventSubscriber`
  - `com.example.common.redis.api.IRedisSubscriber`
- Why it overlaps:
  - Redis moved from `Message` naming to `Event` naming but retained old aliases and old implementations.
  - Canonical classes such as `JsonRedisEventSerializer` and `DefaultRedisEventRegistry` extend deprecated message classes.
- Impact:
  - The new API is not actually independent from the deprecated API.
  - The package looks refactored from the outside but remains old-style inside.

- Files/classes:
  - `com.example.common.kafka.error.KafkaPubSubException`
  - `com.example.common.redis.exception.RedisPubSubException`
- Why it overlaps:
  - Kafka uses package `error`, Redis uses package `exception`.
- Impact:
  - Small but visible inconsistency in common module structure.
  - It makes cross-transport navigation harder.

- Files/classes:
  - `com.example.common.kafka.logging.KafkaEventLogger`
  - `com.example.common.redis.observability.RedisPubSubLogger`
- Why it overlaps:
  - Kafka uses `logging`, Redis uses `observability`.
  - Kafka says `Event`, Redis says `PubSub`.
- Impact:
  - The modules describe equivalent responsibilities with different vocabulary.

**Structural inconsistency**

- Files/classes:
  - Physical path `common-kafka/src/main/java/com/example/common/kafka/event/AccountCreatedEvent.java`
  - Physical path `common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageSentEvent.java`
  - Physical path `common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageEditedEvent.java`
  - Physical path `common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageDeletedEvent.java`
  - Physical path `common-kafka/src/main/java/com/example/common/kafka/event/ChatReactionUpdatedEvent.java`
  - Physical path `common-kafka/src/main/java/com/example/common/kafka/event/FriendRequestKafkaEvent.java`
  - Physical path `common-kafka/src/main/java/com/example/common/kafka/event/FriendshipEvent.java`
  - Physical path `common-kafka/src/main/java/com/example/common/kafka/event/NotificationRequestedEvent.java`
- Why it overlaps:
  - These files are physically under `com/example/common/kafka/event`, but their declared package is `com.example.common.integration.kafka.event`.
  - They extend the deprecated bridge class `com.example.common.integration.kafka.event.AbstractKafkaEvent`, which extends the canonical `com.example.common.kafka.event.AbstractKafkaEvent`.
- Impact:
  - File path, package name, and inheritance direction disagree.
  - This is a strong signal that compatibility shims are controlling the architecture.
  - It increases build/navigation confusion and makes package ownership unclear.

- Files/classes:
  - `com.example.common.kafka.serialization.KafkaEventSerializer`
  - `com.example.common.kafka.serialization.KafkaEventDeserializer`
  - `com.example.common.redis.serialization.RedisEventSerializer`
  - `com.example.common.redis.serialization.JsonRedisEventSerializer`
  - `com.example.common.redis.serialization.JsonRedisMessageSerializer`
- Why it overlaps:
  - Redis owns a concrete JSON serializer/deserializer path.
  - Kafka only exposes serializer/deserializer interfaces and relies on `KafkaTemplate<String, Object>` for actual serialization.
- Impact:
  - The two transports are implemented at different abstraction depths.
  - Kafka's registry/deserializer abstractions are less clearly connected to runtime behavior than Redis's.

- Files/classes:
  - `com.example.common.kafka.config.KafkaAutoConfiguration`
  - `com.example.common.redis.config.RedisAutoConfiguration`
- Why it overlaps:
  - Kafka uses `@AutoConfiguration`.
  - Redis uses `@Configuration`.
  - Neither module appears to provide Spring Boot auto-configuration registration resources under `src/main/resources`.
- Impact:
  - The common modules do not expose configuration consistently.
  - Consumers may need explicit imports or package scanning, which weakens the value of shared auto-configuration.

- Files/classes:
  - `com.example.common.integration.realtime.RealtimeContractVersions`
  - `com.example.common.redis.config.RedisContractVersions`
- Why it overlaps:
  - Redis version constants exist in Redis common, while common-events also exposes deprecated Redis fanout aliases by reflective lookup of `RedisContractVersions`.
- Impact:
  - `common-events` has a semantic dependency back toward `common-redis`.
  - The dependency direction is inverted even if it is not a compile-time Gradle dependency.

**Flow inconsistency**

- Files/classes:
  - `com.example.common.kafka.consumer.KafkaEventDispatcher`
  - `com.example.common.kafka.consumer.KafkaEventConsumer`
  - `com.example.common.redis.listener.RedisEventListener`
  - `com.example.common.redis.dispatcher.RedisEventDispatcher`
- Why it overlaps:
  - Redis common owns listener-to-dispatcher flow.
  - Kafka common owns dispatcher/handler abstractions but does not own a concrete listener equivalent.
- Impact:
  - Redis has a full common receive pipeline.
  - Kafka has a partial common receive pipeline and leaves more flow ownership to service-level `@KafkaListener` code.

- Files/classes:
  - `DefaultKafkaEventProducer`
  - `DefaultRedisEventPublisher`
  - Kafka wrapper events in `com.example.common.integration.kafka.event`
  - Redis generic message model in `com.example.common.redis.message.RedisMessage`
- Why it overlaps:
  - Kafka commonly publishes concrete wrapper classes such as `ChatMessageSentEvent`.
  - Redis commonly publishes generic `RedisMessage<T>` values with registered payload types.
- Impact:
  - Kafka and Redis solve the same event-contract problem in two architectural styles.
  - Kafka emphasizes per-domain transport classes; Redis emphasizes generic message plus registry.
  - There is no clear reason both styles should coexist in shared common modules.

- Files/classes:
  - `KafkaTopics`
  - `RedisChannels`
  - `EventEnvelope`
- Why it overlaps:
  - Kafka topics often duplicate event names, for example `chat.message.sent`.
  - Redis channels are route destinations such as `realtime.chat.room.<id>`, while the message carries the event type separately.
- Impact:
  - Redis keeps destination and event type more separate than Kafka.
  - Kafka conflates routing taxonomy and event taxonomy in the shared constants.
  - The difference is valid at the transport level, but the current constants make it unclear which names are contracts and which are destinations.

# 4. Architectural Violations

- `common-kafka/src/main/java/com/example/common/kafka/event/AccountCreatedEvent.java`
  - Mixes Kafka transport wrapper concerns with `AccountCreatedPayload` domain semantics.
  - Uses `AccountEventType.ACCOUNT_CREATED.value()` inside a transport-specific event class.

- `common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageSentEvent.java`
  - Mixes Kafka transport wrapper concerns with `ChatMessagePayload` domain semantics.
  - Uses `ChatEventType.MESSAGE_SENT.value()` inside the Kafka module.

- `common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageEditedEvent.java`
  - Mixes Kafka transport wrapper concerns with `MessageUpdatedPayload` domain semantics.
  - Creates a Kafka-specific event class for a common domain event.

- `common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageDeletedEvent.java`
  - Mixes Kafka transport wrapper concerns with `MessageDeletedPayload` domain semantics.
  - Duplicates what `EventEnvelope<MessageDeletedPayload>` can represent.

- `common-kafka/src/main/java/com/example/common/kafka/event/ChatReactionUpdatedEvent.java`
  - Mixes Kafka transport wrapper concerns with `ReactionPayload` domain semantics.
  - Adds another per-domain Kafka wrapper where a neutral envelope is sufficient.

- `common-kafka/src/main/java/com/example/common/kafka/event/FriendRequestKafkaEvent.java`
  - Hardcodes `EVENT_TYPE = "friend.request.event"` instead of using `FriendshipEventType`.
  - Creates a vague transport event name outside the domain enum.

- `common-kafka/src/main/java/com/example/common/kafka/event/FriendshipEvent.java`
  - Mixes Kafka transport wrapper concerns with `FriendshipPayload` domain semantics.
  - Factory methods duplicate domain event construction that belongs in a domain contract/factory layer, not the Kafka module.

- `common-kafka/src/main/java/com/example/common/kafka/event/NotificationRequestedEvent.java`
  - Mixes Kafka transport wrapper concerns with `NotificationRequestedPayload` domain semantics.
  - Uses `NotificationEventType.NOTIFICATION_REQUESTED.value()` inside the transport module.

- `com.example.common.kafka.topic.KafkaTopics`
  - Contains business topic names for account, user profile, friendship, chat, and notification events.
  - Several constants are event names rather than clearly transport-only destinations.
  - Duplicates event naming already present in `EventType` and domain event enums.

- `com.example.common.redis.channel.RedisChannels`
  - Contains chat, notification, and presence channel taxonomy.
  - This is Redis-specific, so it is less severe than Kafka event wrappers, but it still makes `common-redis` partly domain-aware.

- `com.example.common.kafka.consumer.KafkaEventDispatcher`
  - Imports `com.example.common.integration.presence.PresenceEventType`.
  - Generic Kafka dispatch code should not know about presence alias normalization.

- `com.example.common.kafka.registry.DefaultKafkaEventRegistry`
  - Imports `PresenceEventType`.
  - Generic Kafka payload registry should not contain presence-specific compatibility behavior.

- `com.example.common.redis.serialization.JsonRedisMessageSerializer`
  - Imports `PresenceEventType`.
  - Generic Redis JSON serialization should not know about one domain's alias rules.

- `com.example.common.redis.registry.DefaultRedisMessageRegistry`
  - Imports `PresenceEventType`.
  - Generic Redis payload registry should not contain presence-specific compatibility behavior.

- `com.example.common.redis.dispatcher.RedisMessageDispatcher`
  - Imports `PresenceEventType`.
  - Deprecated generic dispatcher still owns domain-specific alias resolution.

- `com.example.common.redis.dispatcher.RedisEventDispatcher`
  - Imports `PresenceEventType`.
  - Canonical dispatcher repeats the same domain leak as the deprecated dispatcher.

- `com.example.common.integration.contract.RealtimeContractConventions`
  - Contains generic field constants and deprecated channel prefix constants.
  - The field constants duplicate `EventMetadata` concepts.
  - The WebSocket/realtime channel prefix constants do not belong in a generic event contract package.

- `com.example.common.integration.contract.RealtimeContractValidator`
  - Duplicates `com.example.common.event.validation.EventContractValidator`.
  - Uses `occurredAt` naming while `EventMetadata` uses `createdAt`.
  - Is imported by Kafka and Redis publishers even though validation should belong to the neutral event model.

- `com.example.common.integration.contract.RealtimeEventLifecycleContract`
  - Provides string constants used by transport loggers.
  - This is not a durable integration contract; it is logging vocabulary.

- `com.example.common.integration.realtime.RealtimeContractVersions`
  - Lives in `common-events` but reflects into `com.example.common.redis.config.RedisContractVersions`.
  - Creates a backwards semantic dependency from event contracts toward Redis.

- `com.example.common.event.registry.EventRegistry`
  - Duplicates the payload registry idea implemented separately by Kafka and Redis.
  - Currently does not appear to be the shared abstraction used by either transport.

- `com.example.common.kafka.api.IKafkaEvent`, `com.example.common.redis.api.IRedisMessage`, and `com.example.common.event.EventEnvelope`
  - Create three competing contract models for the same logical event.
  - Force transport adapters to convert between equivalent metadata shapes.

# 5. Review of `com.example.common.integration.contract`

- What problem this package is trying to solve:
  - It tries to define a small shared realtime contract for Kafka, Redis, and possibly WebSocket flows.
  - It centralizes event name validation, identity validation, common metadata field names, deprecated realtime/WebSocket channel prefixes, and lifecycle labels for logs.

- Whether it actually solves that problem well:
  - It solves the problem only superficially.
  - `RealtimeContractValidator` is useful, but its responsibility already belongs in `com.example.common.event.validation.EventContractValidator`.
  - `RealtimeContractConventions` mostly repeats `EventMetadata` field concepts and includes deprecated channel prefix constants.
  - `RealtimeEventLifecycleContract` is only log label vocabulary, not a cross-service event contract.
  - The package does not define the actual shared event model. That model already exists as `EventEnvelope` and `EventMetadata`.

- Whether it is redundant:
  - Yes, mostly.
  - It duplicates validation from `EventContractValidator`.
  - It duplicates metadata field concepts from `EventMetadata`.
  - It overlaps with transport-specific logging code in Kafka and Redis.
  - It overlaps with WebSocket/realtime route naming through deprecated prefix constants without owning a complete WebSocket contract.

- Whether it should be removed, merged, renamed, or kept:
  - It should be removed after a short migration.
  - Do not keep it as-is.
  - Do not rename it alone; renaming would preserve the same ambiguity.
  - Merge the useful validation behavior into `com.example.common.event.validation.EventContractValidator`.
  - Delete field constants if callers can use `EventMetadata` field names or serializer-local constants.
  - Move lifecycle labels into transport-local logging classes, unless a real event lifecycle enum is needed by more than logging.

- Best target location if moved:
  - Validation: `com.example.common.event.validation.EventContractValidator`.
  - Metadata contract: `com.example.common.event.EventMetadata`.
  - Lifecycle log labels: `com.example.common.kafka.logging` and `com.example.common.redis.observability`, or a small `com.example.common.event.EventLifecycle` only if it is used as a real domain concept.
  - Deprecated channel prefixes: delete; Redis channel constants already live in `RedisChannels`, and WebSocket route constants should live in the WebSocket/common-websocket module if still needed.

# 6. Keep / Merge / Delete Recommendations

| Item | Action (Keep / Refactor / Merge / Delete / Move / Rename) | Reason | Suggested destination |
|---|---|---|---|
| `com.example.common.event.EventEnvelope` | Keep | Best existing transport-neutral event contract. | Stay in `common-events`. |
| `com.example.common.event.EventMetadata` | Keep | Canonical place for event id, type, source service, created time, and correlation id. | Stay in `common-events`. |
| `com.example.common.event.Event` | Keep | Useful small interface for payload plus metadata if services need an interface form. | Stay in `common-events`. |
| `com.example.common.event.validation.EventContractValidator` | Refactor | Should absorb the useful static validation currently in `RealtimeContractValidator`. | `common-events/src/main/java/com/example/common/event/validation`. |
| `com.example.common.integration.contract.RealtimeContractValidator` | Delete | Duplicates neutral event validation and keeps Kafka/Redis coupled to `integration.contract`. | Merge useful rules into `EventContractValidator`. |
| `com.example.common.integration.contract.RealtimeContractConventions` | Delete | Mostly duplicates metadata field concepts and contains deprecated route prefixes. | Use `EventMetadata`; move active route constants to owning transport modules. |
| `com.example.common.integration.contract.RealtimeEventLifecycleContract` | Delete | Shared log strings are not enough reason for a contract package. | Inline or move to transport-local logger constants. |
| `com.example.common.integration.realtime.RealtimeContractVersions` | Move | Common-events should not reflect into Redis version constants. | Redis versions to `common-redis`; Kafka versions to `common-kafka` or service contract module. |
| `com.example.common.kafka.producer.KafkaEventProducer` | Refactor | Keep Kafka-specific publish adapter, but make `EventEnvelope<?>` the primary contract. | Stay in `common-kafka`. |
| `com.example.common.kafka.producer.DefaultKafkaEventProducer` | Refactor | Transport adapter is useful, but legacy `IKafkaEvent` path should become compatibility-only. | Stay in `common-kafka`. |
| `com.example.common.kafka.api.IKafkaEvent` | Delete | Duplicates `EventEnvelope` and `EventMetadata`. | Temporary deprecated adapter only until migration completes. |
| `com.example.common.kafka.api.KafkaEvent` | Delete | Deprecated alias for `IKafkaEvent`. | None. |
| `com.example.common.kafka.event.AbstractKafkaEvent` | Delete | Duplicates neutral envelope metadata and supports transport-specific domain wrappers. | Replace with `EventEnvelope` factories. |
| `com.example.common.integration.kafka.event.AbstractKafkaEvent` | Delete | Deprecated bridge that preserves old package shape. | None after wrapper migration. |
| Kafka concrete wrappers in `com.example.common.integration.kafka.event` | Delete | Business event classes do not belong in Kafka common and duplicate `EventEnvelope<Payload>`. | Replace with domain payload plus `EventEnvelope` creation in `common-events` or service factories. |
| `com.example.common.kafka.topic.KafkaTopics` | Refactor | Keep transport destinations, but stop using this as the event-name source of truth. | Stay in `common-kafka` for physical topics; event names in `common-events` domain enums. |
| `com.example.common.kafka.registry.KafkaEventRegistry` | Merge | Same payload mapping responsibility as Redis registry and `EventRegistry`. | Prefer a shared `common-events` payload registry if Kafka deserialization needs one. |
| `com.example.common.kafka.registry.DefaultKafkaEventRegistry` | Merge | Duplicates Redis registry and contains presence-specific normalization. | Shared event registry plus domain normalizer. |
| `com.example.common.kafka.consumer.KafkaEventDispatcher` | Refactor | Useful transport dispatcher, but should dispatch neutral envelopes and remove presence imports. | Stay in `common-kafka`. |
| `com.example.common.kafka.consumer.KafkaEventHandler` | Refactor | Handler shape should align with Redis if both handle neutral envelopes. | Stay in `common-kafka` or shared event handler interface only if truly reused. |
| `com.example.common.kafka.serialization.KafkaEventSerializer` | Refactor | Interface is fine if Kafka owns serialization, but currently disconnected from runtime producer flow. | Stay in `common-kafka` or remove until implemented. |
| `com.example.common.kafka.serialization.KafkaEventDeserializer` | Refactor | Same issue as serializer; align with shared payload registry if kept. | Stay in `common-kafka`. |
| `com.example.common.kafka.error.KafkaPubSubException` | Rename | Transport exception is valid, but package naming should align with Redis. | Rename package to `com.example.common.kafka.exception` or align Redis to `error`. |
| `com.example.common.redis.publisher.RedisEventPublisher` | Refactor | Keep Redis-specific channel publishing, but make `EventEnvelope<?>` primary. | Stay in `common-redis`. |
| `com.example.common.redis.publisher.DefaultRedisEventPublisher` | Refactor | Useful adapter, but legacy `IRedisMessage` path should become compatibility-only. | Stay in `common-redis`. |
| `com.example.common.redis.api.IRedisMessage` | Delete | Duplicates `EventEnvelope` and `EventMetadata`. | Temporary deprecated adapter only until migration completes. |
| `com.example.common.redis.message.RedisMessage` | Delete | Legacy generic message duplicates neutral event envelope. | Replace with `EventEnvelope`. |
| `com.example.common.redis.message.AbstractRedisMessage` | Delete | Deprecated metadata base duplicates `EventMetadata`. | None. |
| `com.example.common.redis.serialization.RedisEventSerializer` | Refactor | Serializer is useful, but should serialize/deserialize `EventEnvelope<?>`. | Stay in `common-redis`. |
| `com.example.common.redis.serialization.JsonRedisEventSerializer` | Refactor | Concrete serializer is useful, but should not extend deprecated `JsonRedisMessageSerializer`. | Stay in `common-redis`. |
| `com.example.common.redis.serialization.JsonRedisMessageSerializer` | Delete | Deprecated class still contains canonical logic and presence alias leakage. | Move active logic to `JsonRedisEventSerializer`. |
| `com.example.common.redis.registry.RedisEventRegistry` | Merge | Same responsibility as Kafka registry and `EventRegistry`. | Shared event payload registry in `common-events`, if needed. |
| `com.example.common.redis.registry.DefaultRedisEventRegistry` | Merge | Canonical class only extends deprecated implementation. | Shared event payload registry or native implementation. |
| `com.example.common.redis.registry.DefaultRedisMessageRegistry` | Delete | Deprecated implementation still owns real behavior and presence normalization. | Move active behavior to shared registry or `DefaultRedisEventRegistry`. |
| `com.example.common.redis.dispatcher.RedisEventDispatcher` | Refactor | Useful receive flow, but should dispatch neutral envelopes and remove presence imports. | Stay in `common-redis`. |
| `com.example.common.redis.dispatcher.RedisMessageDispatcher` | Delete | Deprecated dispatcher duplicates canonical dispatcher. | None after migration. |
| `com.example.common.redis.listener.RedisEventListener` | Refactor | Useful Redis listener, but should not cast canonical serializer to deprecated serializer type. | Stay in `common-redis`. |
| `com.example.common.redis.listener.DefaultRedisMessageListener` | Delete | Deprecated base still controls canonical listener inheritance. | Move active logic into `RedisEventListener`. |
| `com.example.common.redis.channel.RedisChannels` | Refactor | Redis channel constants are valid transport routes, but chat/notification/presence ownership should be explicit. | Keep in `common-redis` if routes are platform-wide; otherwise move to owning service contracts. |
| `com.example.common.redis.config.RedisContractVersions` | Keep | Redis-specific versioning belongs in Redis common. | Stay in `common-redis`. |
| `com.example.common.event.registry.EventRegistry` | Merge | Good shared concept, but unused by Kafka/Redis. | Use as the common payload registry or delete if transport registries remain separate. |
| `com.example.common.event.EventType` | Refactor | Global enum conflicts with domain-specific event enums. | Merge with domain event enums or replace with one authoritative event catalog. |
| Domain event enums in `com.example.common.integration.*` | Refactor | They are often more complete than `EventType`, but values conflict. | Keep as authoritative domain enums only after reconciling names. |
| Presence alias normalization in Kafka/Redis classes | Move | Transport infrastructure should not import `PresenceEventType`. | Domain event normalizer in `common-events` or presence contract package. |

# 7. Proposed Target Design

- Kafka:
  - Keep Kafka as a transport adapter module.
  - Primary publish contract should be `publish(String topic, String key, EventEnvelope<?> event)`.
  - Keep topic/key handling, Kafka client integration, Kafka-specific errors, and Kafka-specific observability in `common-kafka`.
  - Remove concrete domain event wrapper classes from `common-kafka`.
  - Keep Kafka topic constants only when they represent physical Kafka destinations, not canonical event names.
  - If Kafka needs deserialization support, wire it to a shared payload registry based on event type.

- Redis pub/sub:
  - Keep Redis as a transport adapter module.
  - Primary publish contract should be `publish(String channel, EventEnvelope<?> event)`.
  - Keep Redis channel handling, `StringRedisTemplate` integration, Redis listener wiring, Redis-specific errors, and Redis-specific observability in `common-redis`.
  - Move active serializer/listener logic out of deprecated message classes into canonical event classes.
  - Keep `RedisChannels` only if these channels are platform-wide transport routes. Otherwise move chat, notification, and presence channel constants into their owning service or contract modules.

- Shared event/contract model:
  - Use `EventEnvelope<T>` and `EventMetadata` as the one event shape across Kafka and Redis.
  - Pick one event type source of truth.
  - Prefer domain-specific event enums such as `ChatEventType`, `FriendshipEventType`, `PresenceEventType`, and `NotificationEventType` if they are reconciled and complete.
  - Remove or regenerate `EventType` if it cannot stay aligned with the domain enums.
  - Move event validation into `EventContractValidator`.
  - Use one payload registry abstraction if both Kafka and Redis need event type to payload class resolution.

- Transport-specific adapters:
  - Kafka adapter owns topic/key mapping and Kafka client calls.
  - Redis adapter owns channel mapping, Redis serialization, listener registration, and pub/sub dispatch.
  - Neither adapter should own business payload classes.
  - Neither adapter should import one domain enum for alias normalization.
  - Legacy `IKafkaEvent` and `IRedisMessage` adapters can remain temporarily but should not be the main API.

- Where common abstractions should live:
  - `common-events`: `EventEnvelope`, `EventMetadata`, event validation, authoritative event naming, optional shared payload registry, optional domain event normalizer.
  - `common-kafka`: Kafka producer/consumer adapters, Kafka topic routing, Kafka logging, Kafka exceptions, Kafka auto-configuration.
  - `common-redis`: Redis publisher/listener adapters, Redis channel routing, Redis serialization, Redis logging, Redis exceptions, Redis auto-configuration.
  - `common.integration.contract`: no long-term package. Delete after migration.

# 8. Refactor Order

1. Freeze the current wire behavior with tests before moving contracts.
   - Capture current JSON shape, event type values, Kafka topic names, Redis channel names, and correlation id behavior.
   - This is important because event contract refactors can silently break consumers.

2. Choose the authoritative event type source.
   - Reconcile `EventType`, `AccountEventType`, `ChatEventType`, `FriendshipEventType`, `NotificationEventType`, `PresenceEventType`, `UserEventType`, and `KafkaTopics`.
   - Resolve concrete conflicts such as `account.account.created` versus `account.created`, `friend.request.sent` versus `friendship.request.sent`, and `declined` versus `rejected`.

3. Make `EventEnvelope` the primary publish API without deleting old APIs yet.
   - Keep `KafkaEventProducer.publish(String, String, EventEnvelope<?>)`.
   - Keep `RedisEventPublisher.publish(String, EventEnvelope<?>)`.
   - Mark legacy `IKafkaEvent` and `IRedisMessage` paths as compatibility-only in code and tests.

4. Migrate service publishing code to create `EventEnvelope<Payload>` directly.
   - Replace Kafka wrapper event construction such as `new ChatMessageSentEvent(...)` with neutral envelope creation.
   - Replace Redis `RedisMessage<T>` construction with neutral envelope creation.

5. Move useful validation from `RealtimeContractValidator` into `EventContractValidator`.
   - Update `DefaultKafkaEventProducer` and `DefaultRedisEventPublisher` to validate `EventMetadata`.
   - Standardize on `createdAt` naming.

6. Centralize payload registry behavior.
   - Either make `EventRegistry` the shared payload registry or delete it and keep transport registries intentionally separate.
   - If shared, migrate `DefaultKafkaEventRegistry` and `DefaultRedisEventRegistry` to use it.

7. Remove presence alias normalization from transport infrastructure.
   - Move alias handling out of `KafkaEventDispatcher`, `DefaultKafkaEventRegistry`, `JsonRedisMessageSerializer`, `DefaultRedisMessageRegistry`, `RedisMessageDispatcher`, and `RedisEventDispatcher`.
   - Put it in a domain event normalizer or presence contract helper.

8. Unwind Redis deprecated class inheritance.
   - Move active logic from `JsonRedisMessageSerializer` into `JsonRedisEventSerializer`.
   - Move active logic from `DefaultRedisMessageListener` into `RedisEventListener`.
   - Move active logic from `DefaultRedisMessageRegistry` into `DefaultRedisEventRegistry` or the shared registry.

9. Delete or relocate Kafka concrete event wrappers.
   - Remove `AccountCreatedEvent`, `ChatMessageSentEvent`, `ChatMessageEditedEvent`, `ChatMessageDeletedEvent`, `ChatReactionUpdatedEvent`, `FriendRequestKafkaEvent`, `FriendshipEvent`, and `NotificationRequestedEvent` after service callers are migrated.
   - This also removes the physical path versus declared package mismatch.

10. Split or delete version and convention leftovers.
   - Remove `RealtimeContractConventions`.
   - Remove `RealtimeEventLifecycleContract` or replace it with transport-local constants.
   - Move Kafka/Redis version constants to their owning transport modules.
   - Remove `RealtimeContractVersions` reflection into Redis.

11. Normalize module structure.
   - Align package names such as `error` versus `exception` and `logging` versus `observability`.
   - Align auto-configuration style and registration.

12. Delete compatibility APIs after usages are gone.
   - Remove `KafkaEvent`, `IKafkaEventPublisher`, `KafkaEventPublisher`, `DefaultKafkaEventPublisher`, `IRedisPublisher`, `IRedisSubscriber`, `IRedisMessageSerializer`, `RedisMessageDispatcher`, `DefaultRedisPublisher`, and other deprecated bridge classes.

# 9. Severity List

- High:
  - Three competing event models exist: `EventEnvelope`/`EventMetadata`, `IKafkaEvent`, and `IRedisMessage`/`RedisMessage`.
  - `common-kafka` owns concrete business event wrappers in `com.example.common.integration.kafka.event`.
  - Kafka wrapper files physically live under `com/example/common/kafka/event` while declaring package `com.example.common.integration.kafka.event`.
  - Event type names conflict across `EventType`, domain event enums, and `KafkaTopics`.
  - `FriendRequestKafkaEvent` hardcodes `friend.request.event`, bypassing `FriendshipEventType`.
  - `common-events` class `RealtimeContractVersions` reflects into `common-redis` class `RedisContractVersions`.

- Medium:
  - Kafka and Redis duplicate publisher, dispatcher, registry, routing context, logging, validation, and exception abstractions.
  - Presence alias normalization leaks into generic Kafka and Redis infrastructure classes.
  - Redis canonical classes still extend deprecated message implementations.
  - Kafka and Redis implement receive flow at different depths: Redis owns listener plus dispatcher; Kafka owns only dispatcher/handler abstractions.
  - `com.example.common.integration.contract` is misleading because it contains validators, field constants, and log strings rather than the real event contract.
  - Kafka and Redis auto-configuration styles are inconsistent and appear not to have Boot auto-configuration registration resources.

- Low:
  - Kafka uses `error` while Redis uses `exception`.
  - Kafka uses `logging` while Redis uses `observability`.
  - `KafkaPubSubException` and `RedisPubSubException` are similar but acceptable as transport-specific exception types.
  - `RealtimeEventLifecycleContract` shares string labels that could simply be local logger constants.
  - Deprecated compatibility APIs make the public surface larger than necessary.

# 10. Final Verdict

- Kafka and Redis are too duplicated in the shared common layer.
- They should not share one generic transport abstraction because Kafka and Redis have different delivery semantics and routing concepts.
- They should share a common contract model: `EventEnvelope<T>` plus `EventMetadata`, with one reconciled source of event type names.
- Kafka should stop owning business event wrapper classes.
- Redis should stop centering deprecated `IRedisMessage`/`RedisMessage` contracts and should serialize neutral event envelopes.
- `com.example.common.integration.contract` should be deleted after its useful validation behavior is merged into `com.example.common.event.validation.EventContractValidator`.
- The safest next move is to standardize event names and make `EventEnvelope` the primary publish/dispatch contract while keeping legacy Kafka and Redis adapters temporarily for compatibility.
