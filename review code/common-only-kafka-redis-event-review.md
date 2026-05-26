## 1. Executive Summary

- The `common` layer currently contains a shared event model in `common-events`, Kafka transport utilities in `common-kafka`, Redis pub/sub utilities in `common-redis`, Redis cache utilities in `common-redis-cache`, a realtime policy package in `common-web`, and a websocket wire envelope in `common-websocket`.
- The compile boundary is clean for the primary modules: `common-kafka` and `common-redis` depend on `common-events`; `common-events` does not import Kafka or Redis. `common-redis-cache` is separate from the event flow and does not depend on `common-events`.
- The architecture is only partially coherent. The basic dependency direction is correct, but the design is fragmented by duplicate registries, conflicting event catalogs, inconsistent transport naming, inconsistent serializer ownership, and misplaced realtime/common contract concepts.
- Biggest structural issues inside `common`:
  - `common-events/src/main/java/com/example/common/event/EventType.java` conflicts with domain-specific event enums under `common-events/src/main/java/com/example/common/integration/**`.
  - `common-events/src/main/java/com/example/common/event/registry/EventRegistry.java` overlaps with `common-kafka/.../registry/KafkaEventRegistry.java` and `common-redis/.../registry/RedisEventRegistry.java`.
  - Kafka and Redis model the same event pipeline with different names: Kafka uses producer/consumer/handler; Redis uses publisher/subscriber/listener.
  - Kafka declares serializer/deserializer interfaces but has no common implementation, while Redis owns a concrete JSON serializer/deserializer.
  - Redis cache and Redis pub/sub are module-separated but share the same Java package root, `com.example.common.redis`, which makes the separation weaker than the Gradle module split.
  - `common-web/src/main/java/com/example/common/realtime/policy/*` contains messaging/realtime flow policy that is not really web-specific.
- Previous cleanup is not enough. The current code is better than a fully tangled design, but it still has two competing standards: shared event contracts in `common-events`, and transport-local abstractions in Kafka/Redis that partially re-create event concepts.

## 2. Common Layer Boundary

- Current responsibilities inside `common`:
  - `common-events`: shared event envelope, metadata, event name validation, event catalogs, integration DTOs, payload contracts, and one generic event registry abstraction.
  - `common-kafka`: Kafka publishing, handler dispatching, payload registry, topic constants, logging, exceptions, and auto-configuration.
  - `common-redis`: Redis pub/sub publishing, listener adaptation, dispatching, payload registry, channel constants, JSON event serialization, logging, exceptions, and configuration.
  - `common-redis-cache`: time-based Redis cache API and implementation.
  - `common-web`: realtime flow classification policy.
  - `common-websocket`: websocket-facing realtime event envelope.
- Responsibilities that belong in `common`:
  - Transport-agnostic event model: `Event`, `EventEnvelope`, `EventMetadata`.
  - Transport-agnostic event validation rules.
  - Shared integration payload DTOs and event-name contracts, if they are intended to be common contracts.
  - Kafka-specific transport adapters, serializers, topic definitions, publishers, listeners, and dispatchers in `common-kafka`.
  - Redis-specific pub/sub adapters, serializers, channel definitions, publishers, listeners, and dispatchers in `common-redis`.
  - Redis cache abstractions in a cache-specific package/module, separate from Redis pub/sub.
- Responsibilities that look misplaced even though they are still inside `common`:
  - `common-events/src/main/java/com/example/common/event/EventType.java` is a global event catalog that competes with the domain-specific enums in `common-events/src/main/java/com/example/common/integration/**`.
  - `common-events/src/main/java/com/example/common/event/registry/EventRegistry.java` is a generic payload registry abstraction, but the actual transport modules each define their own registry. It is common in name only.
  - `common-web/src/main/java/com/example/common/realtime/policy/RealtimeFlowClassificationPolicy.java`, `RealtimeFlowId.java`, and `RealtimeFlowType.java` describe messaging delivery semantics, not web behavior. They belong closer to event/realtime contracts if retained.
  - `common-websocket/src/main/java/com/example/common/websocket/protocol/RealtimeWsEvent.java` is fine as a websocket wire envelope, but it should not become a second domain event envelope.
  - Empty packages under `common-events/src/main/java/com/example/common/integration/contract`, `common-events/src/main/java/com/example/common/integration/realtime`, and `common-events/src/main/java/com/example/common/integration/websocket` should not exist as placeholders.

