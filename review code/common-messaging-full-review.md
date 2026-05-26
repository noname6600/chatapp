# Common Messaging Full Review

Scope reviewed only:

- `chatappBE/common/common-events` as the requested `common-event`
- `chatappBE/common/common-kafka`
- `chatappBE/common/common-redis` as the requested `common-redis-pubsub`

No refactor is included in this review.

## 1. Summary

Verdict: these modules do not yet form a consistent, clean, scalable messaging architecture. The largest issue is that `common-events` does not own the event abstraction. Kafka and Redis each define their own base model, metadata shape, naming, logging context, and dispatch API.

Severity map:

| Severity | Problems |
| --- | --- |
| High | Shared event abstraction is missing from `common-events`; Kafka and Redis define incompatible base types; Redis deserialization drops explicit `eventId` and `correlationId`; transport-specific Redis/WS/Kafka concepts leak into `common-events`; Kafka event classes have package/path mismatch; presence event types violate the lower-dot validator; Kafka contains domain-specific event wrappers instead of keeping transport generic. |
| Medium | Kafka has publisher only, no common consumer/handler/serializer/registry; Redis has no `channel/` package and no common channel subscription layer; naming is inconsistent across Event/Message, Producer/Publisher, Consumer/Subscriber, Listener/Dispatcher; topic/channel constants are split into wrong modules; metadata types differ between Kafka and Redis. |
| Low | Module names do not match requested architecture (`common-events`, `common-redis`); package root uses `integration` instead of `event`; `observability` should be renamed to `logging`; `exception` should be renamed to `error`; duplicate deprecated API aliases add noise. |

Current module boundaries:

- `common-events` has no Gradle dependency on Kafka or Redis libraries. Good at dependency level, bad at naming/content level because it contains Redis/WS/Kafka-specific concepts.
- `common-kafka` depends on `common-events` and Spring Kafka. It has no Redis dependency. Good.
- `common-redis` depends on `common-events` and Spring Data Redis. It has no Kafka dependency. Good.

## 2. Event Model Issues

High: `common-events` does not define the base event abstraction.

Current `common-events` packages:

- `com.example.common.integration.account`
- `com.example.common.integration.chat`
- `com.example.common.integration.contract`
- `com.example.common.integration.enums`
- `com.example.common.integration.friendship`
- `com.example.common.integration.notification`
- `com.example.common.integration.presence`
- `com.example.common.integration.realtime`
- `com.example.common.integration.user`
- empty directory: `com.example.common.integration.websocket`

Exact missing shared abstractions:

- No `Event` or `BaseEvent` interface in `common-events`.
- No `EventMetadata` model in `common-events`.
- No shared `EventEnvelope<T>` DTO in `common-events`.
- No single shared `EventType` enum in `common-events`.
- No shared event registry contract in `common-events`.

Kafka defines its own event model:

- `com.example.common.kafka.api.IKafkaEvent<T>`
  - `UUID getEventId()`
  - `String getEventType()`
  - `String getSourceService()`
  - `Instant getCreatedAt()`
  - `T getPayload()`
  - default `getCorrelationId()` returns `eventId.toString()`
- `com.example.common.integration.kafka.event.AbstractKafkaEvent`
  - `UUID eventId`
  - `String eventType`
  - `String sourceService`
  - `Instant createdAt`
- Deprecated aliases:
  - `com.example.common.kafka.api.KafkaEvent<T>`
  - `com.example.common.kafka.api.KafkaEventPublisher`

Redis defines a separate message model:

- `com.example.common.redis.api.IRedisMessage`
  - `String getMessageId()`
  - `String getEventType()`
  - `String getSourceService()`
  - `Instant getCreatedAt()`
  - default `getEventId()` returns `getMessageId()`
  - default `getCorrelationId()` returns `getMessageId()`
- `com.example.common.redis.message.AbstractRedisMessage`
  - `String messageId`
  - `String eventId`
  - `String correlationId`
  - `String eventType`
  - `String sourceService`
  - `Instant createdAt`
- `com.example.common.redis.message.RedisMessage<T>`
  - `T payload`

This is not a shared event model. It is two transport-specific abstractions that happen to use some similar field names.

Metadata consistency issues:

