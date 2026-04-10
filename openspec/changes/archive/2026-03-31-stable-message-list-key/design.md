## Context

`MessageList.tsx` renders messages via:

```tsx
group.messages.map((m, indexInGroup) => (
  <div key={m.messageId} ...>
    <MessageItem message={m} ... />
  </div>
))
```

React uses the `key` to identify list items across renders. When the key changes, React unmounts the old node and creates a new one from scratch — regardless of array position. An optimistic message has `messageId: "temp-<uuid>"`. When the server confirms it, `upsertMessage` in the store runs reconciliation that replaces this entry with the real message (which has `messageId: "<real-uuid>"`). Both the previous delete+insert approach and the recent in-place Map swap suffer from the same root cause: the `key` prop presented to React changes, so React destroys and recreates the DOM element, producing a visible flash.

`clientMessageId` is assigned at send time and is identical on both the optimistic placeholder and the server-confirmed message. It never changes across the lifecycle of a sent message. Messages received from other users have `clientMessageId: null`.

## Goals / Non-Goals

**Goals:**
- Eliminate the unmount/remount flash when a sent message transitions from optimistic to confirmed, by using a stable `key` that survives the `messageId` swap.
- Simplify the store reconciliation back to the straightforward delete+insert pattern.

**Non-Goals:**
- Changing any other aspect of the send flow, timeout logic, or status display.
- Modifying backend or WebSocket event shapes.

## Decisions

### Decision 1: Use `clientMessageId ?? messageId` as the React list key

Change:
```tsx
<div key={m.messageId} ...>
```
To:
```tsx
<div key={m.clientMessageId ?? m.messageId} ...>
```

**Why this works:**
- Optimistic render: key = `clientMessageId` (e.g. `"abc-123"`)
- After server confirmation: the store replaces the entry, but the new entry still has `clientMessageId = "abc-123"` → key is still `"abc-123"` → React sees the same element, updates props, no unmount
- Messages from other users: `clientMessageId` is `null`/`undefined`, so `key` falls back to `messageId` (unchanged behavior)

**Alternative considered:** Assign a stable synthetic key per message at render time (e.g. via a `useRef` map). Rejected — more complex, requires mutable ref bookkeeping, and `clientMessageId` is already the exact stable identifier we need.

### Decision 2: Revert the in-place Map swap in `upsertMessage`

The complex in-place array rebuild added in the previous change (`stable-optimistic-send-status`) was only needed to try to keep the React node alive. With the key fix, the standard delete+insert pattern works correctly and is far simpler. Revert `upsertMessage` reconciliation to the original pattern.

## Risks / Trade-offs

- **Key collision**: Two different messages cannot have the same `clientMessageId`. This is already guaranteed by `crypto.randomUUID()` at send time. No risk.
- **`data-message-seq` attribute**: Still set on the wrapping div and based on `m.seq` — unaffected by this change.
- **Duplicate `clientMessageId` from retry**: Retry reuses the same `clientMessageId` by design (idempotency). If the retry produces a new optimistic entry with the same `clientMessageId`, and the old failed entry is already removed, there is no key collision.

## Migration Plan

1. In `MessageList.tsx`: change the single `key` prop.
2. In `chat.store.tsx` `upsertMessage`: revert the in-place swap back to delete+insert.
3. No data migration. No backend changes. Rollback = revert those two files.
