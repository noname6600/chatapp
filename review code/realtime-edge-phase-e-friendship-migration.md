# Phase E - Friendship Migration to realtime-edge-service

## Summary

Phase E adds an additive friendship command bridge through realtime-edge-service while keeping friendship domain logic in friendship-service and preserving the legacy websocket rollback path.

This phase does not redesign friendship business rules. It only adds edge command forwarding and a minimal friendship-service HTTP command ingress for existing domain services.

---

## Scope Implemented

### 1) Edge inbound friendship command routing

Implemented in realtime-edge-service:

- `IFriendshipCommandRouter`
- `FriendshipCommandRequest`
- `RestFriendshipCommandRouter`
- `CommandDispatcher` friendship dispatch branch

Supported command envelope shape at edge websocket command level:

- `domain = friendship` or `type = friendship.command`
- `command` string
- `payload.targetUserId` (fallback `payload.userId`)
- optional `requestId`

Forwarding behavior:

- Requires access token from websocket-authenticated session context
- Forwards to friendship-service at `POST /api/v1/friends/realtime/commands`
- Passes bearer token through `Authorization: Bearer <token>`
- Uses explicit connect/read timeouts
- Emits debug/warn logs with command, targetUserId, requestId
- Raises `IllegalStateException` on forwarding failure

### 2) Friendship-service minimal realtime command endpoint

Implemented in friendship-service:

- `FriendshipRealtimeCommandRequest`
- `FriendshipRealtimeCommandController`

Controller behavior:

- Endpoint: `POST /api/v1/friends/realtime/commands`
- Authenticated user extracted from JWT subject via existing helper
- Reuses existing `IFriendCommandService` methods
- No domain-model redesign

Supported commands (exact mapping):

- `send-request`, `send-friend-request` -> `sendRequest`
- `accept-request` -> `accept`
- `decline-request` -> `decline`
- `cancel-request` -> `cancel`
- `unfriend` -> `unfriend`
- `block` -> `block`
- `unblock` -> `unblock`

Validation:

- `command` required
- `targetUserId` required
- Unsupported command -> bad request

### 3) Outbound friendship delivery through edge

Phase E keeps and reuses the existing friendship outbound delivery path in realtime-edge-service.

Current outbound model:

- Edge consumes friendship Kafka events via friendship consumers
- `FriendshipRealtimeDeliveryService` delivers to user-scoped sessions
- Delivery reuses existing edge session registry and cross-instance handoff foundations already in service

No new outbound contract shape was invented in this phase.

---

## Contract Honesty

Phase E guarantees:

- Edge forwards only explicit supported friendship commands listed above
- Friendship command payload requires `targetUserId`
- Friendship domain execution remains in friendship-service
- Websocket command forwarding is additive and reversible

Phase E does not guarantee:

- Exactly-once command processing
- Replay-safe command recovery
- Cross-service transactional command semantics
- New friendship event schema changes

Payload/event alignment notes:

- Edge command request contract is explicit (`command`, `targetUserId`, `requestId`)
- Outbound friendship events continue using existing Kafka/event contracts already consumed by edge

---

## Rollback and Dual-Path Safety

Rollback safety preserved by design:

- Legacy friendship websocket path remains in friendship-service (`/ws/friendship`)
- Gateway friendship websocket route remains available for non-edge routing strategy
- Phase E adds edge forwarding but does not remove existing friendship websocket components

Operational rollback option:

- Route clients away from edge friendship command path back to prior service-local behavior
- Keep new HTTP command endpoint deployed but unused

---

## Files Added/Updated

Realtime-edge-service:

- `src/main/java/com/example/realtime/routing/command/IFriendshipCommandRouter.java`
- `src/main/java/com/example/realtime/routing/command/FriendshipCommandRequest.java`
- `src/main/java/com/example/realtime/adapter/out/friendship/RestFriendshipCommandRouter.java`
- `src/main/java/com/example/realtime/routing/CommandDispatcher.java`
- `src/main/resources/application.yaml` (friendship URL and timeout config)
- `src/test/java/com/example/realtime/adapter/out/friendship/RestFriendshipCommandRouterTest.java`
- `src/test/java/com/example/realtime/routing/CommandDispatcherTest.java` (friendship branch coverage)

Friendship-service:

- `src/main/java/com/example/friendship/dto/FriendshipRealtimeCommandRequest.java`
- `src/main/java/com/example/friendship/controller/FriendshipRealtimeCommandController.java`
- `src/test/java/com/example/friendship/controller/FriendshipRealtimeCommandControllerTest.java`

---

## Validation Executed

Targeted validation run:

- `:realtime-edge-service:test --tests "*CommandDispatcherTest" --tests "*RestFriendshipCommandRouterTest"`
- `:friendship-service:compileJava`

Result:

- BUILD SUCCESSFUL

Note:

- This phase report records targeted slice validation, not a full backend integration soak.