- Kafka uses `UUID eventId`; Redis uses `String eventId`.
- Kafka has no explicit field for `correlationId`; `IKafkaEvent.getCorrelationId()` derives it from `eventId`.
- Redis has `messageId`, `eventId`, and `correlationId`, but `IRedisMessage` defaults event/correlation IDs to `messageId`.
- `common-events` contract names the timestamp field `FIELD_OCCURRED_AT = "occurredAt"`, while Kafka and Redis expose `createdAt`.
- User-required metadata is not represented once in a shared model:
  - `eventId`: split between Kafka UUID and Redis String.
  - `eventType`: duplicated per transport.
  - `sourceService`: duplicated per transport.
  - `createdAt`: duplicated per transport and inconsistent with `occurredAt` convention.
  - `correlationId`: absent as a real Kafka field; unstable after Redis deserialize.

High: Redis deserialization loses explicit identity metadata.

`com.example.common.redis.serialization.JsonRedisMessageSerializer.deserialize`:

- Reads `eventType`.
- Resolves payload class from `IRedisMessageRegistry`.
- Deserializes a base `RedisMessage`.
- Rebuilds a new `RedisMessage.builder()` with:
  - `.messageId(base.getMessageId())`
  - `.eventType(base.getEventType())`
  - `.sourceService(base.getSourceService())`
  - `.createdAt(base.getCreatedAt())`
  - `.payload(payloadObject)`
- It does not copy:
  - `.eventId(base.getEventId())`
  - `.correlationId(base.getCorrelationId())`

Because `AbstractRedisMessage` has Lombok-generated getters for `eventId` and `correlationId`, the returned deserialized message can have null `eventId` and null `correlationId` even if the wire payload had them. This breaks traceability for listener -> dispatcher -> subscriber flow.

High: transport-specific fields leak into `common-events`.

Exact classes:

- `com.example.common.integration.realtime.RealtimeRedisChannels`
  - Contains Redis channel names and builders.
  - Belongs in `common-redis-pubsub/channel`, not `common-event`.
- `com.example.common.integration.realtime.RealtimeContractVersions`
  - Contains `CHAT_REDIS_FANOUT`, `NOTIFICATION_KAFKA_EVENTS`, `NOTIFICATION_REDIS_FANOUT`, `PRESENCE_REDIS_FANOUT`.
  - Mixes transport-specific versioning into the event module.
- `com.example.common.integration.contract.RealtimeContractConventions`
  - Contains `CHANNEL_PREFIX_REALTIME` and `CHANNEL_PREFIX_WS`.
  - `CHANNEL_PREFIX_WS` is WebSocket terminology inside the shared event module.
- Empty package directory `com.example.common.integration.websocket`
  - Suggests WebSocket contracts are being mixed into event contracts.

High: Kafka event wrappers are transport-specific but domain-specific.

Exact package:

- `com.example.common.integration.kafka.event`

Exact classes:

- `AbstractKafkaEvent`
- `AccountCreatedEvent`
- `ChatMessageSentEvent`
- `ChatMessageEditedEvent`
- `ChatMessageDeletedEvent`
- `ChatReactionUpdatedEvent`
- `FriendshipEvent`
- `FriendRequestKafkaEvent`
- `NotificationRequestedEvent`

These classes belong neither cleanly to `common-event` nor cleanly to a generic Kafka transport module:

- They are transport wrappers because they extend `AbstractKafkaEvent`.
- They are domain-specific because they hard-code account/chat/friendship/notification payloads and event types.
- The result is that `common-kafka` owns business event shapes that should be owned by `common-event`.

Duplicated or confusing event-like models in `common-events`:

- `com.example.common.integration.friendship.FriendRequestEvent` is an event-like payload with `createdAt` and nested `Type`, but Kafka wraps it in `FriendRequestKafkaEvent`.
- `com.example.common.integration.notification.NotificationEvent` is an event-like DTO with notification metadata, while Kafka has `NotificationRequestedEvent` and `NotificationRequestedPayload`.
- Most other classes are named `*Payload`, but these are named `*Event`, causing the event vs payload boundary to blur.

High: some event type values fail the shared validator.

`RealtimeContractConventions.EVENT_NAME_PATTERN` is:

```text
^[a-z0-9-]+(\.[a-z0-9-]+)*$
```

