# Common Messaging Refactor Result

## 1. Scope

**Modules modified:**
- `chatappBE/common/common-events/src/main/java/`
- `chatappBE/common/common-redis/src/main/java/`
- `chatappBE/common/common-kafka/src/main/java/`
- `chatappBE/common/common-kafka/src/test/java/`

**Modules NOT modified:** all service modules, gateway, auth-service, chat-service, presence-service, friendship-service, notification-service, user-service, upload-service, redis-cache module, docker/nginx/deployment files.

**Verification:** `.\gradlew.bat :common:common-events:test :common:common-redis:test :common:common-kafka:test` â†’ `BUILD SUCCESSFUL`

---

## 2. Architectural Goal

### What was standardized
- `common-events` is the single source of truth for event type names, payload definitions, and payload registry logic
- All payload classes in `common-events` are now immutable, use `@JsonCreator`, `@JsonIgnoreProperties(ignoreUnknown = true)`, and consistent Lombok annotations
- Handler method naming aligned: both Redis and Kafka handlers now use `handle(EventEnvelope<T>)`
- Publisher naming aligned: canonical interface is `KafkaEventPublisher` (Redis already had `RedisEventPublisher`)
- Exception naming aligned: Kafka uses `KafkaMessagingException` (matching Redis naming style â€” transport name + concept)
- Deprecated `@Component` removed from `Slf4jRedisPubSubLogger`, `DefaultRedisEventRegistry`, and `Slf4jKafkaEventLogger`; all are now wired only through auto-configuration
- Dispatch error vs. deserialize error are separated in `RedisEventListener`
- Kafka topics for account events corrected from `account.account.*` to `account.*`
- Kafka deprecated topic alias constants removed
- Kafka auto-configuration conditioned on `KafkaTemplate.class` presence
- Dead Kafka registry abstraction deleted

### What was intentionally kept different between Redis and Kafka

| Concern | Redis | Kafka | Justification |
|---|---|---|---|
| Serialization | Owns `RedisEventSerializer` / `JsonRedisEventSerializer` | Relies on Spring Kafka / external config | Redis uses `StringRedisTemplate`; Kafka uses `KafkaTemplate<String, Object>` delegating to configured Spring Kafka serializers |
| Payload registry | `RedisEventRegistry extends EventPayloadRegistry` â€” used at deserialization | No registry â€” deleted | Redis owns the full deserialization pipeline and needs runtime `Class<?>` resolution; Kafka does not own deserialization in common |
| Listener infrastructure | Common module owns `RedisEventListener` (`MessageListener`) | `@KafkaListener` methods are service-owned | Redis pub/sub requires a shared `MessageListenerContainer`; Kafka listener is a service concern |
| Routing context | `RedisEventRoutingContext` has `channel`, no `key` | `KafkaEventRoutingContext` has `topic` and `key` | Redis uses channel-based routing; Kafka uses topic+partition-key routing |
| Observer coverage | `logPublish`, `logReceive`, `logError`, `logDeserializeError` â€” full pipeline | `logPublish`, `logError` (required) + `logDispatch`, `logDispatchError` (no-op defaults) | Redis covers the full common pipeline; Kafka consumer path is service-owned, so dispatcher observability is optional |
| Friendship route names | N/A | `TOPIC_FRIENDSHIP_EVENTS`, `TOPIC_FRIENDSHIP_REQUEST_EVENTS` are aggregate routes | Multiple friendship event types share a single Kafka topic â€” intentional transport-level aggregation |

---

## 3. Changes Made In Common-Events

### AccountEventType
- Fixed event type values: `account.account.created` â†’ `account.created`, `account.account.deleted` â†’ `account.deleted`, `account.account.disabled` â†’ `account.disabled`
- The previous values had a redundant nested namespace prefix

### DefaultEventPayloadRegistry
- Changed `containsKey` + `put` to atomic `putIfAbsent`
- Prevents duplicate registration from slipping through under concurrent startup

### EventContractValidator
- Fixed `isValidEventType(null)` â€” previously threw `NullPointerException`; now returns `false`, consistent with the `boolean`-returning contract

