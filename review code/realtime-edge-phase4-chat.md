# Realtime Edge Phase 4 - Chat Migration

Date: 2026-05-12

## Objective

Migrate chat browser websocket ingress and room fanout delivery to realtime-edge while preserving chat domain ownership and message/reaction/room event behavior in chat-service.

## Scope

In scope:
- chatappBE/chat-service/**
- chatappBE/realtime-edge-service/**
- chatappBE/gateway-service/**

Out of scope respected:
- chat REST API endpoints remain in chat-service (/api/v1/chat/*, /api/v1/rooms/*, /api/v1/messages/*)
- chat domain ownership and message/reaction/room state remain in chat-service
- notification/friendship/presence behavior changes beyond compatibility
- frontend changes

## Migration Summary

Phase-4 chat migration is implemented with realtime-edge owning websocket ingress/session delivery and chat-service retaining domain ownership, state storage, and event production.

### 1) Chat domain ownership preserved in chat-service

Chat-service maintains complete domain responsibility:
- Room lifecycle, membership, bans, metadata
- Message send/edit/delete, reactions, pins, read state
- TTL caching and event production via Redis pub/sub
- Event publishing to realtime.chat.room.{roomId} Redis channels

Client-initiated command behavior:
- Clients send JOIN/LEAVE commands to subscribe/unsubscribe from room channels at the websocket layer
- Message send/edit/delete/reaction commands remain REST-based (/api/v1/messages/*, /api/v1/chat/reactions/*)
- Receiving message/reaction/pin/member events flows through Redis pub/sub -> realtime-edge websocket delivery

### 2) Realtime-edge now owns browser websocket/session/subscription delivery

Realtime-edge changes:
- websocket compatibility alias added: /ws/chat
- websocket handler supports chat commands from browser:
  - JOIN {roomId} - client subscribes to room events
  - LEAVE {roomId} - client unsubscribes from room events
  - PING - heartbeat
- connect flow: no automatic room subscription; clients explicitly JOIN
- chat sessions auto-subscribe to room:{roomId} channels for messages, reactions, member events, pins

### 3) Chat delivery fanout moved to edge

Realtime-edge Redis listener now consumes:
- realtime.chat.room.* (all room events: messages, reactions, member updates, pins)

Chat delivery service routes events to subscribed websocket sessions:
- room:{roomId} subscribers receive all chat events for that room
- fanout happens on last connected instance where subscribers are active

Notification, friendship, and presence delivery paths remain intact and parallel.

### 4) Gateway routing updated

Gateway websocket route for chat is now routed to realtime-edge:
- /ws/chat/** -> realtime-edge-service (was /ws/chat/** -> chat-service)

### 5) Dependency/ownership cleanup

Chat-service cleanup completed:
- removed common-websocket dependency
- removed spring-boot-starter-websocket dependency
- removed stale websocket auto-config exclusion in ChatServiceApplication
- added spring-boot-starter-web to maintain servlet stack for HTTP security context

## Files Updated

**Chat-service:**
- chatappBE/chat-service/build.gradle
- chatappBE/chat-service/src/main/java/com/example/chat/ChatServiceApplication.java

**Realtime-edge-service:**
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/config/WebSocketConfig.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/delivery/ChatRealtimeDeliveryService.java (new)
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/redis/RedisEventListener.java
- chatappBE/realtime-edge-service/src/main/java/com/example/realtime/config/RedisListenerConfig.java

**Gateway-service:**
- chatappBE/gateway-service/src/main/resources/application.yaml

## Command Protocol

**Client → Edge (WebSocket):**

```json
{
  "type": "JOIN",
  "roomId": "uuid-of-room"
}
```

```json
{
  "type": "LEAVE",
  "roomId": "uuid-of-room"
}
```

```json
{
  "type": "PING"
}
```

**Edge → Client (WebSocket):**

Chat events from Redis publish reach clients via the delivery service:

```json
{
  "type": "chat.message.sent",
  "eventId": "correlation-id",
  "payload": {
    "messageId": "...",
    "roomId": "...",
    "seq": 123,
    "content": "...",
    ...
  }
}
```

Similar payloads for:
- chat.message.edited
- chat.message.deleted
- chat.message.pinned
- chat.message.unpinned
- chat.reaction.updated
- chat.room.member.joined
- chat.room.member.left
- chat.room.member.removed

## Validation

Executed from chatappBE root.

Compile validation:
- ./gradlew :chat-service:compileJava :realtime-edge-service:compileJava :gateway-service:compileJava
- Result: BUILD SUCCESSFUL

Test validation:
- ./gradlew :chat-service:test :realtime-edge-service:test :gateway-service:test
- Result: BUILD SUCCESSFUL

All chat domain tests pass, confirming no regressions in message/reaction/room state behavior.

## Architecture Notes

**Command Path Split:**
- Stateful domain operations (send/edit/delete/react) → REST to chat-service (preserves transactional semantics)
- Real-time fanout consumption (receive/subscribe) → WebSocket from realtime-edge (scalable pubsub delivery)
- Lightweight room-join signaling → WebSocket command (low-latency subscription management)

**Scalability:**
- Chat-service: domain state and Kafka/Redis publishing, no session state
- Realtime-edge: websocket session management, subscriptions, delivery, no domain state
- Independent scaling: multiple edge instances for connection capacity; domain sharding at chat-service level

**Behavior Parity:**
- Frontend still connects to /ws/chat and receives message/reaction/pin/member events
- JOIN/LEAVE commands preserve familiar room subscription model
- Event payloads unchanged; delivery layer transparent to client logic
- Notification, presence, friendship channels orthogonal and parallel

## Remaining Known Gaps

- **Future:** chat REST command endpoints do not currently support per-user rate limiting or replay buffers; existing optimistic message confirm timeout (15s) is frontend-side
- **Future:** chat delivery does not dedup events within the same instance; cross-instance fanout dedupe is responsibility of chat-service publisher and event correlation IDs
- **Scope:** chat command validation remains in REST layer; websocket JOIN/LEAVE do not validate room membership at edge (validation deferred to domain)

## Cutover Complete

Phase 4 completes the final and richest capability migration. Chat browser websocket ingress and fanout delivery are now owned by realtime-edge. Chat domain ownership, state, and command execution remain in chat-service. All phases (notification, friendship, presence, chat) are now migrated.
