## 1. Types

- [x] 1.1 Add `clientMessageId?: string | null` to the `ChatMessage` interface in `src/types/message.ts`
- [x] 1.2 Add `clientMessageId?: string` to the `SendMessagePayload` type in `src/types/message.ts`

## 2. WebSocket Helper

- [x] 2.1 Add optional `clientMessageId?: string` parameter to `sendMessageSocket()` in `src/websocket/chat.socket.ts`
- [x] 2.2 Include `clientMessageId` in the JSON payload sent by `sendMessageSocket()` when it is provided

## 3. API Service

- [x] 3.1 In `sendMessageApi()` in `src/api/chat.service.ts`, include `clientMessageId` in `cleanPayload` when present on the input payload

## 4. Store — Optimistic Message Generation

- [x] 4.1 In `store.sendMessage()` in `src/store/chat.store.tsx`, generate `crypto.randomUUID()` as `clientMessageId` before creating the optimistic message
- [x] 4.2 Set `clientMessageId` on the optimistic `ChatMessage` object created in `store.sendMessage()`
- [x] 4.3 Pass `clientMessageId` in the `sendMessageApi()` payload from `store.sendMessage()`

## 5. Store — Reconciliation Logic

- [x] 5.1 In the store's `upsertMessage()` / reconciliation logic, add a `clientMessageId`-first matching pass: find optimistic placeholder by `clientMessageId` when the incoming message has a non-null `clientMessageId`
- [x] 5.2 Keep the existing content-based `(senderId, content, replyToMessageId)` match as a fallback when no `clientMessageId` match is found

## 6. MessageInput — Primary Send Path

- [x] 6.1 In `MessageInput.send()` in `src/components/chat/MessageInput.tsx`, generate `crypto.randomUUID()` as `clientMessageId` before building the optimistic message
- [x] 6.2 Pass `clientMessageId` when calling `upsertMessage()` with the optimistic placeholder
- [x] 6.3 Pass `clientMessageId` to `sendMessageSocket()` call

## 7. Verification

- [x] 7.1 TypeScript compilation check: run `tsc --noEmit` in `chatappFE/` and verify zero errors
- [ ] 7.2 Manual test: send a message via WebSocket path — verify `clientMessageId` appears in the WS payload (browser DevTools Network → WS frames)
- [ ] 7.3 Manual test: resend the same message with the same `clientMessageId` — verify only one message appears in the UI (no duplicate)
- [ ] 7.4 Manual test: receive a message from history (no `clientMessageId`) — verify it still displays correctly (fallback path)
