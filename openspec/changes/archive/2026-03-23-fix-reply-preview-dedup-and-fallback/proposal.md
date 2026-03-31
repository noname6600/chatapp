## Why

Reply relationships are still hard to follow in the message list. The current inline reply snippet sits in the same text flow as message content, making it visually ambiguous. There is no clear "Replying to [name]" label, the reply preview has a distracting border, the original message gets highlighted unnecessarily, and clicking a reply snippet fails silently when the original message is outside the loaded history window.

## What Changes

- Move reply snippet to its own line above message content (block layout, not inline in text flow).
- Remove the border/ring styling from the reply preview widget.
- Only highlight the reply message row (not the original message row), preserving clarity that the new message is the actor.
- Add a clear "Replying to [name]" label in the reply snippet so the relationship is immediately readable.
- When clicking a reply snippet and the original is not in the loaded message window, fetch messages around the original from the backend and scroll to it.
- Keep optimistic reply dedupe behavior stable so reply navigation states are not confused by duplicate temporary/server copies.

## Capabilities

### New Capabilities
- `message-reply-preview-resilience`: Reply relationship rendering and interaction behavior, including reply-only highlight, block-layout preview, clear sender label, click-to-jump navigation with context loading, and missing-original fallback.

### Modified Capabilities
- `message-sending`: Reply send flow must remain deduplicated and preserve stable `replyToMessageId` linkage for navigation.
- `message-grouping`: Message row/group rendering must support block reply preview layout without breaking grouped spacing.

## Impact

- Affected frontend message list rendering: `MessageList` and `MessageItem` reply row layout, highlight logic, and jump behavior.
- Affected frontend API layer: add `getMessagesAround(roomId, messageId)` call to `chat.service.ts`.
- Affected backend: add `GET /api/v1/messages/around?roomId=...&messageId=...&limit=...` endpoint to `MessageQueryController`.
- Affected store: add `loadMessagesAround(roomId, messageId)` action to `ChatProvider`.
- Affected tests: update/add regression tests for block layout, reply-only highlight, clear label, click-jump with context load, and missing-original fallback.