### AccountCreatedPayload
- Added `@JsonIgnoreProperties(ignoreUnknown = true)` for forward-compatible deserialization

### FriendshipPayload
- Replaced wildcard `import lombok.*;` with explicit `import lombok.Getter;`
- Added `@JsonIgnoreProperties(ignoreUnknown = true)`

### RoomOnlineUsersPayload
- Replaced wildcard `import lombok.*;` with explicit `import lombok.Builder; import lombok.Getter;`

### GlobalOnlineUsersPayload
- Removed unused `@AllArgsConstructor` and `@NoArgsConstructor` annotations and their imports

### NotificationRequestedPayload
- Rewrote from mutable Lombok DTO (`@AllArgsConstructor` only) to immutable class with `@JsonCreator`, `@JsonIgnoreProperties`, `@Builder`, and `final` fields
- Now follows the same Jackson contract as all other payload classes in the module

### NotificationEventType
- Added `NOTIFICATION_CREATED("notification.created")` to match `NotificationCreatedPayload`

### NotificationEvent
- Changed `@Deprecated(since = "2.2", forRemoval = false)` â†’ `forRemoval = true` â€” signals scheduled removal

### FriendRequestEvent
- Added `@Deprecated(since = "2.2", forRemoval = true)` with Javadoc explaining the design problems:
  - Misnamed as "event" when it is a payload DTO
  - Nested `Type` enum duplicates `FriendshipEventType`, allowing contradictory state

### UserPresencePayload
- Rewrote from mutable Lombok DTO (`@NoArgsConstructor @AllArgsConstructor`) to immutable class with `@JsonCreator`, `@JsonIgnoreProperties`, and `final` fields

### PresenceEventType
- Annotated `isDeprecatedAlias` and `legacyAliasOf` with `@Deprecated(since = "2.2", forRemoval = true)`
- The `normalize` method and `fromValue` backward-compat logic are retained as internal migration infrastructure

### New: MessagePinPayload
- Created `com.example.common.integration.chat.MessagePinPayload` for `ChatEventType.MESSAGE_PINNED` and `ChatEventType.MESSAGE_UNPINNED`
- Eliminates the gap where these event types had no matching shared payload in `common-events`, forcing services to use local payload classes

### New: FriendRequestPayload
- Created `com.example.common.integration.friendship.FriendRequestPayload` â€” the correctly named payload DTO without a nested action enum
- The event type (sent/accepted/declined/cancelled) is carried in `EventMetadata.eventType` using `FriendshipEventType` values

---

## 4. Changes Made In Common-Redis

### Deleted deprecated aliases
- Deleted `IRedisPubSubLogger.java` (deprecated alias for `RedisPubSubObserver`)
- Deleted `RedisPubSubLogger.java` (deprecated alias for `Slf4jRedisPubSubLogger`)

### RedisEventListener â€” fixed error handling
- Separated deserialization failure from dispatch/handler failure
- Deserialization failure â†’ `logger.logDeserializeError` and early return
- Dispatch/handler failure â†’ `logger.logError`
- Previously all failures from either phase were reported as deserialization errors, masking real subscriber failures in operations tooling

### DefaultRedisEventPublisher â€” fixed lifecycle stage name
- `"Failed at Redis lifecycle stage DISPATCH"` â†’ `"Failed at Redis lifecycle stage PUBLISH"`
- DISPATCH implies the message was sent; the failure actually occurs at the send/publish step

### RedisPubSubObserver â€” removed `logForward` and fixed null safety
- Removed `logForward(String, EventEnvelope<?>)` â€” no common Redis component calls it; it was dead interface surface
- Default methods now guard against `null` context: `if (context != null)` before calling `context.channel()`

### Slf4jRedisPubSubLogger â€” removed `@Component` and `logForward`
- Library beans should not be component-scanned; `RedisAutoConfiguration` already registers this bean
- Removed `logForward` implementation

### DefaultRedisEventRegistry â€” removed `@Component`
- Same reason: auto-configuration owns bean registration