This allows lower-dot segments with letters, numbers, and hyphens. It does not allow underscores.

Exact invalid values in `com.example.common.integration.presence.PresenceEventType`:

- `presence.user.status_changed`
- `presence.room.stop_typing`
- `presence.global.online_users`
- `presence.room.online_users`

`DefaultRedisPublisher` validates `message.getEventType()` using this pattern. Publishing Redis presence events with those values should fail validation.

## 3. Naming Issues

Current naming is inconsistent.

Event vs Message:

- Kafka uses `IKafkaEvent`, `KafkaEvent`, `AbstractKafkaEvent`, `KafkaEventRoutingContext`, `KafkaEventLogger`.
- Redis uses `IRedisMessage`, `RedisMessage`, `AbstractRedisMessage`, `RedisMessageDispatcher`, `RedisMessageFields`, `JsonRedisMessageSerializer`.
- `common-events` mixes `*Payload`, `*Event`, and `*EventType`.

Enforced convention:

- Use `Event` for system-level messaging contracts across all transports.
- Use `Payload` only for the business data inside an event.
- Use `Message` only when referring to a transport library primitive, such as Spring Redis `Message`, not for the app-level contract.

Required renames:

| Current | Target |
| --- | --- |
| `IRedisMessage` | `RedisEventEnvelope` or shared `EventEnvelope<T>` |
| `RedisMessage<T>` | `RedisEvent<T>` only if a Redis-specific wrapper remains |
| `AbstractRedisMessage` | delete after shared `EventMetadata`, or rename to `AbstractEventEnvelope` in `common-event` |
| `RedisMessageDispatcher` | `RedisEventDispatcher` |
| `JsonRedisMessageSerializer` | `JsonRedisEventSerializer` or shared `JsonEventSerializer` |
| `IRedisMessageSerializer` | `RedisEventSerializer` or shared `EventSerializer` |
| `IRedisMessageRegistry` | `RedisEventRegistry` or shared `EventRegistry` |
| `RedisMessageFields` | `EventEnvelopeFields` if shared, or `RedisEventFields` |
| `IKafkaEvent` | shared `EventEnvelope<T>` or `Event<T>` |
| `AbstractKafkaEvent` | delete after shared `EventEnvelope<T>` |
| `KafkaEventPublisher` | `KafkaEventProducer` |
| `DefaultKafkaEventPublisher` | `DefaultKafkaEventProducer` |

Producer vs Publisher:

- Kafka currently uses `IKafkaEventPublisher` and `DefaultKafkaEventPublisher`.
- Target architecture says Kafka should use `producer/`.
- Redis should keep `publisher/`.

Enforced convention:

- Kafka: `Producer` for writes, `Consumer` for reads, `Handler` for event-specific processing.
- Redis Pub/Sub: `Publisher` for writes, `Listener` for Redis network callback, `Dispatcher` for routing, `Subscriber` for app-level callbacks.

Consumer vs Subscriber:

- Kafka has no common consumer abstraction.
- Redis has `IRedisSubscriber`.

Enforced convention:

- Kafka consumers consume from durable topics.
- Redis subscribers receive fanout events after listener/dispatcher.
- Do not call Kafka handlers "subscribers".
- Do not call Redis subscribers "consumers".

Handler vs Listener vs Dispatcher:

- Redis has `DefaultRedisMessageListener` and `RedisMessageDispatcher`.
- Kafka has no handler/listener/dispatcher abstraction in this module.

Enforced convention:

- `Listener`: transport callback only. Redis can use listener because Spring Data Redis exposes `MessageListener`.
- `Dispatcher`: routes already-deserialized events to subscribers/handlers.
- `Handler`: business-facing event processor. Kafka should expose handler registration or handler interfaces under consumer flow.

Serializer naming:

- Redis has `IRedisMessageSerializer` and `JsonRedisMessageSerializer`.
- Kafka has no serializer package.

Enforced convention:

- Shared serializer contract should be `EventSerializer`.
- Transport-specific implementation names should be `JsonKafkaEventSerializer` and `JsonRedisEventSerializer` only if transport wire format truly differs.
- Otherwise use one `JsonEventEnvelopeSerializer` in `common-event` or a shared serialization subpackage.