## 3. Module-by-Module Review

### common-event

- Actual module name: `common-events`. The requested `common-event` concept maps to `chatappBE/common/common-events`.
- It currently contains:
  - `com.example.common.event.Event`
  - `com.example.common.event.EventEnvelope`
  - `com.example.common.event.EventMetadata`
  - `com.example.common.event.EventType`
  - `com.example.common.event.registry.EventRegistry`
  - `com.example.common.event.validation.EventContractValidator`
  - Domain integration DTOs and enums under `com.example.common.integration.account`, `chat`, `friendship`, `notification`, `presence`, `user`, and `enums`.
- What belongs here:
  - `Event`, `EventEnvelope`, `EventMetadata`.
  - Transport-neutral validation in `EventContractValidator`, but only for event name syntax and metadata completeness.
  - Domain event payload contracts and domain event-name enums, if `common-events` is the chosen ownership point for shared contracts.
- What should not be here:
  - A duplicate global event-name catalog if domain enums are the real contract source. `EventType.java` should either be deleted or generated from the domain-specific enums.
  - Transport dispatch or registry concepts unless there is one shared payload registry used by both Kafka and Redis. The current `EventRegistry.java` is not used by the common Kafka/Redis main code and does not currently justify its existence.
  - Realtime delivery policy that talks about durable-first or ephemeral-only flow should not be split into `common-web` if it is part of common event architecture.
- Transport-agnostic verdict:
  - Mostly transport-agnostic at the import/dependency level. `Event`, `EventEnvelope`, `EventMetadata`, and `EventContractValidator` do not import Kafka or Redis.
  - Not fully pure in design language. Javadocs in `Event.java` and related event classes describe Kafka/Redis usage, which weakens the conceptual boundary even if it does not create a compile dependency.
  - The metadata model is transport-neutral enough: `eventId`, `eventType`, `sourceService`, `createdAt`, and `correlationId` are event-level metadata, not Kafka/Redis-specific metadata.

### common-kafka

- The abstraction set is recognizable but not clean enough:
  - `KafkaEventProducer` / `DefaultKafkaEventProducer` publish `EventEnvelope<?>`.
  - `KafkaEventHandler` and `KafkaEventDispatcher` route envelopes by `metadata.eventType`.
  - `KafkaEventConsumer` is a weak marker-like abstraction with default empty behavior.
  - `KafkaEventRegistry` / `DefaultKafkaEventRegistry` map event type names to payload classes.
  - `KafkaEventSerializer` and `KafkaEventDeserializer` are declared but not implemented or wired in the common module.
- Package structure is only partly coherent:
  - Good: `producer`, `consumer`, `registry`, `serialization`, `topic`, `config`.
  - Inconsistent: `error` is used here while Redis uses `exception`.
  - Inconsistent: `logging` is used here while Redis uses `observability`.
  - Inconsistent: Kafka calls outbound delivery `producer`, Redis calls outbound delivery `publisher`.
- Naming is inconsistent with Redis:
  - Kafka: producer, consumer, handler.
  - Redis: publisher, subscriber, listener.
  - The transport-specific Spring adapter should be called listener/consumer; the application callback should be called handler; outbound event delivery should use one common word across transports.
- Kafka overlaps with event/common contracts too much:
  - `KafkaEventRegistry` duplicates the concept of `EventRegistry`.
  - `KafkaEventHandler.eventType()` duplicates event registration concerns.
  - `KafkaTopics` contains constants that are sometimes event names, not only Kafka topic names.
- `KafkaTopics.java` is structurally weak:
  - `ACCOUNT_CREATED = "account.account.created"` and `CHAT_MESSAGE_SENT = "chat.message.sent"` look like semantic event types.
  - `FRIENDSHIP_EVENTS = "friendship.events"` and `FRIENDSHIP_REQUEST_EVENTS = "friendship.request.events"` look like stream topics.
  - `DEAD_LETTER = "system.dead-letter"` and `RETRY = "system.retry"` look like infrastructure topics.
  - One class is mixing semantic event names, aggregate stream topics, and infrastructure topics.

### common-redis

