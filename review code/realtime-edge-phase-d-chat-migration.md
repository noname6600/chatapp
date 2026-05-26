# Phase D - Chat Migration to realtime-edge-service

## Summary

Phase D introduces the additive chat command bridge in realtime-edge-service. Edge websocket chat commands are now forwarded to existing chat-service REST endpoints while edge keeps session ownership and room subscription tracking.

No destructive change was made to chat-service websocket internals. This keeps rollback simple.

---

## What Was Implemented

### realtime-edge-service: Rest Chat Command Router

File: chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/out/chat/RestChatCommandRouter.java

New edge HTTP client that forwards chat commands to chat-service with bearer token pass-through and bounded timeouts.

Supported commands:
- SEND -> POST /api/v1/messages
- EDIT -> PUT /api/v1/messages/{messageId}
- DELETE -> DELETE /api/v1/messages/{messageId}
- REACTION -> POST /api/v1/messages/{messageId}/reactions/{emoji}
- JOIN -> POST /api/v1/rooms/{roomId}/join
- LEAVE -> POST /api/v1/rooms/{roomId}/leave
- PIN -> POST /api/v1/rooms/{roomId}/pins?messageId={messageId}
- UNPIN -> DELETE /api/v1/rooms/{roomId}/pins/{messageId}

Behavior:
- Access token is required for forwarding.
- Errors are logged with command and identifiers, then surfaced as IllegalStateException.
- Timeouts are configurable via application config.

### realtime-edge-service: Websocket Chat Routing

File: chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java

The /ws/chat path now:
- Parses incoming chat command payloads.
- Forwards supported commands through RestChatCommandRouter.
- Maintains edge local subscription state for room channels on JOIN and LEAVE.
- Returns websocket error payload for malformed chat command inputs.

### realtime-edge-service: Chat Router Contract

File: chatappBE/realtime-edge-service/src/main/java/com/example/realtime/routing/command/IChatCommandRouter.java

New interface defining edge-to-chat command forwarding surface.

### realtime-edge-service: Config

File: chatappBE/realtime-edge-service/src/main/resources/application.yaml

Added:
- services.chat.url
- services.chat.http.connect-timeout-ms
- services.chat.http.read-timeout-ms

---

## Delivery Model

Chat fanout delivery remains on existing hardened edge delivery path:
- Redis listener receives realtime.chat.room.* events.
- ChatRealtimeDeliveryService performs local delivery and cross-instance handoff.

Phase D command work does not claim exactly-once or replay semantics.

---

## Ownership Model

- Edge owns websocket connection lifecycle and room subscription tracking.
- Chat-service owns business logic, persistence, validation, and event publication.
- Existing chat-service REST APIs are reused; no duplicate command logic was added in edge.

---

## Rollback Model

Rollback path is preserved:
- Chat-service domain logic is unchanged.
- Forwarding is additive at edge.
- If edge command forwarding is disabled or bypassed, clients can revert to previous route behavior without data migration.

---

## Validation Performed

- realtime-edge-service focused router test: RestChatCommandRouterTest
- realtime-edge-service full test suite: :realtime-edge-service:test

Result: passing.

---

## Remaining Scope (Deliberately Deferred)

- Friendship migration remains out of scope for Phase D.
- No protocol redesign was attempted; existing chat payload shape is preserved.
- No new reliability guarantees were introduced beyond current edge delivery model.