## 4. Structure Issues

Current `common-events` structure:

```text
com.example.common.integration.account
com.example.common.integration.chat
com.example.common.integration.contract
com.example.common.integration.enums
com.example.common.integration.friendship
com.example.common.integration.notification
com.example.common.integration.presence
com.example.common.integration.realtime
com.example.common.integration.user
com.example.common.integration.websocket
```

Issues:

- Package root is `integration`, not `event`.
- There is no `metadata/`, `envelope/`, `type/`, `payload/`, or `registry/` structure.
- `realtime/` contains Redis channel constants.
- `contract/` contains transport lifecycle and WS/channel naming.
- `websocket/` package exists as an empty directory.
- Event types are split by domain, but no central event type registry exists.

Current `common-kafka` structure:

```text
com.example.common.kafka.api
com.example.common.kafka.config
com.example.common.kafka.core
com.example.common.kafka.event
com.example.common.kafka.exception
com.example.common.kafka.flow
com.example.common.kafka.observability
com.example.common.integration.kafka
```

Issues:

- Source path `com/example/common/kafka/event` declares package `com.example.common.integration.kafka.event`.
- `KafkaTopics` is in `com.example.common.integration.kafka`, not `com.example.common.kafka.topic`.
- `core/` hides producer implementation. It should be `producer/`.
- Missing `consumer/`.
- Missing `handler/` or a clear consumer handler contract.
- Missing `topic/`.
- Missing `serialization/`.
- Missing `registry/`.
- `observability/` should be `logging/` for the target architecture.
- `exception/` should be `error/` for the target architecture.
- Domain-specific event wrappers are in Kafka transport.

Current `common-redis` structure:

```text
com.example.common.redis.api
com.example.common.redis.autoconfigure
com.example.common.redis.config
com.example.common.redis.constants
com.example.common.redis.dispatcher
com.example.common.redis.exception
com.example.common.redis.flow
com.example.common.redis.listener
com.example.common.redis.message
com.example.common.redis.observability
com.example.common.redis.publisher
com.example.common.redis.registry
com.example.common.redis.serialization
```

Issues:

- Module is named `common-redis`, not `common-redis-pubsub`, even though this is a Pub/Sub abstraction.
- Package root is `redis`, not `redis.pubsub`.
- `message/` should become event envelope usage, not an app-level message model.
- Missing `channel/`.
- Channel constants are wrongly in `common-events` as `RealtimeRedisChannels`.
- `constants/RedisMessageFields` should move into serialization/envelope naming.
- `observability/` should be `logging/`.
- `exception/` should be `error/`.
- `autoconfigure.RedisAutoConfiguration` is a deprecated bridge duplicating `config.RedisAutoConfiguration`.

Missing layer comparison:

| Layer | common-events | common-kafka | common-redis |
| --- | --- | --- | --- |
| Event abstraction | Missing | Present but Kafka-specific | Present but Redis-specific |
| Metadata | Missing as model | In `AbstractKafkaEvent` | In `AbstractRedisMessage` |
| Config | N/A | Present | Present |
| Producer/Publisher | N/A | Publisher in `core/`, should be producer | Present |
| Consumer/Subscriber | N/A | Missing common consumer | Subscriber present |
| Listener | N/A | Missing | Present |
| Dispatcher | N/A | Missing | Present |
| Handler | N/A | Missing | Subscriber doubles as handler |
| Topic/Channel | Redis channel wrongly present | Kafka topics in wrong package | Missing channel package |
| Serialization | Payload annotations only | Missing | Present |
| Registry | Missing | Missing | Present |
| Logging | Lifecycle constants only | Present as observability | Present as observability |
| Error | Missing | Present as exception | Present as exception |

## 5. Flow Inconsistency

Target Kafka flow:

```text
publish -> topic -> consumer -> handler
```

Current Kafka flow in `common-kafka`:

```text
DefaultKafkaEventPublisher.publish(topic, key, IKafkaEvent<?>)
    -> validate topic and event
    -> KafkaTemplate.send(topic, key, event)
    -> KafkaEventLogger
```

Missing Kafka flow pieces:

- No `producer/` package.
- No `topic/` package.
- No `consumer/` package.
- No `handler/` abstraction.
- No deserialization/registry layer for consumers.
- No error strategy beyond publish exception wrapping.