- Responsibilities are mostly clear for pub/sub:
  - `RedisEventPublisher` publishes envelopes to channels.
  - `RedisEventListener` adapts Spring Redis messages into common envelopes.
  - `RedisEventDispatcher` routes deserialized envelopes to subscribers.
  - `RedisEventSubscriber` is the application callback abstraction.
  - `RedisEventRegistry` maps event types to payload classes for deserialization.
  - `RedisEventSerializer` and `JsonRedisEventSerializer` own wire serialization.
- Cache and pub/sub are separated at Gradle module level:
  - Pub/sub: `chatappBE/common/common-redis`.
  - Cache: `chatappBE/common/common-redis-cache`.
- Cache and pub/sub are not separated cleanly at Java package level:
  - `common-redis-cache/src/main/java/com/example/common/redis/api/*`
  - `common-redis-cache/src/main/java/com/example/common/redis/core/*`
  - Pub/sub also uses `com.example.common.redis.*`.
  - The target package for cache should be `com.example.common.redis.cache.*` or the module should be merged into one Redis module with explicit `cache` and `pubsub` packages.
- Naming and structure are inconsistent with Kafka:
  - Redis has a concrete serializer while Kafka has only interfaces.
  - Redis uses `publisher`; Kafka uses `producer`.
  - Redis uses `subscriber` for application callbacks; Kafka uses `handler`.
  - Redis uses `listener` for the Spring adapter; Kafka has no equivalent wired listener class in common.
- `RedisChannels.java` is acceptable as a Redis route catalog, but it should contain channel names only. It should not become a substitute event catalog.
- `RedisContractVersions.java` appears to be historical baggage. It is not referenced inside common and encodes domain-specific fanout versions in a transport module without an active integration point.

## 4. Dependency Direction Inside Common

- Exact dependency direction from Gradle module declarations:
  - `common-kafka` -> `common-events`.
  - `common-redis` -> `common-events`.
  - `common-websocket` -> `common-events`.
  - `common-redis-cache` has no dependency on `common-events`.
  - `common-events` does not depend on `common-kafka`, `common-redis`, `common-redis-cache`, `common-websocket`, or `common-web`.
- Correct dependency directions:
  - `common-kafka` depending on `common-events` is correct. Transport adapters should depend on the shared event envelope and contracts.
  - `common-redis` depending on `common-events` is correct for Redis pub/sub.
  - `common-websocket` depending on `common-events` is acceptable if websocket protocol code needs common event payload contracts.
  - `common-redis-cache` staying independent from `common-events` is correct because cache concerns are not event/pubsub concerns.
- Violations:
  - No hard compile dependency violation was found inside common for the primary modules. `common-events` does not import Kafka or Redis.
  - The main violation is conceptual rather than compile-time: `common-events` declares `EventRegistry`, while `common-kafka` and `common-redis` each declare separate transport-specific registries for the same payload-resolution responsibility.
  - `common-web` owns `com.example.common.realtime.policy`, but those classes describe messaging flow semantics. That is a misplaced common responsibility, even though it is still inside the `common` folder.
- Wrong abstraction dependencies:
  - Kafka and Redis depend on common event envelopes, which is right.
  - Kafka and Redis do not depend on the generic `EventRegistry`, so the shared registry abstraction is detached from the actual transport implementations.
  - Redis deserialization depends on `RedisEventRegistry`, while Kafka deserialization has only unused serializer/deserializer abstractions. The two transports depend on different abstractions for the same payload-resolution job.

## 5. Inconsistency Audit

- Package naming:
  - `common-events` module uses package root `com.example.common.event` for envelope/model classes, but `com.example.common.integration.*` for event contracts and DTOs.
  - `common-redis-cache` uses `com.example.common.redis.*`, the same root as `common-redis`, instead of `com.example.common.redis.cache.*`.
  - Kafka exception package is `com.example.common.kafka.error`; Redis exception package is `com.example.common.redis.exception`.
  - Kafka logger package is `com.example.common.kafka.logging`; Redis logger package is `com.example.common.redis.observability`.
  - `common-web/src/main/java/com/example/common/realtime/policy` is not web-specific despite being physically in `common-web`.
- Module naming:
  - The event module is named `common-events`, while the conceptual area is singular `common-event`.
  - `common-redis` and `common-redis-cache` are separate modules but share package roots.
  - `common-websocket/build.gradle` describes itself as `Common Redis Library`, which is plainly wrong metadata inside common.
