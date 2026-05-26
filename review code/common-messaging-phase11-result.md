# Common Messaging Refactor — Phase 11 Result

**Phase**: 11 — Service-Level Migration to Canonical APIs  
**Status**: ✅ Complete  
**Build result**: All modules compile; all tests pass (including 44 common-module contract tests from Phases 1–10, all chat/presence/notification service tests)

---

## Goals

1. Migrate service imports and usages from deprecated messaging APIs to the new canonical APIs.
2. Replace deprecated `IKafkaEventPublisher` usages with `KafkaEventProducer`.
3. Replace deprecated `IRedisPublisher` usages with `RedisEventPublisher`.
4. Replace deprecated `IRedisSubscriber<T>` implementations with `RedisEventSubscriber<T>`.
5. Replace deprecated `IRedisMessageRegistry` / `IRedisMessageSerializer` field types with `RedisEventRegistry` / `RedisEventSerializer` in service configs.
6. Replace deprecated `RealtimeRedisChannels` references with `RedisChannels` in service channel constants.
7. Replace deprecated `integration.kafka.KafkaTopics` import with `kafka.topic.KafkaTopics` across all services.
8. Update `RedisEventDispatcher` and `RedisAutoConfiguration` to accept the canonical subscriber type.
9. Keep runtime behaviour unchanged. Preserve all deprecated bridges (no removals).

---

## Changes by Module

### `common/common-redis` (3 files)

#### `RedisEventDispatcher.java`
- **Old**: Single constructor accepting `List<IRedisSubscriber<?>>`, delegated entirely to `super()`.
- **New**: Constructor accepts `List<? extends RedisEventSubscriber<?>>` (wider canonical type). Passes `List.of()` to deprecated parent; builds its own `eventSubscriberMap`. Overrides `dispatch(IRedisMessage)` using the new map, with the same presence-event alias resolution logic as before.
- **Backward compatibility**: `IRedisSubscriber<T>` still extends `RedisEventSubscriber<T>`, so all existing `IRedisSubscriber` beans are automatically picked up.

#### `RedisEventListener.java`
- Added a second canonical constructor accepting `(RedisEventSerializer, RedisEventDispatcher, IRedisPubSubLogger)`, delegating to the deprecated parent via a safe cast. The cast is safe because the autoconfigured `JsonRedisEventSerializer` bean always implements both `IRedisMessageSerializer` and `RedisEventSerializer`.
- Legacy constructor (`IRedisMessageSerializer, RedisMessageDispatcher, IRedisPubSubLogger`) preserved unchanged.

#### `RedisAutoConfiguration.java`
- `redisEventDispatcher(List<IRedisSubscriber<?>> subscribers)` → `redisEventDispatcher(List<RedisEventSubscriber<?>> subscribers)`. Spring injects all `RedisEventSubscriber<?>` beans (including legacy `IRedisSubscriber<T>` implementors).
- Added `@Primary` to the `redisEventSerializer` bean to resolve ambiguity when both `redisEventSerializer` and `redisMessageSerializer` qualify for `RedisEventSerializer` injection.
- Added `import org.springframework.context.annotation.Primary`.

---

### `auth-service` (1 file)

#### `AccountCreatedEventProducer.java`
- `import com.example.common.integration.kafka.KafkaTopics` → `import com.example.common.kafka.topic.KafkaTopics`
- `import com.example.common.kafka.api.IKafkaEventPublisher` → `import com.example.common.kafka.producer.KafkaEventProducer`
- Field: `IKafkaEventPublisher kafkaEventPublisher` → `KafkaEventProducer kafkaEventProducer`
- Call site: `kafkaEventPublisher.publish(...)` → `kafkaEventProducer.publish(...)`

---

### `friendship-service` (3 files)

#### `FriendshipEventProducer.java`
- Same `KafkaTopics` import and `IKafkaEventPublisher` → `KafkaEventProducer` migration as auth-service.

#### `FriendshipEventConsumer.java`
#### `FriendshipRequestEventConsumer.java`
- `import com.example.common.integration.kafka.KafkaTopics` → `import com.example.common.kafka.topic.KafkaTopics`

---

### `user-service` (1 file)

#### `AccountCreatedConsumer.java`
- `import com.example.common.integration.kafka.KafkaTopics` → `import com.example.common.kafka.topic.KafkaTopics`

---

### `notification-service` (8 files)

#### `NotificationEventProducer.java`
- `IKafkaEventPublisher` → `KafkaEventProducer` (import + field + call site).
- `integration.kafka.KafkaTopics` → `kafka.topic.KafkaTopics`.

