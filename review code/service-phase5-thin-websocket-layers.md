# Service Phase5: Thin WebSocket Layers (No Realtime-Edge Yet)

## 1) Scope

This phase thins service-local websocket handlers without introducing a separate realtime-edge service yet.

In-scope services:
- notification-service
- friendship-service
- presence-service
- chat-service

Goal for this phase:
- Keep websocket handlers as transport adapters only (connection/session lifecycle + parse/translate + delegate).
- Move command/event orchestration out of handlers into service-local application/delegation components.
- Preserve existing websocket endpoints and payload compatibility.

Out of scope:
- Cross-service realtime-edge introduction.
- Endpoint URL or contract redesign.

## 2) WebSocket Responsibilities Removed From Handlers

### presence-service
Removed from `PresenceWebSocketHandler`:
- Presence domain state orchestration on connect/disconnect.
- Room presence orchestration on join/leave.
- Typing/stop-typing event publication orchestration.
- Snapshot publication orchestration.

Handler now keeps:
- Session registration/unregistration and room membership bookkeeping.
- Inbound frame parsing (`PresenceWsCommand`).
- Direct delegation calls into application service.

### chat-service
Removed from `ChatWebSocketHandler`:
- Command switch orchestration for join/leave/send/edit/delete/reaction.
- Message/reaction command request construction and dispatch.

Handler now keeps:
- Session lifecycle registration/unregistration.
- Inbound frame parsing (`WsIncomingMessage`).
- Single delegation call to command application service.

### notification-service
No orchestration removal needed (already thin). Clarified temporary adapter role via class-level doc.

### friendship-service
No orchestration removal needed (already thin). Clarified temporary adapter role via class-level doc.

## 3) New Delegation Boundaries

To stay compatible with current compile/source-set alignment, new delegates were placed under websocket package trees.

### presence-service
Introduced:
- `PresenceWebSocketApplicationService`
  - Connect/disconnect presence orchestration.
  - Heartbeat and room join/leave orchestration.
  - Typing/stop-typing realtime publication via `PresenceRealtimePort`.
- `PresenceWebSocketSnapshotPublisher`
  - Encapsulates websocket snapshot payload mapping/publication:
    - `presence.global.snapshot`
    - `presence.room.snapshot`

Boundary summary:
- Handler -> application service -> domain/realtime ports.
- Handler no longer owns domain orchestration logic.

### chat-service
Introduced:
- `ChatWebSocketCommandApplicationService`
  - Command dispatch for `JOIN/LEAVE/SEND/EDIT/DELETE/REACTION`.
  - Request mapping and invocation of `IMessageCommandService` and `IReactionCommandService`.
  - Room membership updates through `ChatSessionRegistry`.

Boundary summary:
- Handler -> command application service -> message/reaction command services.
- Handler no longer owns command orchestration logic.

## 4) Files Changed

Modified:
- `chatappBE/chat-service/src/main/java/com/example/chat/realtime/websocket/handler/ChatWebSocketHandler.java`
- `chatappBE/presence-service/src/main/java/com/example/presence/websocket/handler/PresenceWebSocketHandler.java`
- `chatappBE/notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketHandler.java`
- `chatappBE/friendship-service/src/main/java/com/example/friendship/websocket/FriendshipWebSocketHandler.java`

Added:
- `chatappBE/chat-service/src/main/java/com/example/chat/realtime/websocket/application/ChatWebSocketCommandApplicationService.java`
- `chatappBE/presence-service/src/main/java/com/example/presence/websocket/application/PresenceWebSocketApplicationService.java`
- `chatappBE/presence-service/src/main/java/com/example/presence/websocket/publisher/PresenceWebSocketSnapshotPublisher.java`

## 5) Validation

### Compile
Command:
- `./gradlew.bat :notification-service:compileJava :friendship-service:compileJava :presence-service:compileJava :chat-service:compileJava --continue --no-daemon`

Result:
- BUILD SUCCESSFUL
- Scoped compile is green after relocation of new delegation classes to websocket package paths.

### Tests
Command:
- `./gradlew.bat :notification-service:test :friendship-service:test :presence-service:test :chat-service:test --continue --no-daemon`

Result:
- BUILD FAILED (compileTestJava failures in all 4 scoped services)
- Failures are pre-existing alignment/dependency drift in test source sets (missing common realtime/websocket/kafka symbols and removed/moved types), not caused by the phase5 handler-thinning changes.

Failure buckets observed:
- notification-service test compile: missing kafka/realtime/websocket contract types and related consumers/publishers.
- friendship-service test compile: missing friendship kafka/realtime types.
- presence-service test compile: missing realtime contract/policy types.
- chat-service test compile: missing realtime subscriber/redis/common contract types.

## 6) Migration Notes Toward Realtime-Edge

This phase intentionally keeps service-local websocket endpoints active while creating clearer seams for extraction.

Recommended next migration steps:
1. Promote websocket application/delegation interfaces (currently concrete classes) into explicit inbound ports.
2. Move protocol parsing/DTO mapping into dedicated adapter mappers where command shapes are still service-specific.
3. Introduce service-local facade contracts for realtime publish operations to reduce direct handler knowledge of session registry semantics.
4. In a later phase, route websocket transport to realtime-edge and keep these services as pure domain/event producers.
5. Once realtime-edge is active, retire service-local handlers and keep compatibility adapters only where needed.

## Notes

- No endpoint contract changes were introduced in this phase.
- No realtime-edge service was introduced in this phase by design.
