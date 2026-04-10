## 1. MessageList: Stable React key

- [x] 1.1 In `MessageList.tsx`, change `key={m.messageId}` on the message row wrapper `<div>` to `key={m.clientMessageId ?? m.messageId}`

## 2. Store: Revert in-place Map swap back to simple delete+insert

- [x] 2.1 In `chat.store.tsx` `upsertMessage`, revert the complex in-place array rebuild from the previous change back to the straightforward pattern: find existing optimistic entry by `clientMessageId`, `map.delete(optimistic.messageId)`, then `map.set(real.messageId, { ...msg, deliveryStatus: "sent" })`
- [x] 2.2 Remove the `reconciled` flag and the duplicate return-early block; restore the single unified `map.set` path

## 3. Verification

- [x] 3.1 Send a message and confirm the message row does not flash or disappear/reappear — only one DOM update occurs during the optimistic → confirmed transition
- [x] 3.2 Confirm messages from other users are still keyed by `messageId` and displayed correctly
- [x] 3.3 Retry a failed message and confirm no React duplicate-key warning appears in the console