#### `AccountCreatedEventConsumer.java`, `ChatMessageEventConsumer.java`, `FriendRequestEventConsumer.java`, `MessageCreatedEventConsumer.java`, `ReactionEventConsumer.java`
- `import com.example.common.integration.kafka.KafkaTopics` → `import com.example.common.kafka.topic.KafkaTopics`

#### `NotificationRedisChannels.java`
- `import com.example.common.integration.realtime.RealtimeRedisChannels` → `import com.example.common.redis.channel.RedisChannels`
- `RealtimeRedisChannels.NOTIFICATION_USER_PREFIX/PATTERN` → `RedisChannels.NOTIFICATION_USER_PREFIX/PATTERN`
- `RealtimeRedisChannels.notificationUser(userId)` → `RedisChannels.notificationUser(userId)`

#### `NotificationRealtimeContractBaselineTest.java` (test)
- Updated test assertions to compare against `RedisChannels` instead of `RealtimeRedisChannels`.

---

### `chat-service` (14 files)

#### `KafkaChatMessageEventPublisher.java`, `KafkaReactionEventPublisher.java`
- `IKafkaEventPublisher` → `KafkaEventProducer` (import + field + call sites).
- `integration.kafka.KafkaTopics` → `kafka.topic.KafkaTopics`.

#### `KafkaChatMessageEventConsumer.java`, `KafkaReactionEventConsumer.java`
- `import com.example.common.integration.kafka.KafkaTopics` → `import com.example.common.kafka.topic.KafkaTopics`

#### `ChatRedisPublisher.java`
- `import com.example.common.redis.api.IRedisPublisher` → `import com.example.common.redis.publisher.RedisEventPublisher`
- Field: `IRedisPublisher redisPublisher` → `RedisEventPublisher redisPublisher`

#### `ChatRedisChannels.java`
- `import com.example.common.integration.realtime.RealtimeRedisChannels` → `import com.example.common.redis.channel.RedisChannels`
- All `RealtimeRedisChannels.CHAT_ROOM_PREFIX/PATTERN` and `chatRoom(roomId)` → `RedisChannels.*`

#### `ChatRedisEventConfig.java`
- `import com.example.common.redis.registry.IRedisMessageRegistry` → `import com.example.common.redis.registry.RedisEventRegistry`
- Field: `IRedisMessageRegistry registry` → `RedisEventRegistry registry`

#### `ChatRedisListenerConfig.java`
- `import com.example.common.redis.dispatcher.RedisMessageDispatcher` → `import com.example.common.redis.dispatcher.RedisEventDispatcher`
- `import com.example.common.redis.listener.DefaultRedisMessageListener` → `import com.example.common.redis.listener.RedisEventListener`
- `import com.example.common.redis.serialization.IRedisMessageSerializer` → `import com.example.common.redis.serialization.RedisEventSerializer`
- Fields: `IRedisMessageSerializer serializer` → `RedisEventSerializer serializer`; `RedisMessageDispatcher dispatcher` → `RedisEventDispatcher dispatcher`
- Listener creation: `new DefaultRedisMessageListener(...)` → `new RedisEventListener(...)`

#### 6 chat Redis subscribers (all in `realtime/subscriber/`)
- `ChatMessageSentRedisSubscriber`, `ChatMessageDeletedRedisSubscriber`, `ChatMessageEditedRedisSubscriber`, `ChatMessagePinnedRedisSubscriber`, `ChatMessageUnpinnedRedisSubscriber`, `ChatReactionUpdatedRedisSubscriber`
- `import com.example.common.redis.api.IRedisSubscriber` → `import com.example.common.redis.subscriber.RedisEventSubscriber`
- `implements IRedisSubscriber<RedisMessage<T>>` → `implements RedisEventSubscriber<RedisMessage<T>>`
- Body unchanged (same method signatures).

#### `RealtimeContractBaselineTest.java` (test)
- `import com.example.common.integration.kafka.KafkaTopics` → `import com.example.common.kafka.topic.KafkaTopics`
- `import com.example.common.integration.realtime.RealtimeRedisChannels` → `import com.example.common.redis.channel.RedisChannels`
- Updated assertions to use `RedisChannels`.

#### `RealtimeMessagingAlignmentTest.java` (test)
- Added `import com.example.common.event.EventEnvelope`.
- Fixed pre-existing bug: `KafkaEventPublisher` lambda assignment → replaced with anonymous class implementing both `publish` overloads (required because `KafkaEventProducer` has two abstract methods and is not a `@FunctionalInterface`).

---

### `presence-service` (13 files)

#### `PresenceRedisPublisher.java`
- `import com.example.common.redis.api.IRedisPublisher` → `import com.example.common.redis.publisher.RedisEventPublisher`
- Field: `IRedisPublisher redisPublisher` → `RedisEventPublisher redisPublisher`