- Class naming:
  - `KafkaTopics` and `RedisChannels` are parallel catalogs, but one uses plural transport object names and the other uses channel naming. This is acceptable, but the contents are not parallel.
  - `RedisContractVersions` is named like a contract version catalog but lives in a Redis transport package and is not connected to a serializer, envelope, or version negotiation mechanism.
  - `RealtimeWsEvent` is a websocket envelope, but the name is close enough to domain event wording that it should remain clearly isolated under websocket protocol.
- Interface naming:
  - `IKafkaEventLogger` and `IRedisPubSubLogger` use an `I` prefix, while `KafkaEventProducer`, `RedisEventPublisher`, `KafkaEventRegistry`, and `RedisEventRegistry` do not.
  - `ITimeRedisCache` and `ITimeRedisCacheManager` also use the `I` prefix, which makes cache style different from event transport style.
- Event naming:
  - `EventType.ACCOUNT_CREATED` uses `account.created`.
  - `AccountEventType.ACCOUNT_CREATED` uses `account.account.created`.
  - `EventType.FRIENDSHIP_REQUEST_SENT` uses `friendship.request.sent`.
  - `FriendshipEventType.FRIEND_REQUEST_SENT` uses `friend.request.sent`.
  - `EventType.USER_CREATED` uses `user.created`.
  - `UserEventType.USER_PROFILE_CREATED` uses `user.profile.created`.
  - `ChatEventType` includes `chat.message.pinned`, `chat.message.unpinned`, and room membership events that are not present in `EventType`.
  - `PresenceEventType` includes deprecated underscore alias mapping logic while `EventContractValidator` explicitly bans underscores.
- Topic naming:
  - `KafkaTopics.ACCOUNT_CREATED = "account.account.created"` is event-type-shaped.
  - `KafkaTopics.FRIENDSHIP_EVENTS = "friendship.events"` is stream-topic-shaped.
  - `KafkaTopics.DEAD_LETTER = "system.dead-letter"` is infra-topic-shaped.
  - These should not be mixed without naming that distinguishes event names from topics.
- Redis channel naming:
  - `RedisChannels.CHAT_ROOM_PREFIX = "realtime.chat.room."` uses dynamic channel routing.
  - `RedisChannels.PRESENCE_USER = "realtime.presence.user"` is static.
  - `RedisChannels.PRESENCE_PATTERN = "realtime.presence.*"` is a subscription pattern.
  - Prefixes, exact channels, and patterns live in one flat class without type distinction.
- DTO/envelope naming:
  - `EventEnvelope` is the internal/common event envelope.
  - `RealtimeWsEvent` is another envelope-like shape for websocket payloads.
  - The split is acceptable only if `RealtimeWsEvent` remains a websocket protocol DTO and never becomes a second event contract standard.
- Serializer/deserializer naming:
  - Kafka has `KafkaEventSerializer` and `KafkaEventDeserializer` as separate interfaces.
  - Redis has one combined `RedisEventSerializer` interface with both `serialize` and `deserialize`, plus `JsonRedisEventSerializer`.
  - This is two different patterns for the same responsibility.
- Publisher/subscriber/consumer/listener/handler/dispatcher naming:
  - Kafka outbound: `KafkaEventProducer`.
  - Redis outbound: `RedisEventPublisher`.
  - Kafka callback: `KafkaEventHandler`.
  - Redis callback: `RedisEventSubscriber`.
  - Kafka input abstraction: `KafkaEventConsumer`.
  - Redis input adapter: `RedisEventListener`.
  - Both use `Dispatcher`, which should become the standard routing term.
- Registry/factory/resolver naming:
  - `EventRegistry` in `common-events`.
  - `KafkaEventRegistry` in `common-kafka`.
  - `RedisEventRegistry` in `common-redis`.
  - All represent payload resolution by event type, but only the transport-specific registries are wired.
- Folder structure:
  - Kafka: `config`, `consumer`, `error`, `flow`, `logging`, `producer`, `registry`, `serialization`, `topic`.
  - Redis: `channel`, `config`, `dispatcher`, `exception`, `flow`, `listener`, `observability`, `publisher`, `registry`, `serialization`, `subscriber`.
  - Redis has dedicated `dispatcher`; Kafka puts dispatcher under `consumer`.
  - Kafka has `topic`; Redis has `channel`. That distinction is fine, but both should have the same internal layout style.
