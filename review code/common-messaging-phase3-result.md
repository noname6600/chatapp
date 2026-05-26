# Phase 3 Implementation Result

Status: Complete
Date: 2026-04-30
Scope: common-events, common-kafka, common-redis
Approach: Event type normalization with backward-compatible alias handling

---

## OpenSpec Context

Using change: align-kafka-redis-pubsub-structure
Schema: spec-driven
Progress from OpenSpec: 12/12 tasks complete (state: all_done)
Instruction from OpenSpec: change is ready to archive

Note: This Phase 3 implementation was applied as an additional compatibility pass based on the source-of-truth review/proposal documents.

---

## Summary

Phase 3 goals were implemented for presence event type normalization and migration-safe reads:

1. Invalid underscored presence event type values were replaced with validator-compliant hyphenated values.
2. Backward-compatible alias handling was added for old wire values.
3. Redis deserialization, payload resolution, and subscriber dispatch now accept both old and new values.
4. Warning logs were added when deprecated old values are encountered.
5. No package reorganization or service migration was performed.

Compile verification:
- :common:common-events:compileJava -> PASS
- :common:common-redis:compileJava -> PASS

---

## Event Type Values Changed

File changed:
- chatappBE/common/common-events/src/main/java/com/example/common/integration/presence/PresenceEventType.java

Updated enum values:
- presence.user.status_changed -> presence.user.status-changed
- presence.room.stop_typing -> presence.room.stop-typing
- presence.global.online_users -> presence.global.online-users
- presence.room.online_users -> presence.room.online-users

These values now comply with the shared validator pattern:
- ^[a-z0-9-]+(\.[a-z0-9-]+)*$

---

## Alias Mappings Added

Location:
- chatappBE/common/common-events/src/main/java/com/example/common/integration/presence/PresenceEventType.java

Deprecated -> normalized mapping:
- presence.user.status_changed -> presence.user.status-changed
- presence.room.stop_typing -> presence.room.stop-typing
- presence.global.online_users -> presence.global.online-users
- presence.room.online_users -> presence.room.online-users

New helpers:
- normalize(String): converts deprecated alias to normalized value
- isDeprecatedAlias(String): checks whether incoming value is deprecated
- legacyAliasOf(String): reverse lookup for compatibility fallback
- fromValue(String): now accepts old and new values, returns canonical enum constant

Warning behavior:
- fromValue logs WARNING when deprecated values are used.

---

## Compatibility Behavior

### Read compatibility preserved

Old underscored values remain readable in Redis flow via alias-aware handling:

1. Redis deserialization
- File: chatappBE/common/common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisMessageSerializer.java
- Behavior:
  - Reads eventType from wire as-is.
  - Normalizes only for payload class resolution.
  - Preserves original wire eventType in deserialized RedisMessage.
  - Logs WARNING when deprecated old value is received.

2. Redis payload registry resolution
- File: chatappBE/common/common-redis/src/main/java/com/example/common/redis/registry/DefaultRedisMessageRegistry.java
- Behavior:
  - Tries exact key first.
  - Falls back old -> new normalized key.
  - Falls back new -> old legacy key.
  - Logs WARNING when fallback path is used.

3. Redis subscriber dispatch routing
- File: chatappBE/common/common-redis/src/main/java/com/example/common/redis/dispatcher/RedisMessageDispatcher.java
- Behavior:
  - Tries exact subscriber eventType key.
  - Falls back old -> new normalized key.
  - Falls back new -> old legacy key.
  - Logs WARNING when alias fallback dispatch occurs.

### Publish behavior

- Presence enum now emits normalized hyphenated values when producers use PresenceEventType.value().
- Validator behavior remains strict (underscored values are still invalid for new publish paths unless producers are still using legacy raw strings).
- No legacy API was removed.

### Wire compatibility

- Existing old wire payloads with underscored presence event types remain readable.
- New normalized values are also routable for services still keyed to legacy aliases (via dispatcher/registry fallback).

---

## Files Modified

1. chatappBE/common/common-events/src/main/java/com/example/common/integration/presence/PresenceEventType.java
- Normalized 4 enum values
- Added alias maps and normalization helpers
- Added deprecated-value warning logging in fromValue

2. chatappBE/common/common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisMessageSerializer.java
- Added alias normalization for payload resolution
- Added warning logs for deprecated wire event types
- Preserved original wire eventType in deserialized message

3. chatappBE/common/common-redis/src/main/java/com/example/common/redis/registry/DefaultRedisMessageRegistry.java
- Added alias-aware fallback lookup in resolvePayload
- Added warning logs when fallback is used

4. chatappBE/common/common-redis/src/main/java/com/example/common/redis/dispatcher/RedisMessageDispatcher.java
- Added alias-aware fallback subscriber lookup
- Added warning logs when alias-based dispatch occurs

No changes were made in common-kafka for Phase 3.

---

## Remaining Migration Risks

1. Services that hardcode legacy underscored strings in publish paths may still fail strict validation when bypassing PresenceEventType enum values.
2. Kafka-specific inbound compatibility for legacy presence eventType aliases is not implemented here because common-kafka currently has no shared deserializer/dispatcher layer equivalent to Redis.
3. Warning logs for deprecated aliases may be noisy during migration spikes; monitor log volume.
4. Full removal of alias fallback should wait until all services publish/consume normalized values and migration verification is complete.

---

## Phase Status

Phase 1: Complete
Phase 2: Complete
Phase 3: Complete (this document)
Phase 4+: Not started in this implementation
