# Phase 1D - Presence Room Authorization (presence-service)

## Files changed
- chatappBE/presence-service/src/main/java/com/example/presence/authorization/RoomAuthorizationService.java
- chatappBE/presence-service/src/main/java/com/example/presence/authorization/ChatRoomAuthorizationService.java
- chatappBE/presence-service/src/main/java/com/example/presence/controller/PresenceController.java
- chatappBE/presence-service/src/main/java/com/example/presence/controller/PresenceEdgeCommandController.java
- chatappBE/presence-service/src/main/java/com/example/presence/websocket/handler/PresenceWebSocketHandler.java
- chatappBE/presence-service/src/main/resources/application.yaml
- chatappBE/presence-service/src/test/java/com/example/presence/controller/PresenceEdgeCommandControllerIntegrationTest.java
- chatappBE/presence-service/src/test/java/com/example/presence/websocket/handler/PresenceWebSocketHandlerDisconnectTest.java
- chatappBE/presence-service/src/test/java/com/example/presence/websocket/handler/PresenceWebSocketHandlerRoomAuthorizationTest.java

## Endpoints/flows now protected
1. Presence room read endpoint
- `GET /api/v1/presence/room/{roomId}` now requires verified room membership.

2. Edge-originated room-scoped commands
- `POST /api/v1/presence/ws/rooms/{roomId}/join`
- `POST /api/v1/presence/ws/rooms/{roomId}/leave`
- `POST /api/v1/presence/ws/rooms/{roomId}/typing`
- `POST /api/v1/presence/ws/rooms/{roomId}/stop-typing`
- All now require verified room membership before state/event actions.

3. Local websocket room/typing commands
- `presence.room.join`
- `presence.room.typing`
- `presence.room.stop-typing`
- All now require verified room membership before room-scoped presence operations.

## How authorization is checked
- Added service-local adapter `RoomAuthorizationService`.
- Implemented by `ChatRoomAuthorizationService` using chat-service as source of truth for membership authorization.
- Check mechanism:
  - Authenticated call to chat-service endpoint `GET /api/v1/rooms/{roomId}/member-count` with caller token (`Authorization: Bearer <token>`).
  - Success => authorized room member.
  - Failure => denied (`FORBIDDEN`).
- Token source by path:
  - REST controllers: token from authenticated `Jwt` principal.
  - Local websocket handler: token from session `accessToken` attribute or `token` query param fallback.

## Regression risks
- If chat-service room authorization endpoint is unavailable or slow, room-scoped presence and typing operations can be denied.
- Local websocket clients missing token propagation may lose room join/typing behavior until token forwarding is correct.
- Authorization is stricter now, so previously permissive room presence reads/typing from non-members will be rejected.

## Test commands to run
Executed:
- `./gradlew.bat :presence-service:compileJava :presence-service:compileTestJava --no-daemon`
- `./gradlew.bat :presence-service:test --tests "com.example.presence.controller.PresenceEdgeCommandControllerIntegrationTest" --tests "com.example.presence.websocket.handler.PresenceWebSocketHandlerDisconnectTest" --tests "com.example.presence.websocket.handler.PresenceWebSocketHandlerRoomAuthorizationTest" --no-daemon`

Result:
- `BUILD SUCCESSFUL`