- Abstraction style:
  - Kafka has some abstractions without implementations (`KafkaEventSerializer`, `KafkaEventDeserializer`, `KafkaEventConsumer`).
  - Redis has concrete end-to-end pub/sub serialization and listener flow.
  - `common-events` has a generic registry abstraction that neither transport uses.

## 6. Duplicate or Overlapping Responsibilities

- `common-events/src/main/java/com/example/common/event/EventType.java` vs domain enums in `common-events/src/main/java/com/example/common/integration/**`
  - Problem: two event catalogs represent the same concept with different values.
  - Standard: use domain-specific event enums as the source of truth, grouped by domain. Delete `EventType` or generate it from domain enums.
- `common-events/src/main/java/com/example/common/event/registry/EventRegistry.java` vs `common-kafka/src/main/java/com/example/common/kafka/registry/KafkaEventRegistry.java` vs `common-redis/src/main/java/com/example/common/redis/registry/RedisEventRegistry.java`
  - Problem: three payload registry abstractions exist for one concept.
  - Standard: define one shared `EventPayloadRegistry` in `common-events` if payload resolution is transport-neutral, then let Kafka/Redis depend on it. If the transports need different behavior, delete the generic `EventRegistry`.
- `KafkaEventHandler` vs `RedisEventSubscriber`
  - Problem: both are event callbacks selected by `metadata.eventType`, but they use different names.
  - Standard: use `EventHandler` as the callback concept in both transports, e.g. `KafkaEventHandler` and `RedisEventHandler`.
- `KafkaEventProducer` vs `RedisEventPublisher`
  - Problem: both publish event envelopes to a transport route.
  - Standard: use `Publisher` for common outbound abstraction names, e.g. `KafkaEventPublisher` and `RedisEventPublisher`. Kafka can still use Spring's `KafkaTemplate` internally.
- `KafkaEventSerializer` / `KafkaEventDeserializer` vs `RedisEventSerializer`
  - Problem: Kafka splits serializer/deserializer and does not wire them; Redis combines both and implements JSON.
  - Standard: either use a single bidirectional codec interface for both transports, or split both into serializer/deserializer. The cleaner common standard is `EventEnvelopeCodec` per transport with explicit `serialize` and `deserialize` methods.
- `KafkaTopics` vs `EventType` / domain event enums
  - Problem: `KafkaTopics` contains values that look like event types.
  - Standard: transport route constants must be route names only. Event type names belong in `common-events`.
- `RedisChannels` vs realtime/domain event naming
  - Problem: `RedisChannels` is route-oriented, but its domain-specific names can become confused with event contracts.
  - Standard: keep Redis channel definitions in `common-redis`, but call them route/channel constants and do not use them as event names.
- `KafkaEventRoutingContext` vs `RedisEventRoutingContext`
  - Problem: both appear to model routing metadata for transport-specific flows.
  - Standard: keep transport routing context transport-specific only if it carries route-specific data. Shared envelope metadata belongs in `EventMetadata`.
- `RealtimeFlowClassificationPolicy` vs transport route definitions
  - Problem: realtime flow classification is neither Kafka-specific nor Redis-specific, but it affects both messaging choices and currently lives in `common-web`.
  - Standard: move durable/ephemeral/mixed flow policy to a neutral event or realtime contract package inside common.
- `RealtimeWsEvent` vs `EventEnvelope`
  - Problem: both are envelope-like DTOs.
  - Standard: `EventEnvelope` is the internal event envelope. `RealtimeWsEvent` is a websocket client protocol DTO only. Do not merge them unless websocket delivery is intended to expose the internal event envelope directly.

## 7. Contract Package Verdict

- `common-events/src/main/java/com/example/common/integration/**`
  - Why it exists: it holds shared integration payload DTOs and event-name enums for account, chat, friendship, notification, presence, and user flows.
  - Value: it still adds value if the common layer is meant to own cross-module event contracts.
  - Problem: it is not consistently treated as the source of truth because `EventType.java` also declares event names.
  - Verdict: keep the domain-specific integration packages, but standardize them as the only event contract catalog.
- `common-events/src/main/java/com/example/common/integration/contract`
  - Why it exists: likely historical placeholder for shared contracts.
  - Value: none in the current common code because it contains no source files.
  - Verdict: remove the empty package directory. Recreate it only if it will contain deliberate interfaces such as `IntegrationEventName` or `EventPayloadContract`.
