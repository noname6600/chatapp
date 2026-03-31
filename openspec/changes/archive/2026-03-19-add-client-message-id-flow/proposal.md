# Proposal: Add Client Message ID Flow

## Why

The `clientMessageId` field was added to the `ChatMessage` entity and indexed, but is not wired through the send pipeline. Without this, the frontend cannot perform optimistic UI reconciliation — it cannot match a locally-displayed (optimistic) message with the server-confirmed one, causing duplicate messages or flickering in the chat UI.

## What Changes

- Add `clientMessageId` to `SendMessageRequest` DTO (client provides it on send)
- Wire `clientMessageId` through the message creation pipeline (`CreateMessageAggregateStep`, `MessageAggregate`)
- Add `clientMessageId` to `MessageResponse` DTO (server echoes it back)
- Add `clientMessageId` to `WsIncomingMessage` WebSocket command model
- Add `clientMessageId` to `ChatMessagePayload` Kafka event payload
- Add `findByRoomIdAndClientMessageId` repository query for deduplication guard
- Add idempotency check in the send pipeline to prevent duplicate processing if the same `clientMessageId` is resubmitted

## Capabilities

### New Capabilities

- `client-message-id-flow`: Full end-to-end propagation of a client-generated message ID through HTTP REST and WebSocket send paths, including idempotency deduplication, echo-back in the response, and inclusion in the Kafka event payload.

### Modified Capabilities

- (none — no existing spec-level behavior changes to existing capabilities)

## Impact

- **chat-service**: `SendMessageRequest`, `MessageAggregate`, `CreateMessageAggregateStep`, `MessageResponse`, `MessageMapper`, `WsIncomingMessage`, `ChatWebSocketHandler`, `ChatMessageRepository`, `ChatMessagePayloadFactory`, `ChatMessagePayload`
- **common-events**: `ChatMessagePayload` or equivalent event payload class may need the field added
- **Frontend**: Can now send `clientMessageId` and reconcile optimistic messages without duplication
- **No breaking changes** — `clientMessageId` is optional on all inputs; existing flows without it continue to work
