# Freeze Rerun Blockers - Round 2

## Scope
- Services only:
  - chat-service
  - user-service
  - presence-service
- Previous kafka/notification/friendship fixes were kept intact.
- No architecture redesign was introduced.

## Compile Command Executed
- `./gradlew.bat :chat-service:compileJava :user-service:compileJava :presence-service:compileJava --no-daemon --continue`
- Result: `BUILD SUCCESSFUL`

## 1) Strict Order Fixes

### Blocker 1 - chat-service BOM issue

#### Requested file fixed
- `chatappBE/chat-service/src/main/java/com/example/chat/realtime/publisher/ChatRealtimeEventPublisher.java`

#### Action
- Rewrote file as UTF-8 without BOM.

#### Compile error resolved
- `illegal character: '\ufeff'` at line 1.

#### Status
- **Fully cleared: YES**

---

### Blocker 2 - user-service TimeRedisCacheManager type mismatch

#### Requested file fixed
- `chatappBE/user-service/src/main/java/com/example/user/configuration/RedisCacheConfig.java`

#### Action
- Import aligned from legacy alias package to current concrete builder return type:
  - from `com.example.common.redis.core.TimeRedisCacheManager`
  - to `com.example.common.redis.cache.core.TimeRedisCacheManager`

#### Compile error resolved
- `incompatible types: com.example.common.redis.cache.core.TimeRedisCacheManager cannot be converted to com.example.common.redis.core.TimeRedisCacheManager`

#### Status
- **Fully cleared: YES**

---

### Blocker 3 - presence-service Redis API symbol/type mismatch

#### Requested files fixed
- `chatappBE/presence-service/src/main/java/com/example/presence/configuration/PresenceRedisListenerConfig.java`
- `chatappBE/presence-service/src/main/java/com/example/presence/redis/PresenceRedisPublisher.java`

#### Actions
- Replaced non-existent logger type:
  - from `RedisPubSubLogger`
  - to `com.example.common.redis.observability.RedisPubSubObserver`
- Removed non-existent Redis message type usage:
  - removed `com.example.common.redis.message.RedisMessage`
  - replaced with canonical `EventEnvelope` + `EventMetadata` construction

#### Compile errors resolved
- `cannot find symbol: RedisPubSubLogger`
- `package com.example.common.redis.message does not exist`
- `cannot find symbol: class RedisMessage`

#### Status
- **Fully cleared: YES**

---

## Additional Compile Drift Fixed (required to make target services compile cleanly)

These surfaced during verification and were fixed minimally within the same 3-service scope:

### chat-service
- `chatappBE/chat-service/src/main/java/com/example/chat/config/ChatRedisListenerConfig.java`
  - Replaced `IRedisPubSubLogger` with `RedisPubSubObserver`
- `chatappBE/chat-service/src/main/java/com/example/chat/config/RedisCacheConfig.java`
  - `TimeRedisCacheManager` import aligned to `com.example.common.redis.cache.core.TimeRedisCacheManager`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java`
  - Fixed call to `ChatMessagePayloadFactory.from(...)` to match current method signature (removed obsolete extra argument)
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/room/cache/redis/RedisRoomListCacheAdapter.java`
  - Corrected checked exception import package:
    - from `com.example.common.redis.exception.CreateCacheException`
    - to `com.example.common.redis.cache.exception.CreateCacheException`

### presence-service
- `chatappBE/presence-service/src/main/java/com/example/presence/configuration/RedisCacheConfig.java`
  - `TimeRedisCacheManager` import aligned to `com.example.common.redis.cache.core.TimeRedisCacheManager`
- `chatappBE/presence-service/src/main/java/com/example/presence/state/redis/RedisPresenceTtlCacheAdapter.java`
  - Corrected checked exception import package:
    - from `com.example.common.redis.exception.CreateCacheException`
    - to `com.example.common.redis.cache.exception.CreateCacheException`

## Remaining Compile Errors
- None for the requested compile target (`chat-service`, `user-service`, `presence-service`).

## Blocker Clearance Summary
- chat-service BOM blocker: **CLEARED**
- user-service TimeRedisCacheManager blocker: **CLEARED**
- presence-service Redis API symbol/type blocker: **CLEARED**

## Readiness For Another Full Phase 5 Rerun
- **YES**
- The three remaining hard compile blockers from the rerun are cleared.
- Backend is ready for another full Phase 5 integration/freeze-readiness rerun pass.
