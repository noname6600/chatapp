## Context

The chat frontend uses an optimistic-send pattern: when the user sends a message, a temporary placeholder (`messageId: "temp-<uuid>"`) is inserted immediately into the store, shown with `deliveryStatus: "pending"`. When the server confirms, `upsertMessage` finds the placeholder by `clientMessageId`, **deletes** it from the `Map`, then inserts a brand-new entry keyed by the real server `messageId`.

This delete-then-insert causes React to unmount the old `MessageItem` and mount a fresh one (different `key`), which produces a visible flicker — the message briefly disappears or jumps, especially when smooth-scroll or animations are active.

Separately, the "Sending…" / "Failed to send" labels are rendered in standalone `<div>` nodes placed after the message content block. This adds height to the message row while pending, then removes that height on confirmation, causing another layout shift.

## Goals / Non-Goals

**Goals:**
- Eliminate the unmount/remount cycle during optimistic reconciliation so the React node is never destroyed during the send-to-confirm transition.
- Keep the send-status indicator on the same horizontal line as the message timestamp so message height stays constant before, during, and after confirmation.

**Non-Goals:**
- Changing the timeout duration or retry logic.
- Redesigning the message header or timestamp formatting.
- Any backend changes.

## Decisions

### Decision 1: In-place Map key swap instead of delete+insert

**Current:** `map.delete(optimistic.messageId)` → `map.set(real.messageId, realMsg)`

**Chosen:** Rebuild the Map preserving entry order but replacing the optimistic entry's key with the real `messageId` and merging in the server fields.

```
new Map([...current].map((m) => {
  if (m.clientMessageId === incoming.clientMessageId && isTempId(m.messageId)) {
    return [incoming.messageId, { ...m, ...incoming, deliveryStatus: "sent" }];
  }
  return [m.messageId, m];
}))
```

React keys are derived from `messageId`. By emitting the real `messageId` in the same array position the optimistic entry occupied, React re-uses the existing DOM node (same index + stable key signals an update, not unmount). No flicker.

**Alternative considered:** Keep a stable `clientMessageId` as the React `key` for all messages. Rejected — it requires touching every render site, breaks pagination deduplication logic, and `clientMessageId` is null for server-delivered messages from other users.

### Decision 2: Move status indicator into the timestamp line

**Current:** Status label is a sibling `<div>` rendered below `MessageContent`, adding/removing height.

**Chosen:** Remove the standalone status divs. Render the send-status badge inline inside (or immediately after) the `MessageHeader` timestamp span, so it occupies the same row and adds no net height.

Concretely: `MessageHeader` already renders the `createdAt` timestamp. A small `deliveryStatus` prop is passed down and rendered as a sibling `<span>` within that same flex row — `text-amber-500 "Sending…"` or `text-red-500 "Failed"` — plus the retry/delete buttons grouped in the same line for the failed case.

**Alternative considered:** A CSS `::after` pseudo-element for the pending indicator. Rejected — too hard to conditionally control and incompatible with Tailwind's JIT approach.

## Risks / Trade-offs

- **Map key swap and seq ordering**: Map insertion order no longer matches visual sort order after a swap. The existing `Array.from(map.values()).sort((a,b) => a.seq - b.seq)` sort step still runs, so ordering is unaffected.
- **Timestamp line crowding (failed state)**: The "Failed to send" message + Retry + Delete buttons need horizontal space. At narrow widths the line may wrap. Mitigation: use `flex-wrap` on the header row, or keep the retry/delete buttons on a second line only in the failed case (still within the footer area, not below content).
- **MessageHeader prop surface growth**: Adding `deliveryStatus` to `MessageHeader` couples a pure display component to message state. Mitigation: keep the prop optional with a default of `undefined` (no render); header remains usable in isolation.

## Migration Plan

1. Update `upsertMessage` in `chat.store.tsx` — swap Map key in-place.
2. Add optional `deliveryStatus` prop to `MessageHeader` / or inline in `MessageItem` timestamp row.
3. Remove standalone `deliveryStatus` divs from `MessageItem`.
4. Move retry/delete button group into the timestamp footer row.
5. No feature flag needed — pure client-side UI change with no API impact.
6. Rollback: revert `chat.store.tsx` and `MessageItem.tsx`; no data migrations.

## Open Questions

- Should the "Failed to send" retry/delete buttons remain inline with the timestamp, or always drop to a wrapped second line? (Current proposal: inline, wrapping allowed at narrow widths.)