Target Redis flow:

```text
publish -> channel -> listener -> dispatcher -> subscriber
```

Current Redis flow in `common-redis`:

```text
DefaultRedisPublisher.publish(channel, IRedisMessage)
    -> validate event
    -> IRedisMessageSerializer.serialize
    -> StringRedisTemplate.convertAndSend(channel, payload)
    -> RedisPubSubLogger

DefaultRedisMessageListener.onMessage(Message, pattern)
    -> decode channel/body
    -> IRedisMessageSerializer.deserialize
    -> RedisEventRoutingContext
    -> RedisPubSubLogger.logReceive
    -> RedisMessageDispatcher.dispatch
    -> IRedisSubscriber.onMessage
```

Redis is conceptually closer to the target flow, but still not clean:

- It publishes and dispatches `Message`, not shared `Event`.
- It has no `channel/` package.
- It does not own channel constants.
- It has no common listener container/channel subscription configuration.
- It drops explicit event identity on deserialize.

Flow alignment problem:

- Kafka exposes only outbound durable publishing.
- Redis exposes outbound and inbound realtime fanout.
- The abstraction level is not comparable. One module is a partial publisher helper; the other is a fuller Pub/Sub mini-framework.

Required alignment:

- Kafka should add common inbound abstractions: consumer, handler, event deserializer/registry, topic registry.
- Redis should rename app-level messages to events and move channels into a channel package.
- Both should use the same `EventEnvelope<T>` and `EventMetadata`.

## 6. Boundary Violations

Dependency boundary status:

| Boundary | Status |
| --- | --- |
| `common-events` has zero Gradle dependency on Kafka/Redis | Pass |
| `common-kafka` has zero dependency on Redis | Pass |
| `common-redis` has zero dependency on Kafka | Pass |
| WebSocket library dependency is absent from Kafka/Redis | Pass |
| WebSocket terminology is absent from event/redis contracts | Fail |
| Transport-specific constants absent from `common-events` | Fail |
| Domain-specific event wrappers absent from transport modules | Fail |

Specific boundary issues:

- `common-events` contains `RealtimeRedisChannels`, which is Redis-specific.
- `common-events` contains `RealtimeContractVersions` constants named for Redis fanout and Kafka events.
- `common-events` contains `RealtimeContractConventions.CHANNEL_PREFIX_WS`, which is WebSocket-specific.
- `common-events` contains an empty `integration.websocket` directory.
- `common-kafka` contains domain event wrappers in `com.example.common.integration.kafka.event`.
- `common-kafka` stores `KafkaTopics` under `com.example.common.integration.kafka` instead of a Kafka-owned `topic/` package.
- `common-redis` logger has `logForward(String destination, IRedisMessage message)` and log text `Forwarding RedisMessage to WS destination=...`, which mixes WebSocket forwarding language into the Redis Pub/Sub common module.

Business/domain logic:

- No business behavior was found in the three modules.
- However, domain-specific message contracts are misplaced in `common-kafka`:
  - `AccountCreatedEvent`
  - `ChatMessageSentEvent`
  - `ChatMessageEditedEvent`
  - `ChatMessageDeletedEvent`
  - `ChatReactionUpdatedEvent`
  - `FriendshipEvent`
  - `FriendRequestKafkaEvent`
  - `NotificationRequestedEvent`

Those should be shared event DTOs in `common-event`, or replaced by a generic shared envelope plus payload.

## 7. Duplications

Duplicated event metadata:

- Kafka: `IKafkaEvent` plus `AbstractKafkaEvent`.
- Redis: `IRedisMessage` plus `AbstractRedisMessage`.
- Shared validation: `RealtimeContractValidator`.
- Missing shared metadata model causes duplicated and inconsistent handling.

Duplicated routing context:

- `com.example.common.kafka.flow.KafkaEventRoutingContext`
- `com.example.common.redis.flow.RedisEventRoutingContext`

These are nearly the same concept with transport-specific destination fields:

- Kafka: `topic`, `key`, `eventType`, `correlationId`, `sourceService`
- Redis: `channel`, `eventType`, `correlationId`, `sourceService`

The common part should be derived from shared `EventMetadata`; transport contexts should only add destination data.