- `common-events/src/main/java/com/example/common/integration/realtime`
  - Why it exists: likely placeholder for realtime contracts.
  - Value: none in the current common code because it contains no source files.
  - Verdict: remove the empty package directory, or move `RealtimeFlowId`, `RealtimeFlowType`, and `RealtimeFlowClassificationPolicy` here if realtime flow policy is part of event contract architecture.
- `common-events/src/main/java/com/example/common/integration/websocket`
  - Why it exists: likely placeholder for websocket integration contracts.
  - Value: none in the current common code because it contains no source files.
  - Verdict: remove the empty package directory. Websocket protocol DTOs already belong in `common-websocket`.
- `common-websocket/src/main/java/com/example/common/websocket/protocol/RealtimeWsEvent.java`
  - Why it exists: it defines a websocket protocol envelope.
  - Value: yes, if the client-facing websocket protocol intentionally differs from internal `EventEnvelope`.
  - Verdict: keep in `common-websocket`, but do not move it into `common-events` unless the internal event envelope and websocket protocol envelope are intentionally unified.

## 8. Dead Code / Legacy / Removable

- `common-events/src/main/java/com/example/common/event/EventType.java`
  - Appears redundant because domain-specific event enums exist under `common-events/src/main/java/com/example/common/integration/**`.
  - It is also inconsistent with those enums. Delete it after moving registration/validation to the domain enums, or regenerate it from them.
- `common-events/src/main/java/com/example/common/event/registry/EventRegistry.java`
  - Appears unused by common Kafka/Redis main code.
  - Safe to delete if the target is transport-local registries. If the target is shared payload resolution, rename and wire it as the single registry.
- `common-events/src/main/java/com/example/common/integration/contract`
  - Empty package. Remove.
- `common-events/src/main/java/com/example/common/integration/realtime`
  - Empty package. Remove or populate deliberately by moving realtime policy from `common-web`.
- `common-events/src/main/java/com/example/common/integration/websocket`
  - Empty package. Remove.
- `common-redis/src/main/java/com/example/common/redis/config/RedisContractVersions.java`
  - Not referenced inside common and has no active version negotiation role.
  - Remove after confirming no common migration path needs it; if versioning is needed, move version metadata into event contracts or serializers.
- `common-kafka/src/main/java/com/example/common/kafka/serialization/KafkaEventSerializer.java`
  - Wrapper-only interface with no common implementation or wiring.
  - Implement and wire it, or remove it until Kafka actually owns serialization.
- `common-kafka/src/main/java/com/example/common/kafka/serialization/KafkaEventDeserializer.java`
  - Wrapper-only interface with no common implementation or wiring.
  - Implement and wire it, or remove it until Kafka actually owns deserialization.
- `common-kafka/src/main/java/com/example/common/kafka/consumer/KafkaEventConsumer.java`
  - Weak abstraction with default empty handlers and unhandled-event behavior.
  - Delete or replace with a real listener adapter equivalent to `RedisEventListener`.
- `common-kafka/src/main/java/com/example/common/kafka/flow/KafkaEventRoutingContext.java` and `common-redis/src/main/java/com/example/common/redis/flow/RedisEventRoutingContext.java`
  - Review for actual value. If they only duplicate metadata already in `EventMetadata` plus a route name, keep transport-specific route context only where it is actively used.
- Phase/history comments in common source files:
  - `KafkaTopics.java`, `RedisChannels.java`, Kafka registry/serializer/producer/consumer classes, and Redis/Kafka flow classes contain phase-history comments.
  - These are legacy migration notes and should be removed from stable common APIs.

## 9. Target Structure for Common

- Target `common-events` responsibilities:
  - Own the transport-neutral event model.
  - Own event metadata and validation.
  - Own shared event payload DTOs.
  - Own event-name contracts by domain.
  - Optionally own one shared payload registry/codec contract if both Kafka and Redis use it.
- Target `common-events` package structure:
  - `com.example.common.event.model.Event`
  - `com.example.common.event.model.EventEnvelope`
  - `com.example.common.event.model.EventMetadata`
  - `com.example.common.event.name.EventName` or domain enum contract interface
  - `com.example.common.event.validation.EventContractValidator`
  - `com.example.common.event.registry.EventPayloadRegistry` only if it is actually used by both transports
  - `com.example.common.integration.<domain>.<Domain>EventType`
  - `com.example.common.integration.<domain>.<payload DTOs>`