### RedisEventHandler â€” aligned handler method naming
- Added `handle(EventEnvelope<T> envelope)` as the canonical dispatch method (aligned with `KafkaEventHandler.handle`)
- `onEvent(EventEnvelope<T>)` retained as `@Deprecated default` delegating to `handle`
- Existing implementations continue to work unchanged

### RedisEventDispatcher â€” updated to call canonical method
- Now calls `handler.handle(envelope)` instead of `handler.onEvent(envelope)`

---

## 5. Changes Made In Common-Kafka

### Deleted dead abstractions
- Deleted `KafkaEventRegistry.java` â€” not used by any Kafka serde, dispatcher, or producer
- Deleted `DefaultKafkaEventRegistry.java` â€” same reason
- Deleted empty `registry/` directory

### Deleted deprecated alias classes
- Deleted `observability/IKafkaEventLogger.java`
- Deleted `observability/KafkaEventLogger.java`
- Deleted `logging/IKafkaEventLogger.java`
- Deleted `logging/KafkaEventLogger.java`
- Deleted `logging/` directory

### New: KafkaEventPublisher â€” canonical producer interface
- Created `com.example.common.kafka.producer.KafkaEventPublisher` as the canonical interface
- `KafkaEventProducer` is now a deprecated alias extending `KafkaEventPublisher`

### New: DefaultKafkaEventPublisher â€” canonical producer implementation
- Created `DefaultKafkaEventPublisher implements KafkaEventPublisher, KafkaEventProducer`
- Contains the actual publish logic: metadata validation, `KafkaTemplate.send`, observer callbacks
- Error message corrected: `"Failed at Kafka lifecycle stage PUBLISH"` (was `DISPATCH`)
- `DefaultKafkaEventProducer` is now a deprecated subclass with no logic of its own

### New: KafkaMessagingException â€” correctly named exception
- Created `KafkaMessagingException` replacing the "PubSub"-named `KafkaPubSubException`
- `KafkaPubSubException` is now a deprecated subclass of `KafkaMessagingException`

### KafkaTopics â€” removed deprecated aliases and fixed account values
- Removed all `@Deprecated` alias constants (`ACCOUNT_CREATED`, `CHAT_MESSAGE_SENT`, etc.)
- Fixed account topic values: `account.account.created` â†’ `account.created`
- Added Javadoc distinguishing 1:1 route constants from aggregate routes (`TOPIC_FRIENDSHIP_EVENTS`)

### KafkaAutoConfiguration â€” cleaned up
- Removed `kafkaEventRegistry` bean (registry deleted)
- Added `@ConditionalOnClass(KafkaTemplate.class)` (consistent with Redis `@ConditionalOnClass(StringRedisTemplate.class)`)
- Producer bean is now `KafkaEventPublisher kafkaEventPublisher(...)` returning `DefaultKafkaEventPublisher`

### KafkaEventObserver â€” added consumer-side observability hooks
- Added `logDispatch` and `logDispatchError` with no-op defaults
- No-op defaults preserve backward compatibility for existing implementations

### Slf4jKafkaEventLogger â€” removed `@Component`
- Library beans should not be component-scanned

### KafkaEventDispatcher â€” updated Javadoc
- Removed stale references to `RedisMessageDispatcher` (deleted class)

### KafkaEventHandler â€” updated Javadoc
- Removed stale reference to `IRedisSubscriber` (deleted class)
- Updated to reference `RedisEventHandler`

### KafkaContractTest â€” updated for renamed types
- Replaced `KafkaEventRegistry`/`DefaultKafkaEventRegistry` tests with `EventPayloadRegistry`/`DefaultEventPayloadRegistry` from `common-events`
- Replaced `DefaultKafkaEventProducer` with `DefaultKafkaEventPublisher`
- Replaced `KafkaEventProducer` class reference with `KafkaEventPublisher`

---

## 6. Cross-Module Consistency Fixes