Duplicated logging shape:

- `KafkaEventLogger`
- `RedisPubSubLogger`

Both log lifecycle stage, destination, event type, correlation ID, event/message ID, source service. This should be standardized through shared metadata naming.

Event type duplication:

- `AccountEventType.ACCOUNT_CREATED = "account.account.created"`
- `KafkaTopics.ACCOUNT_CREATED = "account.account.created"`
- `ChatEventType.MESSAGE_SENT = "chat.message.sent"`
- `KafkaTopics.CHAT_MESSAGE_SENT = "chat.message.sent"`
- `ChatEventType.REACTION_UPDATED = "chat.reaction.updated"`
- `KafkaTopics.CHAT_REACTION_UPDATED = "chat.reaction.updated"`
- `NotificationEventType.REQUESTED = "notification.requested"`
- `KafkaTopics.NOTIFICATION_REQUESTED = "notification.requested"`

This repeats event names as both event types and topics without a clear rule for when topic equals event type.

Duplicated or overlapping event DTOs:

- `FriendRequestEvent` in `common-events` plus `FriendRequestKafkaEvent` in `common-kafka`.
- `NotificationEvent` in `common-events` plus `NotificationRequestedPayload` and `NotificationRequestedEvent`.
- `AccountCreatedPayload` plus `AccountCreatedEvent`.
- Chat payloads plus Kafka-specific chat event wrappers.

Serializer/registry inconsistency:

- Redis has `IRedisMessageSerializer`, `JsonRedisMessageSerializer`, `IRedisMessageRegistry`, and `DefaultRedisMessageRegistry`.
- Kafka has no matching serializer or registry layer.
- This is not direct code duplication, but it is architectural duplication waiting to happen as Kafka consumers are added.

## 8. Target Architecture

Use one shared event model and transport-specific routing only at the edges.

### common-event

Target module name:

```text
common-event
```

Target package root:

```text
com.example.common.event
```

Target structure:

```text
common-event
  src/main/java/com/example/common/event/
    Event.java
    EventEnvelope.java
    EventMetadata.java
    EventType.java
    payload/
    dto/
    registry/
    validation/
```

Required contents:

- Base event interface:

```java
public interface Event<T> {
    EventMetadata metadata();
    T payload();
}
```

- Metadata model:

```java
public record EventMetadata(
        String eventId,
        String eventType,
        String sourceService,
        Instant createdAt,
        String correlationId
) {}
```

- Shared DTO:

```java
public record EventEnvelope<T>(
        EventMetadata metadata,
        T payload
) implements Event<T> {}
```

- Shared event type enum or registry:

```text
EventType
EventTypeRegistry
```

Rules:

- No Kafka class names.
- No Redis class names.
- No WebSocket class names.
- No topic/channel constants.
- No transport lifecycle constants unless the naming is transport-neutral.
- Event type values must all pass the same lower-dot validator, with no underscores.

### common-kafka

Target package root:

```text
com.example.common.kafka
```

Target structure:

```text
common-kafka
  producer/
  consumer/
  topic/
  serialization/
  registry/
  config/
  logging/
  error/
```

Responsibilities:

- `producer/`
  - `KafkaEventProducer`
  - `DefaultKafkaEventProducer`
  - Accepts `EventEnvelope<?>`.
- `consumer/`
  - Kafka listener adapters and consumer contracts.
  - Routes consumed envelopes to handlers.
- `topic/`
  - `KafkaTopics`
  - Topic naming and mapping only.
- `serialization/`
  - Kafka envelope serializer/deserializer if not fully delegated to Spring.
- `registry/`
  - Event type to payload class mapping for Kafka consumers.
- `config/`
  - Kafka auto-configuration only.
- `logging/`
  - Kafka event logging using shared metadata.
- `error/`
  - Kafka-specific exceptions and error handling.

Kafka flow:

```text
producer.publish(topic, key, EventEnvelope)
  -> topic
  -> consumer
  -> handler
```

### common-redis-pubsub

Target module name:

```text
common-redis-pubsub
```

Target package root:

```text
com.example.common.redis.pubsub
```

Target structure:

```text
common-redis-pubsub
  publisher/
  subscriber/
  listener/
  dispatcher/
  channel/
  serialization/
  registry/
  config/
  logging/
  error/
```

