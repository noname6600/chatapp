# Send Message Flow

> Scope: full end-to-end path from client to WebSocket delivery  
> Last updated: 2026-05-19

---

## Overview

A message can enter the system through **two entry points**:

| Entry | Path |
|---|---|
| REST | `POST /api/v1/messages` → chat-service directly |
| WebSocket command | `SEND` frame on `/ws/chat` → realtime-edge → HTTP → chat-service |

Both paths converge at `MessageCommandService.sendMessage()` and follow the same pipeline from that point on.

---

## Entry Point A — REST

```
Client
  └─ POST /api/v1/messages  (JWT in Authorization header)
       └─ MessageCommandController.sendMessage()
              chat-service
```

**Class:** `MessageCommandController`  
**Package:** `com.example.chat.modules.message.controller`

Extracts `senderId` from the JWT, attaches it to `SendMessageRequest`, delegates to the command service.

---

## Entry Point B — WebSocket SEND Command

```
Client
  └─ WS /ws/chat  →  SEND frame  { type:"SEND", roomId:"...", content:"..." }
       └─ RealtimeWebSocketHandler.handleTextMessage()
            └─ handleChatMessage()
                 └─ RestChatCommandRouter.sendMessage()
                      └─ HTTP POST /api/v1/messages  (forwards client JWT)
                           └─ MessageCommandController  (same as Entry A)
```

**Classes involved:**

| Class | Service | Role |
|---|---|---|
| `RealtimeWebSocketHandler` | realtime-edge | Receives raw WS frame, reads `type` field |
| `RestChatCommandRouter` | realtime-edge | Forwards command to chat-service via Spring RestClient |
| `MessageCommandController` | chat-service | Receives the forwarded HTTP request |

---

## Phase 1 — Command Service & Idempotency

```
MessageCommandController
  └─ MessageCommandService.sendMessage()
        1. filterMentionedUsers()         — strip @mentions not in room
        2. validateInviteBlocks()         — guard invite-block misuse
        3. idempotency check              — if clientMessageId already exists → return cached response
        4. sendPipeline.execute(context)  — hand off to pipeline
```

**Class:** `MessageCommandService`  
**Package:** `com.example.chat.modules.message.application.command.impl`

The idempotency check queries `messageRepository.findByRoomIdAndClientMessageId()`. If found, returns the existing message without re-executing the pipeline — safe for client retries.

---

## Phase 2 — Pipeline Steps

The pipeline is a sequential `PipelineExecutor` built by `PipelineFactory`. Each step receives and mutates a shared `SendMessageContext`.

```
SendMessagePipeline.execute(context)
  │
  ├─ 1. ValidateMessageStep
  ├─ 2. ValidateRoomPermissionStep
  ├─ 3. ExtractMentionStep
  ├─ 4. CreateMessageAggregateStep
  ├─ 5. PersistMessageStep            ← DB write, assigns seq
  ├─ 6. PersistMentionStep
  └─ 7. PublishMessageEventStep       ← fires after transaction commit
```

**All steps package:** `com.example.chat.modules.message.application.pipeline.send.steps`

### Step 1 — `ValidateMessageStep`
- Asserts `roomId` and `senderId` are present
- Asserts message has at least one of: `content`, `attachments`, or renderable `blocks`

### Step 2 — `ValidateRoomPermissionStep`
- Calls `roomPermissionService.canSendMessage(roomId, senderId)`
- Throws `FORBIDDEN` if user is banned or not a member

### Step 3 — `ExtractMentionStep`
- Parses `@mention` user IDs from the request
- Validates they are current room members
- Stores result on context for later persistence

### Step 4 — `CreateMessageAggregateStep`
- If request has `blocks`, serialises them to JSON and derives a plain-text `content`
- Calls `MessageAggregate.create()` — produces the entity ready for persistence
- No DB access at this step

### Step 5 — `PersistMessageStep`  *(transactional)*
- Calls `sequenceService.nextSeq(roomId)` — atomically increments the room's message sequence counter
- Saves `ChatMessage` via `messageRepository.save()`
- Saves `ChatAttachment` records if any
- Writes `savedMessage` and `savedAttachments` back to context

### Step 6 — `PersistMentionStep`
- Persists mention records linking message → mentioned user IDs

### Step 7 — `PublishMessageEventStep`
- Updates the room's `lastMessage` projection synchronously: `roomService.updateLastMessage()`
- Marks room as read for the sender: `roomService.markRoomRead()`
- Registers `TransactionSynchronization.afterCommit()` — event publication fires **only after the DB transaction commits**, preventing phantom events on rollback

---

## Phase 3 — Event Publication

```
PublishMessageEventStep.afterCommit()
  └─ ChatMessageEventPublisherAdapter.publishMessageCreated()
        ├─ Queries room members to build recipientUserIds
        ├─ Queries room type (PRIVATE vs GROUP)
        ├─ ChatMessagePayloadFactory.from()   — builds ChatMessagePayload
        │
        ├─ ChatRedisPublisher.publishMessageSent()
        │     └─ RedisEventPublisher.publish("chat:room:{roomId}", EventEnvelope)
        │              → Redis pub/sub broadcast
        │
        └─ KafkaEventPublisher.publish(TOPIC_CHAT_EVENTS, envelope)
                 → Kafka (durable, for other consumers)
```

**Classes:**

| Class | Package | Role |
|---|---|---|
| `ChatMessageEventPublisherAdapter` | `...infrastructure.redis` | Orchestrates Redis + Kafka publish |
| `ChatMessagePayloadFactory` | `...event.factory` | Builds `ChatMessagePayload` from entity |
| `ChatRedisPublisher` | `...infrastructure.redis` | Wraps payload in `EventEnvelope`, calls `RedisEventPublisher` |
| `DefaultRedisEventPublisher` | `common-redis` | Validates + serialises + calls `StringRedisTemplate.convertAndSend()` |

