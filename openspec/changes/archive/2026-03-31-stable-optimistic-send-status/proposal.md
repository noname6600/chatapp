## Why

When a user sends a message, the UI flickers and jumps because the optimistic placeholder is deleted and re-inserted as a new React node when the server confirms it. Additionally, the "Sending…" status text renders on its own line below the message bubble, making the message height expand and then shrink — compounding the visual noise. Both issues make the send experience feel unstable and unpolished.

## What Changes

- The "Sending…" / "Failed to send" status label is moved inline on the same line as the message timestamp footer, so the message bubble height stays constant before and after confirmation.
- Optimistic message reconciliation is changed from **delete-then-insert** to an **in-place key update**: when the server confirms a message the store mutates the existing entry's `messageId` from `"temp-…"` to the real server ID instead of removing and re-adding, so the React component is never unmounted during the transition.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `message-sending`: Reconciliation MUST update the optimistic entry in-place (no delete+insert); send status indicator MUST render on the same line as the message timestamp, not below the message body.

## Impact

- `chatappFE/src/store/chat.store.tsx` — `upsertMessage` reconciliation logic
- `chatappFE/src/components/chat/MessageItem.tsx` — delivery-status render location (moved into timestamp footer row)
