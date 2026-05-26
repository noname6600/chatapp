# Realtime Edge Session Registry - Redis Foundation

## Summary

This change adds a Redis-backed realtime session registry foundation for `realtime-edge-service` while preserving the existing in-memory behavior as default.

## Contract and Compatibility

### Interface contract

- Added contract: `IRealtimeSessionRegistry`
- Existing facade retained: `RealtimeSessionRegistry`
- Existing local behavior retained: in-memory remains default (`REALTIME_SESSION_REGISTRY_MODE=in-memory`)

### Backward compatibility

- Existing call sites continue injecting/using `RealtimeSessionRegistry`
- Existing method signatures used by current delivery/websocket logic were preserved
- Additional methods were added for ownership-aware querying:
  - `findByUserIdOwnedByCurrentInstance(UUID userId)`
  - `findByChannelOwnedByCurrentInstance(String channel)`
  - `addSubscription(String sessionId, String channel)`
  - `removeSubscription(String sessionId, String channel)`
  - `getCurrentInstanceId()`

## Implementations

### `InMemoryRealtimeSessionRegistry`

- Mode: `in-memory`
- Purpose: local/dev/test fallback and safe default
- Stores sessions, user index, and subscription index in process memory

### `RedisRealtimeSessionRegistry`

- Mode: `redis`
- Purpose: global multi-instance session/subscription metadata foundation
- Uses Redis hashes and sets to model active sessions and subscriptions

## Redis Data Model and Key Naming

The registry uses these keys:

- `realtime:session:{sessionId}` (hash)
  - `sessionId`
  - `userId`
  - `instanceId`
  - `connectedAt`
  - `lastActivityAt`
  - `state`

- `realtime:user:sessions:{userId}` (set)
  - members: `sessionId`

- `realtime:subscription:sessions:{subscriptionKey}` (set)
  - members: `sessionId`

- `realtime:session:subscriptions:{sessionId}` (set)
  - members: `subscriptionKey`

- `realtime:instance:sessions:{instanceId}` (set)
  - members: `sessionId`

- `realtime:users` (set)
  - members: `userId`

## Instance Ownership Model

Each session stores `instanceId` metadata.

- A live websocket session is owned by exactly one edge instance.
- Registry global queries can return sessions owned by different instances.
- Local delivery/writes must use `OwnedByCurrentInstance` queries because only the owning instance has the in-memory `WebSocketSession` object.

## Local vs Global Behavior

### Global metadata behavior

In Redis mode:

- `findByUserId` and `findByChannel` are global metadata queries.
- They can include sessions from other instances.

### Local delivery behavior

Current delivery path now uses ownership-aware methods:

- `findByUserIdOwnedByCurrentInstance`
- `findByChannelOwnedByCurrentInstance`

This keeps send semantics honest: only local sessions are written to websocket.

## Cleanup Behavior on Disconnect

On unregister:

1. Session hash is removed
2. Session removed from user-session set
3. Session removed from instance-session set
4. Session removed from each subscription set
5. Session subscription set is deleted
6. `realtime:users` entry removed when user has zero sessions

## Configuration Selection

New config in `application.yaml`:

- `realtime.session-registry.mode` (`in-memory` default, `redis` optional)
- `realtime.session-registry.instance-id` (default `${spring.application.name}-local`)

Env overrides:

- `REALTIME_SESSION_REGISTRY_MODE`
- `REALTIME_INSTANCE_ID`

## Known Limitations (Intentional)

This is infrastructure foundation only. It does **not** implement full cross-instance fanout yet.

Not implemented yet:

- Cross-instance event dispatch coordination
- Edge-to-edge pub/sub fanout handoff
- Automatic failover/reclaim of orphaned sessions
- Heartbeat/lease-based stale session reaping

Redis registry currently provides global metadata + local ownership filtering only.

## Files Changed

- `realtime-edge-service/src/main/java/com/example/realtime/connection/IRealtimeSessionRegistry.java`
- `realtime-edge-service/src/main/java/com/example/realtime/connection/InMemoryRealtimeSessionRegistry.java`
- `realtime-edge-service/src/main/java/com/example/realtime/connection/RedisRealtimeSessionRegistry.java`
- `realtime-edge-service/src/main/java/com/example/realtime/connection/RealtimeSessionRegistry.java`
- `realtime-edge-service/src/main/java/com/example/realtime/connection/RealtimeSession.java`
- `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java`
- `realtime-edge-service/src/main/java/com/example/realtime/delivery/ChatRealtimeDeliveryService.java`
- `realtime-edge-service/src/main/java/com/example/realtime/delivery/NotificationRealtimeDeliveryService.java`
- `realtime-edge-service/src/main/java/com/example/realtime/delivery/FriendshipRealtimeDeliveryService.java`
- `realtime-edge-service/src/main/java/com/example/realtime/delivery/PresenceRealtimeDeliveryService.java`
- `realtime-edge-service/src/main/java/com/example/realtime/delivery/EventDeliveryService.java`
- `realtime-edge-service/src/main/resources/application.yaml`