**Redis channel:** `chat:room:{roomId}`  
**Event type:** `chat.message.sent`  
**Wire format:**
```json
{
  "metadata": {
    "eventId": "uuid",
    "eventType": "chat.message.sent",
    "sourceService": "chat-service",
    "createdAt": "2026-05-19T...",
    "correlationId": "uuid"
  },
  "payload": { ...ChatMessagePayload... }
}
```

---

## Phase 4 — Realtime Edge Delivery

Redis pub/sub broadcasts to **every running instance** of `realtime-edge-service` simultaneously.

```
DefaultRedisEventPublisher → Redis pub/sub
  │
  └─ (all realtime-edge instances receive the event)
       └─ RedisEventListener.onMessage()
             1. Parse EventEnvelope from JSON
             2. Extract eventType, eventId, payload from metadata
             3. Channel starts with "chat:room:" → route to ChatRealtimeDeliveryService
             └─ ChatRealtimeDeliveryService.deliverRoom(roomId, eventType, eventId, body)
                   1. Resolve subscription channel: "room:{roomId}"
                   2. sessionRegistry.findByChannelOwnedByCurrentInstance("room:{roomId}")
                      → returns only sessions owned by THIS instance (no cross-instance duplicates)
                   3. For each session:
                        webSocketSessionStore.findBySessionId()
                        → synchronized send via WebSocketSession.sendMessage()
```

**Classes:**

| Class | Service | Role |
|---|---|---|
| `RedisEventListener` | realtime-edge | `MessageListener` bean — entry point for all Redis pub/sub events |
| `ChatRealtimeDeliveryService` | realtime-edge | Finds local sessions for the room channel, delivers frame |
| `RedisRealtimeSessionRegistry` | realtime-edge | Redis-backed registry, tracks which instance owns each session |
| `RealtimeWebSocketSessionStore` | realtime-edge | In-memory `ConcurrentHashMap<sessionId, WebSocketSession>` |

**WebSocket frame sent to client:**
```json
{
  "type": "chat.message.sent",
  "eventId": "uuid",
  "payload": { ...ChatMessagePayload... }
}
```

---

## Phase 5 — Session Subscription (prerequisite)

For a client to receive messages, their WebSocket session must be **subscribed to the room channel** before the message arrives.

```
Client connects:  WS /ws/chat
  └─ RealtimeWebSocketHandler.afterConnectionEstablished()
        └─ RealtimeSession.create(userId)
             └─ sessionRegistry.register(session)
             └─ webSocketSessionStore.register(sessionId, wsSession)

Client sends JOIN frame:  { type:"JOIN", roomId:"..." }
  └─ RealtimeWebSocketHandler.handleChatMessage()
        └─ "JOIN" → sessionRegistry.addSubscription(sessionId, "room:{roomId}")
```

`findByChannelOwnedByCurrentInstance("room:{roomId}")` only returns sessions that have joined this channel.

---

## Complete Class Reference

| # | Class | Service | Package |
|---|---|---|---|
| 1 | `MessageCommandController` | chat-service | `modules.message.controller` |
| 2 | `RealtimeWebSocketHandler` | realtime-edge | `adapter.in.websocket` |
| 3 | `RestChatCommandRouter` | realtime-edge | `adapter.out.chat` |
| 4 | `MessageCommandService` | chat-service | `modules.message.application.command.impl` |
| 5 | `SendMessagePipeline` | chat-service | `modules.message.application.pipeline.send` |
| 6 | `ValidateMessageStep` | chat-service | `...pipeline.send.steps` |
| 7 | `ValidateRoomPermissionStep` | chat-service | `...pipeline.send.steps` |
| 8 | `ExtractMentionStep` | chat-service | `...pipeline.send.steps` |
| 9 | `CreateMessageAggregateStep` | chat-service | `...pipeline.send.steps` |
| 10 | `PersistMessageStep` | chat-service | `...pipeline.send.steps` |
| 11 | `PersistMentionStep` | chat-service | `...pipeline.send.steps` |
| 12 | `PublishMessageEventStep` | chat-service | `...pipeline.send.steps` |
| 13 | `ChatMessageEventPublisherAdapter` | chat-service | `...infrastructure.redis` |
| 14 | `ChatMessagePayloadFactory` | chat-service | `...event.factory` |
| 15 | `ChatRedisPublisher` | chat-service | `...infrastructure.redis` |
| 16 | `DefaultRedisEventPublisher` | common-redis | `com.example.common.redis.publisher` |
| 17 | `RedisEventListener` | realtime-edge | `adapter.in.redis` |
| 18 | `ChatRealtimeDeliveryService` | realtime-edge | `delivery` |
| 19 | `RedisRealtimeSessionRegistry` | realtime-edge | `connection` |
| 20 | `RealtimeWebSocketSessionStore` | realtime-edge | `connection` |

---

## Key Design Decisions

**Publish after commit** — `PublishMessageEventStep` registers `TransactionSynchronization.afterCommit()`. The Redis/Kafka event only fires if the DB write succeeds. A failed transaction produces no phantom event.

**Idempotent send** — `clientMessageId` lets clients retry on network failure without creating duplicate messages.

**Multi-instance safe** — Redis pub/sub delivers to all `realtime-edge` instances. Each instance calls `findByChannelOwnedByCurrentInstance()` so only the instance that owns a client's session delivers to it. No duplicates, no cross-instance handoff needed.

**Dual entry points** — REST and WebSocket `SEND` both hit the same `MessageCommandController`. The WebSocket path is a thin proxy: `realtime-edge` adds the client's JWT to the forwarded HTTP request, so `chat-service` sees an authenticated request regardless of which path was used.
