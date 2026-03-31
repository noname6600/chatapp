## Context

Four distinct bugs exist in the message inline-edit experience after the recent edit/draft-review work:

1. **Edited-badge alignment**: Edited messages gain a `rounded bg-slate-50/60 px-1.5 py-1` wrapper around the content block. Non-edited messages have no such wrapper. This extra padding shifts the text baseline relative to the avatar and time gutter, making the row visually taller and misaligned vs. regular messages.

2. **Scroll jump after edit confirm**: `handleEditSubmit` in `MessageItem.tsx` calls `loadMessagesAround(roomId, messageId)` after a successful edit. `loadMessagesAround` is a bulk re-hydration that replaces the message window, triggering a re-render that resets the chat scroll container's scroll position.

3. **Action bar fully interactive during editing**: The `MessageActions` component now sets `opacity-100` while `isEditing`, making the toolbar always visible during editing. However, Reply, Emoji/Reaction, and Delete are still fully interactive while the user is typing an edit. This can cause conflicting flows (e.g., deleting a message that is mid-edit).

4. **Deleted messages reappear after refresh**: All backend history queries in `ChatMessageRepository` (`findLatestByRoom`, `findBeforeSeq`, `findRange`, `findLastMessages`) use native SQL or JPQL without a `deleted = false` filter. Soft-deleted rows are persisted correctly (via `MessageAggregate.delete()`), but are re-loaded into the client on hydration.

## Goals / Non-Goals

**Goals:**
- Edited messages render at the same line height as non-edited messages; the "edited" badge is inline with the text, not a structural wrapper.
- Scroll position does not change after submitting an inline edit.
- While a message is mid-edit, Reply, Emoji, and Delete actions are disabled (visually muted).
- All history API calls exclude soft-deleted messages so they never reappear after refresh.

**Non-Goals:**
- Not redesigning the action bar layout or adding new actions.
- Not changing how the "edited" badge looks — only removing the structural wrapper that causes alignment drift.
- Not changing WebSocket event handling for deletes (real-time delete already works).
- Not adding user-facing "message was deleted" tombstones (out of scope).

## Decisions

### Decision 1: Edited badge — inline span, no wrapper div

**Chosen**: Remove the `<div className="... bg-slate-50/60 ...">` wrapper around edited text content. Move the "edited" badge to be a plain `<span>` injected inline at the end of the text node (already the pattern in the non-wrapper case; apply consistently to the structured-blocks case too).

**Alternative considered**: Keep the wrapper but make its padding `p-0`. Rejected — the background color still visually distinguishes edited messages from non-edited ones in a layout-shifting way, and it adds unnecessary DOM elements.

### Decision 2: Suppress `loadMessagesAround` after inline edit

**Chosen**: Remove the `loadMessagesAround` call from `handleEditSubmit`. The optimistic update + server `upsertMessage` call together already keep the local state correct. A bulk refresh is not needed for a content-only change and is the direct cause of scroll resets.

**Alternative considered**: Use a scroll-anchored re-hydration (save scroll offset before, restore after). Rejected — over-engineered for a case where local state already has the correct data.

### Decision 3: Disable (not hide) conflicting actions during editing

**Chosen**: Pass `isEditing` into `MessageActions` and add `disabled={isEditing}` to `onReply`, `onEmojiSelect` (EmojiPicker prop), and `onDelete` buttons. The Edit button is already hidden during editing. Disabled buttons remain visible (greyed out) so the user knows actions exist but are temporarily locked.

**Alternative considered**: Hide all buttons while editing. Rejected — it causes layout shift, and hiding interactions entirely while something is in progress is a less conventional pattern.

### Decision 4: Add `AND deleted = false` to all history repository queries

**Chosen**: Update all four query methods in `ChatMessageRepository` to add a `deleted = false` / `m.deleted = false` predicate. This is the authoritative fix at the data layer — no client-side filtering needed because the server should never return deleted rows in history.

**Alternative considered**: Filter in `MessageQueryService`. Rejected — pushing the filter up to the service layer leaves the repository contract ambiguous and allows future callers to unintentionally include deleted rows.

## Risks / Trade-offs

- **Removing `loadMessagesAround`**: If an edit causes a server-side structural change (e.g., type normalisation from MIXED to TEXT), the local state used after `upsertMessage(...updated)` may differ from the refresh window. Risk is low because the `upsertMessage` call already merges the server response; removing the extra refresh only forgoes a second opportunity for correction. Mitigation: the `updated` response from `editMessageApi` is fully merged into local state.
- **Backend query change**: Adding `AND deleted = false` causes the `findLatestByRoom` subquery to skip deleted rows when counting `limit+1`. If many consecutive messages are deleted, a room may return fewer than `limit` messages. This is correct and preferable to returning deleted rows. Mitigation: none required — this is the intended behavior.

## Migration Plan

All changes are non-breaking:
1. Deploy backend chat-service with updated queries — deleted rows stop appearing in history responses.
2. Deploy frontend with edit badge fix, removed `loadMessagesAround`, and disabled action states.
3. No database migration required — `deleted` column already exists.
4. No rollback risk — reverting either deploy independently restores previous behaviour.