- Target `common-kafka` responsibilities:
  - Own Kafka route/topic definitions.
  - Own Kafka publisher implementation.
  - Own Kafka listener/consumer adapter if common provides inbound support.
  - Own Kafka event handler dispatcher.
  - Own Kafka serializer/deserializer/codec only if Kafka uses a custom common wire format.
  - Own Kafka logging/observability and exceptions.
- Target `common-kafka` package structure:
  - `com.example.common.kafka.config`
  - `com.example.common.kafka.route` or `topic`
  - `com.example.common.kafka.publisher`
  - `com.example.common.kafka.listener`
  - `com.example.common.kafka.handler`
  - `com.example.common.kafka.dispatcher`
  - `com.example.common.kafka.codec`
  - `com.example.common.kafka.registry` only if not replaced by shared `EventPayloadRegistry`
  - `com.example.common.kafka.observability`
  - `com.example.common.kafka.exception`
- Target `common-redis` responsibilities:
  - Own Redis pub/sub route/channel definitions.
  - Own Redis pub/sub publisher implementation.
  - Own Redis message listener adapter.
  - Own Redis event handler dispatcher.
  - Own Redis event envelope codec.
  - Own Redis logging/observability and exceptions.
- Target `common-redis` package structure:
  - `com.example.common.redis.pubsub.config`
  - `com.example.common.redis.pubsub.channel`
  - `com.example.common.redis.pubsub.publisher`
  - `com.example.common.redis.pubsub.listener`
  - `com.example.common.redis.pubsub.handler`
  - `com.example.common.redis.pubsub.dispatcher`
  - `com.example.common.redis.pubsub.codec`
  - `com.example.common.redis.pubsub.registry` only if not replaced by shared `EventPayloadRegistry`
  - `com.example.common.redis.pubsub.observability`
  - `com.example.common.redis.pubsub.exception`
  - `com.example.common.redis.cache.*` for cache APIs and implementations if `common-redis-cache` remains separate.
- Naming standard:
  - Use `EventEnvelope` only for the internal common event envelope.
  - Use `<Domain>EventType` for semantic event names.
  - Use `<Transport>EventPublisher` for outbound event publishing.
  - Use `<Transport>EventListener` for transport/framework inbound adapters.
  - Use `<Transport>EventHandler` for application callback interfaces.
  - Use `<Transport>EventDispatcher` for eventType-to-handler routing.
  - Use `<Transport>EventCodec` or paired `<Transport>EventSerializer` / `<Transport>EventDeserializer`, but use the same pattern for Kafka and Redis.
  - Use `<Transport>Routes`, `KafkaTopics`, and `RedisChannels` only for transport route names, not semantic event names.
- Ownership of contracts:
  - Event names and payload DTOs belong to `common-events`.
  - Kafka topics belong to `common-kafka`.
  - Redis channels belong to `common-redis`.
  - Websocket protocol envelopes belong to `common-websocket`.
- Ownership of serializers:
  - Event-neutral JSON/Jackson envelope rules may live in `common-events` only if every transport shares the exact same wire format.
  - Transport-specific codecs belong in their transport modules.
  - Do not keep Kafka serializer/deserializer interfaces without an implementation or integration point.
- Ownership of publishers/subscribers/consumers:
  - Publisher abstractions belong in transport modules.
  - Listener adapters belong in transport modules.
  - Handler interfaces belong in transport modules unless a single generic event handler is introduced in `common-events`.
- Ownership of topic/channel definitions:
  - `KafkaTopics` owns Kafka topic names only.
  - `RedisChannels` owns Redis channel names and patterns only.
  - Domain event enums own semantic event type names only.

## 10. Refactor Order

1. Remove empty common contract placeholders first:
   - Delete `common-events/src/main/java/com/example/common/integration/contract`.
   - Delete `common-events/src/main/java/com/example/common/integration/realtime` unless it is immediately populated.
   - Delete `common-events/src/main/java/com/example/common/integration/websocket`.
2. Standardize the event-name source of truth:
   - Keep domain-specific enums under `common-events/src/main/java/com/example/common/integration/**`.
   - Reconcile conflicting names in `EventType.java`, `AccountEventType`, `FriendshipEventType`, `UserEventType`, `ChatEventType`, and `PresenceEventType`.
   - Delete `EventType.java` or make it generated/delegating, not manually competing.
