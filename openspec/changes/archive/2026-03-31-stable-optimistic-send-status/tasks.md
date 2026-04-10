## 1. Store: In-place optimistic reconciliation

- [x] 1.1 In `chat.store.tsx` `upsertMessage`, replace the `map.delete(optimistic.messageId)` + `map.set(real.messageId, ...)` pattern with a Map rebuild that swaps the temp key to the real `messageId` in-place, preserving insertion order
- [x] 1.2 Verify the `deliveryStatus` is set to `"sent"` on the reconciled entry and all server fields (`seq`, `messageId`, `createdAt`, etc.) are merged in
- [x] 1.3 Confirm messages from other users (no matching `clientMessageId` in local store) are still inserted normally without side-effects

## 2. UI: Inline send-status in timestamp row

- [x] 2.1 In `MessageItem.tsx`, remove the standalone `<div className="mt-0.5 ...">Sending...</div>` block that renders below message content
- [x] 2.2 In `MessageItem.tsx`, remove the standalone `<div className="mt-0.5 ...">Failed to send ...</div>` block (with Retry/Delete buttons) that renders below message content
- [x] 2.3 Pass `deliveryStatus` to `MessageHeader` (or render status inline within `MessageItem`'s header row) so the "Sending…" / "Failed to send" label appears on the same horizontal line as the timestamp
- [x] 2.4 Keep Retry and Delete buttons on the same line as the failed indicator (using `flex` layout in the header/footer row); allow wrapping at narrow widths via `flex-wrap`

## 3. Verification

- [x] 3.1 Send a message and confirm no visible flicker or height change occurs as the optimistic placeholder transitions to confirmed
- [x] 3.2 Confirm "Sending…" text appears next to the timestamp, not below the message bubble
- [x] 3.3 Confirm "Failed to send" + Retry/Delete appear next to the timestamp after timeout
- [x] 3.4 Retry a failed message and confirm the same `clientMessageId` is reused and the message resolves correctly
- [x] 3.5 Send multiple messages in quick succession and confirm ordering and reconciliation are correct for all