### Naming alignment
| Concept | Before | After |
|---|---|---|
| Kafka publisher interface | `KafkaEventProducer` | `KafkaEventPublisher` (alias retained) |
| Kafka publisher implementation | `DefaultKafkaEventProducer` | `DefaultKafkaEventPublisher` (alias retained) |
| Kafka exception | `KafkaPubSubException` | `KafkaMessagingException` (alias retained) |
| Redis handler method | `onEvent` | `handle` (onEvent retained as deprecated default) |
| Kafka handler method | `handle` | `handle` (already correct) |
| Lifecycle stage in exception | `DISPATCH` | `PUBLISH` (both Redis and Kafka) |

### Registry/catalog ownership
- `EventPayloadRegistry` / `DefaultEventPayloadRegistry` in `common-events` is the shared registry
- `RedisEventRegistry extends EventPayloadRegistry` â€” kept (Redis needs transport-scoped registry for serde)
- `KafkaEventRegistry` / `DefaultKafkaEventRegistry` â€” deleted (no Kafka serde uses them)

### Validation consistency
- Both publishers call `EventContractValidator.validateEventNameOrThrow` and `validateIdentityOrThrow` before sending
- `EventContractValidator.isValidEventType(null)` now returns `false` instead of throwing NPE

### Observability alignment
- Both modules have `logPublish` + `logError` as required methods on the observer interface
- Redis additionally has `logReceive` and `logDeserializeError` (full pipeline coverage)
- Kafka additionally has `logDispatch` and `logDispatchError` as optional no-op defaults
- Difference is intentional: Redis common owns the listener; Kafka listener is service-owned

### @Component removal
- `Slf4jRedisPubSubLogger`, `DefaultRedisEventRegistry`, `Slf4jKafkaEventLogger` no longer carry `@Component`
- All are wired exclusively through auto-configuration beans

---

## 7. Removed Code

### Deleted files
| Module | File | Reason |
|---|---|---|
| common-redis | `observability/IRedisPubSubLogger.java` | Deprecated alias |
| common-redis | `observability/RedisPubSubLogger.java` | Deprecated alias |
| common-kafka | `observability/IKafkaEventLogger.java` | Deprecated alias |
| common-kafka | `observability/KafkaEventLogger.java` | Deprecated alias |
| common-kafka | `logging/IKafkaEventLogger.java` | Deprecated alias |
| common-kafka | `logging/KafkaEventLogger.java` | Deprecated alias |
| common-kafka | `registry/KafkaEventRegistry.java` | Dead abstraction â€” unused by any Kafka serde |
| common-kafka | `registry/DefaultKafkaEventRegistry.java` | Dead abstraction |

### Removed constants
- All `@Deprecated` alias constants from `KafkaTopics`: `ACCOUNT_CREATED`, `ACCOUNT_DELETED`, `ACCOUNT_DISABLED`, `USER_PROFILE_CREATED`, `USER_PROFILE_UPDATED`, `FRIENDSHIP_EVENTS`, `FRIENDSHIP_REQUEST_EVENTS`, `CHAT_MESSAGE_SENT`, `CHAT_MESSAGE_EDITED`, `CHAT_MESSAGE_DELETED`, `CHAT_REACTION_UPDATED`, `NOTIFICATION_REQUESTED`, `NOTIFICATION_SENT`, `DEAD_LETTER`, `RETRY`

### Removed interface methods
- `RedisPubSubObserver.logForward(String, EventEnvelope<?>)` â€” no caller inside common modules

### Removed annotations
- `@Component` from `Slf4jRedisPubSubLogger`, `DefaultRedisEventRegistry`, `Slf4jKafkaEventLogger`

### Replaced with deprecation marker
- `NotificationEvent` â€” `forRemoval = false` â†’ `forRemoval = true`
- `FriendRequestEvent` â€” added `@Deprecated(since = "2.2", forRemoval = true)`
- `PresenceEventType.isDeprecatedAlias` and `legacyAliasOf` â€” annotated `@Deprecated(forRemoval = true)`

---

## 8. Intentional Differences Kept

### Redis serialization pipeline vs. Kafka Spring-managed serialization
- Redis: explicit `JsonRedisEventSerializer` + `RedisEventRegistry`
- Kafka: Spring Kafka handles serialization; no common serializer
- Reason: `KafkaTemplate<String, Object>` uses configured `Serializer`/`Deserializer` beans. Adding a common Kafka serde would require owning the full consumer pipeline, conflicting with service-level `@KafkaListener` patterns

