# Phase 1B - Chat Room Authorization (chat-service only)

## Scope
- Service: chat-service only
- Focus: room-scoped read/reaction/subscription authorization hardening
- Out of scope: common-* modules, broad room-module redesign, websocket endpoint removal

## Files changed
- chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/RoomMembershipGuard.java
- chatappBE/chat-service/src/main/java/com/example/chat/modules/room/service/impl/RoomMembershipGuardImpl.java
- chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/query/MessageQueryService.java
- chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/pipeline/reaction/steps/PersistReactionStep.java
- chatappBE/chat-service/src/main/java/com/example/chat/modules/room/controller/RoomController.java
- chatappBE/chat-service/src/main/java/com/example/chat/realtime/websocket/handler/ChatWebSocketHandler.java
- chatappBE/chat-service/src/test/java/com/example/chat/modules/message/application/query/MessageQueryServiceTest.java
- chatappBE/chat-service/src/test/java/com/example/chat/modules/message/application/pipeline/reaction/steps/PersistReactionStepTest.java
- chatappBE/chat-service/src/test/java/com/example/chat/modules/room/controller/RoomControllerInviteRouteTest.java
- chatappBE/chat-service/src/test/java/com/example/chat/modules/room/controller/RoomControllerAuthorizationTest.java
- chatappBE/chat-service/src/test/java/com/example/chat/modules/room/service/impl/RoomMembershipGuardImplTest.java

## Exact checks added
1. Centralized membership guard
- Added RoomMembershipGuard with ensureRoomMember(roomId, userId).
- Implementation uses room_member existence check and throws FORBIDDEN when user is not a member.

2. Message query authorization
- MessageQueryService now calls roomMembershipGuard.ensureRoomMember(roomId, currentUserId) before:
  - getLatestMessages
  - getMessagesBefore
  - getMessagesAround
  - getMessageRange
- getMessagesAround additionally validates messageId belongs to the requested roomId; rejects mismatch with BAD_REQUEST.

3. Reaction authorization
- PersistReactionStep now resolves target message by messageId, then enforces:
  - roomMembershipGuard.ensureRoomMember(message.roomId, userId)
- Reaction create/delete continues only after membership is verified.

4. Room read endpoint authorization
- RoomController now enforces membership before these room-scoped read endpoints:
  - GET /api/v1/rooms/{roomId}/code
  - GET /api/v1/rooms/{roomId}/members
  - GET /api/v1/rooms/{roomId}/members/page
  - GET /api/v1/rooms/{roomId}/member-count

5. Local websocket JOIN authorization
- ChatWebSocketHandler JOIN path now checks that the current session user is a member of target room before subscribing the session.
- Unauthorized JOIN attempts are denied (no subscription performed).

## Endpoints and flows now protected
- Message query API
  - /api/v1/messages/latest
  - /api/v1/messages/before
  - /api/v1/messages/range
  - /api/v1/messages/around
- Reaction flow
  - HTTP reaction toggle endpoint path (via pipeline)
  - Websocket reaction command path (via same pipeline)
- Room-scoped read APIs
  - /api/v1/rooms/{roomId}/code
  - /api/v1/rooms/{roomId}/members
  - /api/v1/rooms/{roomId}/members/page
  - /api/v1/rooms/{roomId}/member-count
- Local websocket subscription
  - WS JOIN command in ChatWebSocketHandler

## Expected rejected behavior for unauthorized users
- Non-member querying room-scoped messages: FORBIDDEN (Not a room member).
- Non-member toggling reaction on a message in a room: FORBIDDEN (Not a room member).
- Non-member requesting room code/member list/member count: FORBIDDEN (Not a room member).
- Non-member issuing websocket JOIN: join is rejected and session is not subscribed to that room.

## Regression risks
- Any internal or client path relying on previous permissive room read endpoints will now be blocked unless user is a room member.
- Websocket JOIN clients that attempted speculative room subscription without membership will no longer subscribe.
- Error-message text and timing may differ in some invalid-input sequences (membership check now happens earlier in query/reaction flows).

## Test updates and commands
Added/updated focused tests:
- MessageQueryServiceTest: guard invocation + unauthorized path
- PersistReactionStepTest: membership check before persistence + unauthorized path
- RoomMembershipGuardImplTest: guard pass/fail unit tests
- RoomControllerAuthorizationTest: room code/member-count membership checks
- RoomControllerInviteRouteTest: constructor wiring update for new dependency

Command executed:
- ./gradlew.bat :chat-service:compileJava :chat-service:compileTestJava --no-daemon

Result:
- BUILD SUCCESSFUL