Responsibilities:

- `publisher/`
  - `RedisEventPublisher`
  - `DefaultRedisEventPublisher`
  - Accepts `EventEnvelope<?>`.
- `subscriber/`
  - `RedisEventSubscriber<T>`.
- `listener/`
  - Redis transport callback only.
- `dispatcher/`
  - Routes deserialized events by `eventType`.
- `channel/`
  - `RedisChannels`
  - Realtime channel constants and channel builders.
- `serialization/`
  - Redis envelope serializer/deserializer.
- `registry/`
  - Event type to payload class mapping.
- `config/`
  - Redis Pub/Sub auto-configuration and listener container wiring.
- `logging/`
  - Redis event logging using shared metadata.
- `error/`
  - Redis-specific exceptions and error handling.

Redis flow:

```text
publisher.publish(channel, EventEnvelope)
  -> channel
  -> listener
  -> dispatcher
  -> subscriber
```

## 9. Refactor Plan

Do this in compatibility phases to avoid breaking services.

1. Add shared event model to `common-events` first.

   Add:

   - `com.example.common.event.Event`
   - `com.example.common.event.EventEnvelope<T>`
   - `com.example.common.event.EventMetadata`
   - `com.example.common.event.EventType`
   - `com.example.common.event.registry.EventRegistry`
   - `com.example.common.event.validation.EventContractValidator`

   Keep existing payloads and event type enums temporarily.

2. Normalize metadata names.

   Standardize on:

   - `eventId`
   - `eventType`
   - `sourceService`
   - `createdAt`
   - `correlationId`

   Replace `FIELD_OCCURRED_AT = "occurredAt"` with `FIELD_CREATED_AT = "createdAt"` or explicitly map old `occurredAt` as a backward-compatible alias.

3. Fix event type values before enforcing validators globally.

   Rename or alias invalid presence values:

   - `presence.user.status_changed` -> `presence.user.status-changed`
   - `presence.room.stop_typing` -> `presence.room.stop-typing`
   - `presence.global.online_users` -> `presence.global.online-users`
   - `presence.room.online_users` -> `presence.room.online-users`

   Keep deserializer aliases for old values during migration.

4. Move Redis-specific constants out of `common-events`.

   Move:

   - `RealtimeRedisChannels` -> `common-redis-pubsub/channel/RedisChannels`
   - Redis entries in `RealtimeContractVersions` -> Redis channel/version config
   - `CHANNEL_PREFIX_REALTIME` and `CHANNEL_PREFIX_WS` out of `RealtimeContractConventions`

   Keep deprecated bridge classes in `common-events` for one release if services import them.

5. Move Kafka-specific constants into Kafka topic package.

   Move:

   - `com.example.common.integration.kafka.KafkaTopics`
   - to `com.example.common.kafka.topic.KafkaTopics`

   Keep old package as a deprecated delegating alias.

6. Replace Kafka event wrappers with shared envelopes.

   Current wrappers to delete or deprecate:

   - `AbstractKafkaEvent`
   - `AccountCreatedEvent`
   - `ChatMessageSentEvent`
   - `ChatMessageEditedEvent`
   - `ChatMessageDeletedEvent`
   - `ChatReactionUpdatedEvent`
   - `FriendshipEvent`
   - `FriendRequestKafkaEvent`
   - `NotificationRequestedEvent`

   Replacement:

   - `EventEnvelope<AccountCreatedPayload>`
   - `EventEnvelope<ChatMessagePayload>`
   - `EventEnvelope<MessageUpdatedPayload>`
   - `EventEnvelope<MessageDeletedPayload>`
   - `EventEnvelope<ReactionPayload>`
   - `EventEnvelope<FriendshipPayload>`
   - `EventEnvelope<FriendRequestEvent>` or rename this payload first
   - `EventEnvelope<NotificationRequestedPayload>`

7. Rename Kafka publisher API to producer API.

   Add:

   - `KafkaEventProducer`
   - `DefaultKafkaEventProducer`

   Keep deprecated adapters:

   - `IKafkaEventPublisher`
   - `KafkaEventPublisher`
   - `DefaultKafkaEventPublisher`

   These adapters should delegate to the new producer until services migrate.