### Redis listener pipeline vs. Kafka service-owned @KafkaListener
- Redis: `RedisEventListener â†’ RedisEventDispatcher â†’ RedisEventHandler.handle`
- Kafka: services write `@KafkaListener` methods; `KafkaEventDispatcher` is optional
- Reason: Redis pub/sub does not support per-consumer message filtering at protocol level. Kafka supports per-topic per-group consumption natively

### Redis registry vs. no Kafka registry
- Redis: `RedisEventRegistry` â€” needed for JSON deserialization class resolution
- Kafka: no registry â€” `KafkaEventRegistry` deleted as dead code
- Reason: Redis deserializes `Object` at runtime; Kafka delivers through Spring Kafka's typed deserialization

### Routing context shape
- Redis: `RedisEventRoutingContext(channel, eventType, correlationId, sourceService)`
- Kafka: `KafkaEventRoutingContext(topic, key, eventType, correlationId, sourceService)`
- Reason: Kafka partitioning requires a `key`; Redis pub/sub has no equivalent

### Friendship aggregate Kafka routes
- `TOPIC_FRIENDSHIP_EVENTS` and `TOPIC_FRIENDSHIP_REQUEST_EVENTS` remain as aggregate route names
- These are intentional transport-level design decisions: multiple event types are routed to one topic

---

## 9. Remaining Limitations

### Require service-module changes (not made)

1. **`RedisEventHandler.onEvent` implementations** â€” Services that override `onEvent` continue to work via the deprecated default delegation. Correct migration is to override `handle` instead. Requires service edits.

2. **`KafkaTopics` deprecated aliases removed** â€” Services using `KafkaTopics.ACCOUNT_CREATED`, `KafkaTopics.CHAT_MESSAGE_SENT`, etc. (without `TOPIC_` prefix) will fail to compile. These were already `@Deprecated` for several versions. Migration requires updating service references to `TOPIC_*` prefixed constants.

3. **`AccountEventType` value change** â€” `account.account.created` â†’ `account.created`. Services that hardcoded the old string value will have mismatched event routing until updated. This is the correct canonical value per the review.

4. **`FriendRequestEvent` / `NotificationEvent` still present** â€” Retained with `forRemoval = true`. Actual deletion requires confirming no service imports them.

5. **`PresenceEventType.isDeprecatedAlias` / `legacyAliasOf`** â€” Marked `@Deprecated(forRemoval = true)`. Retained because presence-service may call them. Removal requires verifying presence-service does not use these methods.

6. **`UserPresencePayload` still in `user` package** â€” Review recommends moving to `presence` package. Not moved because services import `com.example.common.integration.user.UserPresencePayload`. Retained with corrected immutability and JSON annotations.

---

## 10. Final Summary

**common-events** is now the true contract layer. Every shared event type with a consumer across services has a shared payload class. All payload classes are uniformly immutable with correct Jackson annotations. The payload registry lives in `common-events`. Account event type naming is correct (`account.created`, not `account.account.created`). Deprecated classes (`NotificationEvent`, `FriendRequestEvent`) are marked for future removal.

**common-redis** is a complete, clean pub/sub transport layer. It owns channels, publish flow, listener pipeline, serialization, dispatch, and observability. Deserialize failures and handler failures are now correctly separated. No component-scanned beans. No legacy alias classes. Handler method aligned with Kafka (`handle`).

**common-kafka** is a lean Kafka transport layer. It owns topics, publish flow, optional dispatcher, and observability. Dead registry abstractions are gone. Deprecated alias constants are gone. The canonical interface is `KafkaEventPublisher`. The canonical exception is `KafkaMessagingException`. Backward-compatible aliases (`KafkaEventProducer`, `DefaultKafkaEventProducer`, `KafkaPubSubException`) are retained to avoid breaking service modules until they migrate.

The remaining differences between Redis and Kafka â€” serialization ownership, listener infrastructure, registry presence, routing context shape â€” are all justified by real transport semantics, not historical accident or half-migrated design.