#### `PresenceRedisChannels.java`
- `import com.example.common.integration.realtime.RealtimeRedisChannels` → `import com.example.common.redis.channel.RedisChannels`
- All `RealtimeRedisChannels.*` constants and `presenceRoom(roomId)` → `RedisChannels.*`

#### `PresenceRedisRegistryConfig.java`
- `import com.example.common.redis.registry.IRedisMessageRegistry` → `import com.example.common.redis.registry.RedisEventRegistry`
- Field: `IRedisMessageRegistry registry` → `RedisEventRegistry registry`

#### `PresenceRedisListenerConfig.java`
- Same listener config migration as `ChatRedisListenerConfig` (dispatcher/serializer/listener types).
- `IRedisMessageSerializer serializer` → `RedisEventSerializer serializer`
- `RedisMessageDispatcher dispatcher` → `RedisEventDispatcher dispatcher`
- `new DefaultRedisMessageListener(...)` → `new RedisEventListener(...)`

#### 8 presence Redis subscribers
- `UserOnlineSubscriber`, `UserOfflineSubscriber`, `UserStatusChangedSubscriber`, `RoomJoinSubscriber`, `RoomLeaveSubscriber`, `RoomOnlineUsersSubscriber`, `UserTypingSubscriber`, `UserStopTypingSubscriber`
- `import com.example.common.redis.api.IRedisSubscriber` → `import com.example.common.redis.subscriber.RedisEventSubscriber`
- `implements IRedisSubscriber<RedisMessage<T>>` → `implements RedisEventSubscriber<RedisMessage<T>>`
- Body unchanged.

#### `PresenceRealtimeContractBaselineTest.java` (test)
- `import com.example.common.integration.realtime.RealtimeRedisChannels` → `import com.example.common.redis.channel.RedisChannels`
- Updated assertions to compare against `RedisChannels`.

---

## Issues Encountered and Resolved

### 1. `RedisEventDispatcher` constructor generics
The old constructor required `List<IRedisSubscriber<?>>`. A `List<RedisEventSubscriber<?>>` is NOT assignment-compatible due to Java generics invariance. The fix was to use `List<? extends RedisEventSubscriber<?>>` (wildcarded upper bound). Since `IRedisSubscriber<T>` extends `RedisEventSubscriber<T>`, existing beans are still collected automatically.

### 2. `@Primary` required for `RedisEventSerializer` bean disambiguation
After changing service config fields from `IRedisMessageSerializer` to `RedisEventSerializer`, Spring found two qualifying beans:
- `redisEventSerializer` (declared type `RedisEventSerializer`)  
- `redisMessageSerializer` (declared type `IRedisMessageSerializer`, which extends `RedisEventSerializer`)

Fixed by adding `@Primary` to the `redisEventSerializer` bean in `RedisAutoConfiguration`, making it the unambiguous primary candidate for `RedisEventSerializer` injection.

### 3. `RedisEventListener` canonical constructor
The new canonical listener constructor `(RedisEventSerializer, RedisEventDispatcher, IRedisPubSubLogger)` needed to delegate to `super()` which requires `IRedisMessageSerializer`. The cast `(IRedisMessageSerializer) serializer` is safe because the autoconfigured serializer is always `JsonRedisEventSerializer`, which implements both interfaces.

### 4. `KafkaEventPublisher` is not a functional interface
`KafkaEventProducer` has two abstract `publish` overloads, so none of the Kafka interfaces can be used as lambdas. A pre-existing bug in `RealtimeMessagingAlignmentTest` used lambda syntax for `KafkaEventPublisher`. Fixed using anonymous class syntax implementing both overloads.

---

## Invariants Preserved

- **No deprecated bridges removed**: All deprecated adapter interfaces, types and aliases remain in-place in `common-*` modules.
- **Runtime behaviour unchanged**: All business logic in services is identical; only import paths and field types changed.
- **Spring wiring unchanged**: All beans are resolved to the same underlying `JsonRedisEventSerializer` / `DefaultRedisEventPublisher` / `RedisEventDispatcher` instances as before.
- **Backward compatibility**: Services still using `IRedisSubscriber<T>` (if any) would continue to compile and be injected correctly, since `IRedisSubscriber<T>` extends `RedisEventSubscriber<T>`.

---

## Test Summary

| Module | Tests |
|--------|-------|
| `common-events` | 11 passed (Phase 10 contract suite) |
| `common-kafka` | 18 passed (Phase 10 contract suite) |
| `common-redis` | 15 passed (Phase 10 contract suite) |
| `chat-service` | All tests passed (including `contextLoads`, contract baselines, `CrossInstanceRealtimeFanoutIntegrationTest`) |
| `presence-service` | All tests passed |
| `notification-service` | All tests passed |

Total: **44 common-module contract tests** + all service tests — all green.
