## 1. Backend: Messages Around Endpoint

- [x] 1.1 Add `getMessagesAround(roomId, messageId, limit)` service method to `IMessageQueryService` and its impl, fetching N messages before and after the target messageId's seq.
- [x] 1.2 Add `GET /api/v1/messages/around?roomId=...&messageId=...&limit=...` endpoint in `MessageQueryController`.

## 2. Frontend API and Store

- [x] 2.1 Add `getMessagesAround(roomId, messageId, limit?)` function to `chat.service.ts` calling the new endpoint.
- [x] 2.2 Add `loadMessagesAround(roomId, messageId)` action to `ChatProvider` in `chat.store.tsx` that fetches context and replaces the room's message window.

## 3. Reply Snippet Layout and Styling

- [x] 3.1 Change `MessageItem` reply snippet from inline button inside text flow to a block `<div>` rendered above message content; remove all border/ring styles from the snippet.
- [x] 3.2 Add a clear "Replying to [name]" label (with ↩ icon or text) inside the reply snippet.
- [x] 3.3 Update `ReplyPreview.tsx` (composer preview) to match the no-border block layout and "Replying to [name]" label.

## 4. Highlight and Jump Logic

- [x] 4.1 Update `MessageList` linked highlight logic to only add the reply message ID to the highlight set (remove the original message ID from the set).
- [x] 4.2 Update `handleJumpToMessage` in `MessageList` to call `loadMessagesAround` when the target is not found in the current DOM, then scroll after the window replacement.

## 5. Tests and Validation

- [x] 5.1 Add/update tests for: block layout rendering, no-border snippet, "Replying to [name]" label, reply-only highlight, click-to-jump with context load, and missing-original fallback.
- [x] 5.2 Run frontend tests and fix any regressions. Verify TypeScript compile is clean.