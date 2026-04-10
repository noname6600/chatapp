## Why

When a user sends a message, the chat list renders two UI updates instead of one:
1. The optimistic placeholder appears (correct)
2. When the server confirms, the entire message row unmounts and remounts

The remount causes a visible flash where the message disappears and reappears. The root cause is that the React list uses `key={m.messageId}`, and the `messageId` changes from `"temp-<uuid>"` (optimistic) to the real server-assigned UUID on confirmation — React treats this as a different element and destroys + recreates the DOM node. Users want the message to appear once and stay, updating only its internal state (status from "Sending…" to confirmed).

## What Changes

- The `key` prop on the message row wrapper in `MessageList.tsx` is changed from `m.messageId` to `m.clientMessageId ?? m.messageId`, so the React node survives the `messageId` swap during optimistic reconciliation.
- The previous in-place Map-swap workaround in `chat.store.tsx` (`upsertMessage`) can be simplified back to the original delete-then-insert pattern, since with a stable key the component is no longer unmounted on reconciliation.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `message-sending`: The message list row MUST use a key that is stable across the optimistic → confirmed transition so no unmount/remount occurs during send confirmation.

## Impact

- `chatappFE/src/components/chat/MessageList.tsx` — change `key={m.messageId}` to `key={m.clientMessageId ?? m.messageId}`
- `chatappFE/src/store/chat.store.tsx` — revert the complex in-place Map swap back to simple delete+insert (previous reconciliation logic), since the stable key now prevents React from unmounting
