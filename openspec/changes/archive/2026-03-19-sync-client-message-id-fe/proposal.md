# Proposal: Sync clientMessageId to Frontend

## Why

The backend now persists and echoes a client-supplied `clientMessageId` on every sent message (see `add-client-message-id-flow`). The frontend's current optimistic reconciliation matches server-confirmed messages to optimistic ones by content — a fragile strategy that breaks on duplicate sends and produces wrong replacements. Now that the server is ready, the frontend should generate and send a `clientMessageId` and use it as the reliable reconciliation key.

## What Changes

- Add `clientMessageId` to the `ChatMessage` type and `SendMessagePayload` type
- Generate a stable UUID as `clientMessageId` when creating each optimistic message
- Include `clientMessageId` in both the HTTP `sendMessageApi()` call and the WebSocket `sendMessageSocket()` call
- Replace content-based optimistic reconciliation in the store with `clientMessageId`-based matching
- Update `MessageInput` to pass `clientMessageId` when sending via WebSocket
- Store the `chatApi` HTTP send path to pass `clientMessageId` as well

## Capabilities

### New Capabilities

- (none — this is pure frontend synchronization with an existing backend capability)

### Modified Capabilities

- `client-message-id-flow`: The frontend now satisfies the client-side obligations of this capability — generating `clientMessageId`, including it in sends, and using it for reconciliation. The existing requirements already mandate this behavior; no new requirements are added, but the FE-side reconciliation scenarios become testable.
- `live-message-grouping-parity`: The reconciliation strategy change directly improves the "Optimistic reconciliation without duplication" requirement by switching to ID-based matching, making it more reliable than content-based matching.

## Impact

- **chatappFE/src/types/message.ts**: add `clientMessageId` to `ChatMessage` and `SendMessagePayload`
- **chatappFE/src/api/chat.service.ts**: pass `clientMessageId` in `sendMessageApi()`
- **chatappFE/src/websocket/chat.socket.ts**: add `clientMessageId` parameter to `sendMessageSocket()`
- **chatappFE/src/store/chat.store.tsx**: generate `clientMessageId` per optimistic message; switch reconciliation to ID-based matching; fall back to content matching for legacy messages without `clientMessageId`
- **chatappFE/src/components/chat/MessageInput.tsx**: generate `clientMessageId` before sending; pass it to `sendMessageSocket()` and `upsertMessage()`
- **No breaking changes** — `clientMessageId` is optional throughout; existing messages without it continue to display and reconcile via legacy fallback