3. Decide the registry model:
   - If payload resolution is shared, replace `EventRegistry`, `KafkaEventRegistry`, and `RedisEventRegistry` with one `EventPayloadRegistry` in `common-events`.
   - If payload resolution is transport-specific, delete `EventRegistry.java` and keep transport registries.
4. Standardize Kafka/Redis naming:
   - Rename Kafka outbound abstraction toward `KafkaEventPublisher`, or explicitly document why Kafka alone keeps `Producer`.
   - Rename Redis callback abstraction toward `RedisEventHandler`, keeping `RedisEventListener` for the Spring adapter.
   - Align `error`/`exception` and `logging`/`observability` package names.
5. Standardize serializer/codec ownership:
   - Either implement and wire Kafka JSON envelope serialization/deserialization, or remove the unused Kafka serializer/deserializer interfaces.
   - Decide whether Redis `RedisEventSerializer` becomes `RedisEventCodec` for naming symmetry.
6. Separate Redis cache packages:
   - Move `common-redis-cache/src/main/java/com/example/common/redis/api/*` and `core/*` under `com.example.common.redis.cache.*`.
   - Keep cache independent from `common-events`.
7. Move realtime flow policy if retained:
   - Move `RealtimeFlowClassificationPolicy`, `RealtimeFlowId`, and `RealtimeFlowType` out of `common-web` into a neutral common event/realtime package.
   - Keep `RealtimeWsEvent` in `common-websocket`.
8. Clean transport route catalogs:
   - Split `KafkaTopics` constants into semantic topic routes, stream topics, and infrastructure topics, or rename constants so the distinction is explicit.
   - Keep `RedisChannels` as channel/pattern/prefix definitions only.
9. Clean auto-configuration consistency:
   - Align conditional bean behavior between Kafka and Redis.
   - Inject logger interfaces instead of concrete logger classes where possible.
   - If these are intended as Spring Boot auto-configurations, add/verify proper auto-configuration metadata inside the common modules.
10. Delete legacy and wrapper-only abstractions last:
   - Delete `RedisContractVersions` if no common-owned versioning strategy uses it.
   - Delete phase-history comments from stable APIs.
   - Delete weak wrappers that remain unused after the registry and serializer decisions.

## 11. Final Verdict

- Should stay:
  - `Event`, `EventEnvelope`, `EventMetadata`.
  - Domain payload DTOs and domain event enums under `common-events/src/main/java/com/example/common/integration/**`, after naming reconciliation.
  - Kafka and Redis transport modules as separate modules.
  - `RedisChannels` and `KafkaTopics`, but only as transport route catalogs.
  - `RealtimeWsEvent` in `common-websocket` as a client protocol DTO.
- Should move:
  - `RealtimeFlowClassificationPolicy`, `RealtimeFlowId`, and `RealtimeFlowType` should move from `common-web` to a neutral event/realtime contract package if they remain part of common messaging architecture.
  - `common-redis-cache` Java packages should move under `com.example.common.redis.cache.*`.
- Should merge:
  - Payload registry abstractions should merge into one shared `EventPayloadRegistry` if both Kafka and Redis need the same mapping behavior.
  - Serializer/codec conventions should merge into one naming and ownership pattern across Kafka and Redis.
- Should split:
  - `KafkaTopics` should split or rename constants so event-shaped topic names, aggregate stream topics, and infrastructure topics are not presented as one undifferentiated catalog.
  - `RedisChannels` can remain one class for now, but exact channels, prefixes, and patterns should be made clearer if the catalog grows.
- Should be removed:
  - Empty packages `integration.contract`, `integration.realtime`, and `integration.websocket`.
  - `EventType.java`, unless it becomes generated/delegating from the domain enums.
  - `EventRegistry.java`, unless it becomes the one wired shared registry.
  - `RedisContractVersions.java`, unless a real common versioning mechanism is added.
  - Unwired Kafka serializer/deserializer interfaces, unless implemented and connected.
  - `KafkaEventConsumer.java`, unless it becomes a real Kafka listener/consumer adapter.
- Long-term verdict:
  - The current `common` architecture is not clean enough for long-term reuse. Its dependency direction is mostly right, but the abstraction model is fragmented. The target should be one event contract source of truth in `common-events`, transport-only route and adapter ownership in Kafka/Redis, one naming standard for publisher/listener/handler/dispatcher/codec, and no empty or historical contract packages kept around as architectural clutter.
