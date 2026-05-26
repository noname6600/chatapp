# Phase 4 Implementation Result

Status: Complete
Date: 2026-04-30
Scope: common-events, common-kafka, common-redis
Approach: Minimal constants move with compatibility bridges

---

## OpenSpec Context

Using change: align-kafka-redis-pubsub-structure
Schema: spec-driven
OpenSpec progress: 12/12 complete (state: all_done)
Instruction state: ready to archive

Note: Phase 4 changes below were applied as a scoped compatibility pass based on review/proposal source-of-truth documents.

---

## Summary

Implemented ONLY Phase 4 goals with additive, backward-compatible changes:

1. Moved Redis transport constants into common-redis target locations.
2. Added deprecated compatibility bridges in old common-events locations.
3. Preserved existing imports and old API names.
4. Did not modify service modules.
5. Did not remove old APIs.
6. Did not perform broad package reorganization.

Compile verification:
- :common:common-events:compileJava -> PASS
- :common:common-redis:compileJava -> PASS
- :common:common-kafka:compileJava -> PASS

---

## Files Moved / Introduced

New canonical Redis constants locations (common-redis):

1. chatappBE/common/common-redis/src/main/java/com/example/common/redis/channel/RedisChannels.java
- New Redis-owned channel constants class
- Contains chat/notification/presence Redis channel prefixes/patterns and builders

2. chatappBE/common/common-redis/src/main/java/com/example/common/redis/config/RedisContractVersions.java
- New Redis-owned transport version constants
- Contains:
  - CHAT_REDIS_FANOUT
  - NOTIFICATION_REDIS_FANOUT
  - PRESENCE_REDIS_FANOUT

---

## Bridge Classes Added / Updated

1. chatappBE/common/common-events/src/main/java/com/example/common/integration/realtime/RealtimeRedisChannels.java
- Kept at old location
- Marked deprecated
- Now acts as compatibility bridge
- Uses runtime resolution toward new class name:
  - com.example.common.redis.channel.RedisChannels
- Falls back to existing literal values if new class is unavailable

Important compatibility behavior:
- Existing imports from old location still compile.
- Existing constant names and methods are unchanged.
- Bridge behavior avoids module-cycle issues while preserving old contract.

2. chatappBE/common/common-events/src/main/java/com/example/common/integration/realtime/RealtimeContractVersions.java
- Kept at old location
- Redis-specific constants converted to deprecated aliases:
  - CHAT_REDIS_FANOUT
  - NOTIFICATION_REDIS_FANOUT
  - PRESENCE_REDIS_FANOUT
- Aliases resolve from new class at runtime:
  - com.example.common.redis.config.RedisContractVersions
- Preserve fallback values for safety
- Non-Redis shared constants remain unchanged:
  - CHAT_MESSAGE_EVENTS
  - NOTIFICATION_KAFKA_EVENTS

3. chatappBE/common/common-events/src/main/java/com/example/common/integration/contract/RealtimeContractConventions.java
- Kept compatibility constants for downstream imports
- Marked transport-specific prefixes deprecated:
  - CHANNEL_PREFIX_REALTIME
  - CHANNEL_PREFIX_WS
- No removal to avoid import breakage in this phase

---

## Imports Preserved

Preserved import compatibility:

- Existing imports of com.example.common.integration.realtime.RealtimeRedisChannels still work.
- Existing imports of com.example.common.integration.realtime.RealtimeContractVersions still work.
- Existing imports of com.example.common.integration.contract.RealtimeContractConventions still work.

No service-module import rewrites were required.

---

## Anything Deferred To Later Phases

Deferred intentionally (out of Phase 4 scope or unsafe without service migration):

1. Full import migration in downstream services to new Redis-owned classes.
2. Physical deletion of old bridge classes and deprecated constants.
3. Removal of CHANNEL_PREFIX_WS and other transport-specific legacy aliases.
4. Broader package reorganization (Phase 5+ and later).
5. Kafka topic move and remaining transport/API renames (future phases).

---

## Risk Notes

1. Because common-events cannot directly depend on common-redis (module direction), bridges use runtime resolution for delegation and safe fallback literals.
2. This preserves compile/runtime compatibility without introducing dependency cycles.
3. Final cleanup still requires planned later-phase service migration and bridge retirement.
