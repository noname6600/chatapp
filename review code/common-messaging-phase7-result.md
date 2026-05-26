# Common Messaging Refactor - Phase 7 Result

## Scope Implemented

Implemented ONLY Phase 7 from the proposal within:
- `chatappBE/common/common-events`
- `chatappBE/common/common-kafka`
- `chatappBE/common/common-redis`

Actual code changes in this phase were intentionally limited to `common-redis` to satisfy the Phase 7 goals while preserving compatibility.

## New Redis Event-Oriented Classes Added

Added new event-oriented API/package surface (additive, non-breaking):

1. `com.example.common.redis.publisher.RedisEventPublisher`
2. `com.example.common.redis.publisher.DefaultRedisEventPublisher`
3. `com.example.common.redis.subscriber.RedisEventSubscriber<T>`
4. `com.example.common.redis.dispatcher.RedisEventDispatcher`
5. `com.example.common.redis.listener.RedisEventListener`
6. `com.example.common.redis.serialization.RedisEventSerializer`
7. `com.example.common.redis.serialization.JsonRedisEventSerializer`
8. `com.example.common.redis.registry.RedisEventRegistry`
9. `com.example.common.redis.registry.DefaultRedisEventRegistry`

These coexist with old message APIs and do not require service-module migration.

## Old Message APIs Preserved

Legacy Redis message APIs are kept and now explicitly marked as deprecated compatibility adapters:

- Interfaces:
  - `IRedisPublisher` (extends `RedisEventPublisher`)
  - `IRedisSubscriber<T>` (extends `RedisEventSubscriber<T>`)
  - `IRedisMessageSerializer` (extends `RedisEventSerializer`)
  - `IRedisMessageRegistry` (extends `RedisEventRegistry`)
- Message model:
  - `IRedisMessage`
  - `AbstractRedisMessage`
  - `RedisMessage<T>`
- Implementations:
  - `DefaultRedisPublisher` (delegates to `RedisEventPublisher`)
  - `RedisMessageDispatcher`
  - `DefaultRedisMessageListener`
  - `JsonRedisMessageSerializer`
  - `DefaultRedisMessageRegistry`

No old APIs were removed.

## Serializer/Deserializer Compatibility Behavior

`JsonRedisMessageSerializer.deserialize(...)` was hardened for wire compatibility:

1. Identity metadata preservation:
- Preserves `eventId` and `correlationId` from payload when present.
- Uses fallback mapping when old messageId-based payload shape is received:
  - `messageId` fallback chain: `messageId` -> `eventId` -> generated UUID
  - `eventId` fallback chain: explicit `eventId` -> `messageId`
  - `correlationId` fallback chain: explicit `correlationId` -> `eventId`

2. Old metadata shape compatibility:
- Supports `type` as fallback alias when `eventType` is absent.
- Supports `data` as fallback alias when `payload` is absent.
- Supports `occurredAt` fallback when `createdAt` is absent.

3. Existing presence event-type alias normalization behavior is preserved:
- Old underscored values are normalized for payload type resolution.
- Deprecated alias usage still logs warnings.

This keeps old wire reads compatible without changing existing business logic.

## Redis Auto-Configuration Coexistence

`common-redis` auto-configuration now exposes both new and legacy surfaces:

- New canonical beans:
  - `RedisEventSerializer` -> `JsonRedisEventSerializer`
  - `RedisEventPublisher` -> `DefaultRedisEventPublisher`
  - `RedisEventDispatcher` -> `RedisEventDispatcher`
  - Event listener bean using `RedisEventListener`
- Legacy compatibility beans retained:
  - `IRedisMessageSerializer`
  - `IRedisPublisher` (adapter wrapping new publisher)
  - `RedisMessageDispatcher`
  - `redisListener` alias bean

This preserves injection compatibility for existing consumers.

## Validation

Executed compile validation:

`./gradlew :common:common-events:compileJava :common:common-redis:compileJava :common:common-kafka:compileJava`

Result:
- BUILD SUCCESSFUL
- Deprecation warnings only (expected from compatibility adapters)

## Remaining Risks For Phase 8+

1. New event-oriented Redis subscriber contract currently coexists with old subscriber shape but does not yet enforce a fully envelope-native subscriber pipeline.
2. `DefaultRedisEventRegistry` is added for API completeness; full bean primacy migration can be tightened in a later phase if desired.
3. Full package reorganization is intentionally deferred, per scope constraints.
4. Optional future cleanup may remove adapter layers only after downstream migration is complete.