8. Rename Redis message API to event API.

   Add:

   - `RedisEventPublisher`
   - `RedisEventSubscriber<T>`
   - `RedisEventDispatcher`
   - `RedisEventListener`
   - `RedisEventSerializer`
   - `RedisEventRegistry`

   Deprecate:

   - `IRedisMessage`
   - `RedisMessage`
   - `AbstractRedisMessage`
   - `RedisMessageDispatcher`
   - `DefaultRedisMessageListener`
   - `IRedisMessageSerializer`
   - `JsonRedisMessageSerializer`
   - `IRedisMessageRegistry`
   - `DefaultRedisMessageRegistry`
   - `RedisMessageFields`

9. Fix Redis serializer identity preservation immediately.

   In the new serializer, preserve:

   - `eventId`
   - `correlationId`
   - `eventType`
   - `sourceService`
   - `createdAt`
   - payload

   During compatibility, old Redis payloads with `messageId` should map:

   - `eventId = eventId ?? messageId`
   - `correlationId = correlationId ?? eventId ?? messageId`

10. Add Kafka serialization and registry.

    Add:

    - `KafkaEventSerializer`
    - `KafkaEventDeserializer`
    - `KafkaEventRegistry`

    Make it conceptually equivalent to Redis registry/serialization.

11. Add Kafka consumer and handler contracts.

    Add:

    - `KafkaEventConsumer`
    - `KafkaEventHandler<T>`
    - dispatch by `eventType`

    This completes durable flow parity with Redis.

12. Rename packages without breaking imports.

    Move toward:

    - `com.example.common.event`
    - `com.example.common.kafka.producer`
    - `com.example.common.kafka.consumer`
    - `com.example.common.kafka.topic`
    - `com.example.common.kafka.serialization`
    - `com.example.common.kafka.registry`
    - `com.example.common.kafka.logging`
    - `com.example.common.kafka.error`
    - `com.example.common.redis.pubsub.publisher`
    - `com.example.common.redis.pubsub.subscriber`
    - `com.example.common.redis.pubsub.listener`
    - `com.example.common.redis.pubsub.dispatcher`
    - `com.example.common.redis.pubsub.channel`
    - `com.example.common.redis.pubsub.serialization`
    - `com.example.common.redis.pubsub.registry`
    - `com.example.common.redis.pubsub.logging`
    - `com.example.common.redis.pubsub.error`

    Keep deprecated bridge packages for existing service imports.

13. Remove deprecated bridges only after services migrate.

    Delete after all services stop importing old APIs:

    - Deprecated Kafka aliases.
    - Deprecated Redis message APIs.
    - `com.example.common.integration.kafka.*`.
    - `RealtimeRedisChannels` in `common-events`.
    - Empty `integration.websocket` package.

14. Add contract tests.

    Required tests:

    - Shared event envelope serialization round-trip.
    - Kafka producer uses shared metadata.
    - Kafka consumer deserializes event type to payload.
    - Redis publisher serializes shared envelope.
    - Redis listener preserves `eventId` and `correlationId`.
    - Event type validator accepts every `EventType` value.
    - Old wire format compatibility tests during migration.

## 10. Final Verdict

Strict verdict: fail for architecture consistency.

The modules have useful pieces, but the event model is in the wrong place. `common-events` is currently a payload/constants module, not the messaging contract authority. Kafka and Redis each invented their own abstraction, and Redis calls it a message while Kafka calls it an event. This will not scale cleanly as more services, event types, consumers, and realtime fanout paths are added.

Required final direction:

- Make `common-event` the single source of truth for event envelope, metadata, event type, payload DTOs, and validation.
- Make `common-kafka` a durable streaming transport that only knows topics, producers, consumers, serialization, registry, logging, and Kafka errors.
- Make `common-redis-pubsub` a realtime fanout transport that only knows channels, publishers, listeners, dispatchers, subscribers, serialization, registry, logging, and Redis errors.
- Remove transport-specific constants from `common-event`.
- Remove domain-specific event wrappers from `common-kafka`.
- Rename Redis app-level `Message` abstractions to `Event` abstractions.
- Preserve compatibility through deprecated adapters, old package bridges, and serializer aliases while services migrate.
