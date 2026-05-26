# Phase 1C - Realtime Edge JOIN Fix

## Scope
- Primary service changed: realtime-edge-service
- chat-service changes: none required
- No gateway route cutover performed
- No legacy domain websocket endpoint removal performed

## Files changed
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/out/chat/RestChatCommandRouter.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/subscription/ChannelSubscriptionManager.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/routing/command/IChatCommandRouter.java
- chatappBE/realtime-edge-service/src/test/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandlerChatRoutingTest.java
- chatappBE/realtime-edge-service/src/test/java/com/example/realtime/adapter/out/chat/RestChatCommandRouterTest.java
- chatappBE/realtime-edge-service/src/test/java/com/example/realtime/subscription/ChannelSubscriptionManagerTest.java

## Old unsafe behavior
- In chat websocket handling, JOIN did this sequence:
  1) call chat mutation route `POST /api/v1/rooms/{roomId}/join`
  2) subscribe edge session to `room:{roomId}`
- This meant a websocket subscribe intent could mutate room membership state.

## New safe JOIN semantics
- JOIN is now subscription-only.
- JOIN sequence is now:
  1) verify membership/access for the room
  2) if authorized, subscribe edge session to `room:{roomId}`
  3) if unauthorized, reject JOIN and do not subscribe
- JOIN no longer calls chat room-join mutation endpoint.

## How membership verification works
- Added non-mutating access check contract in edge router:
  - `IChatCommandRouter.canAccessRoom(accessToken, roomId)`
- `RestChatCommandRouter` implementation performs authenticated GET to:
  - `/api/v1/rooms/{roomId}/member-count`
- Authorization result handling:
  - 2xx => access allowed
  - non-2xx/exception => access denied
- Channel authorization hardening:
  - `ChannelSubscriptionManager` now requires `canAccessRoom` for `room:`, `presence:`, and `typing:` channels.

## New internal dependency introduced
- realtime-edge `ChannelSubscriptionManager` now depends on `IChatCommandRouter` to perform room membership/access checks.
- No new service dependency beyond existing edge -> chat HTTP integration.
- No new chat-service endpoint was introduced; existing room read endpoint is reused as authorization-style check.

## Regression risks
- If chat-service room-membership read endpoint is unavailable/slow, room/presence/typing subscriptions may be denied.
- Clients that previously relied on JOIN to implicitly become room members will no longer work (intended hardening).
- JOIN denial now returns websocket error response for unauthorized room subscriptions.

## Focused tests added/updated
- Updated `RealtimeWebSocketHandlerChatRoutingTest`:
  - JOIN subscribes only when access check passes
  - JOIN denied when access check fails (no subscription)
- Updated `RestChatCommandRouterTest`:
  - `canAccessRoom` returns true on 200
  - `canAccessRoom` returns false on 403
- Added `ChannelSubscriptionManagerTest`:
  - room/presence channels require verified membership
  - user channel self-subscribe behavior preserved

## Verification commands
Executed:
- `./gradlew.bat :realtime-edge-service:compileJava :realtime-edge-service:compileTestJava --no-daemon`

Result:
- `BUILD SUCCESSFUL`
