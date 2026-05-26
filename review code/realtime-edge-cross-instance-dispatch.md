# Realtime Edge Cross-Instance Dispatch

## Summary

This change adds minimal edge-to-edge Redis handoff so a non-owning edge instance can forward outbound realtime delivery to the edge instance that owns the target websocket session.

Scope is infrastructure-only in realtime-edge-service.

## Handoff Design

### Problem

- Redis session registry now provides global session/subscription metadata.
- Only the owning edge instance has the live in-memory WebSocketSession object.
- Local-only delivery misses remote-owned sessions in multi-instance mode.

### Model

A non-owning instance publishes an internal handoff event to Redis.

- Channel model: `realtime.edge.handoff.{targetInstanceId}`
- Event includes:
  - `targetInstanceId`
  - `sourceInstanceId`
  - `deliveryType`
  - `eventType`
  - `originalEventId`
  - `subscriptionKey` and optional `targetUserId`
  - `targetSessionIds`
  - `payload`
  - `handoffEventId`, `createdAt`

Owning instance consumes and performs the websocket send locally.

## Redis Channel Model

- Existing domain ingress channels remain unchanged:
  - `realtime.chat.room.*`
  - `realtime.notification.user.*`
  - `realtime.presence.*`
- New internal channel pattern:
  - `realtime.edge.handoff.*`

Handoff listener ignores events not targeting local `instanceId`.

## Local vs Remote Delivery Behavior

For each delivery request:

1. Resolve candidate sessions globally (`findByUserId` / `findByChannel`).
2. Split by ownership (`instanceId`):
   - local-owned sessions
   - remote-owned sessions grouped by target instance
3. Deliver local-owned sessions directly.
4. Publish one handoff event per remote target instance.

No local redis re-send is used for already local sends.

## Delivery Sequence

1. Domain event arrives on edge (Redis/Kafka ingress).
2. Delivery service computes target sessions from registry.
3. Local sends happen immediately on current instance.
4. Remote groups publish handoff events to `realtime.edge.handoff.{instanceId}`.
5. Target edge consumes handoff and sends to locally owned sessions.

## Integrated Services

Cross-instance dispatch now wraps delivery for:

- notification delivery
- presence delivery
- chat delivery
- friendship delivery
- shared `EventDeliveryService` abstraction

## Semantics and Limits

Current semantics are honest and limited:

- At-least-attempted handoff, not exactly-once.
- Duplicate prevention across handoff boundaries is not guaranteed.
- Replay/recovery for dropped handoff events is not implemented.
- Stale ownership metadata can still cause misses until heartbeat/lease cleanup is added.

## Failure Modes

1. Redis unavailable during publish
   - Local sends still succeed.
   - Remote handoff publish fails and is logged.
2. Target instance down
   - Handoff event may be published but not consumed by a live owner.
3. Stale session ownership in registry
   - Handoff may target wrong instance or dead session IDs.
4. Consumer processing error
   - Message is logged; no replay queue yet.

## Configuration

Added in `application.yaml`:

- `realtime.dispatch.handoff.enabled` (default `false`)
- `realtime.dispatch.handoff.channel-prefix` (default `realtime.edge.handoff`)

Existing instance identity remains from:

- `realtime.session-registry.instance-id`

## Key Classes

- `EdgeDeliveryHandoffEvent`
- `EdgeDeliveryHandoffPublisher`
- `EdgeDeliveryHandoffListener`
- `EdgeCrossInstanceDispatchCoordinator`

This remains intentionally minimal and compile-safe, without introducing domain-level redesign.
