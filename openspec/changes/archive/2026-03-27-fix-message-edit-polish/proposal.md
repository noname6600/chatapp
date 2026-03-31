## Why

The inline message edit experience has four rough edges that degrade UX: edited messages appear visually misaligned compared to non-edited messages, confirming an edit causes a jarring scroll jump, the action bar stays fully interactive while editing (allowing conflicting actions), and deleted messages reappear after a page refresh. These regressions were introduced or exposed during recent work on inline editing and must be fixed before the feature is stable.

## What Changes

- **Edited-badge alignment**: Remove the extra background/padding wrapper added to edited message content so the text baseline stays flush with neighbouring messages. Move the "edited" badge to be inline after the message text without shifting layout.
- **Scroll stability after edit confirm**: Suppress or defer the `loadMessagesAround` refresh so it does not cause the scroll container to jump after an edit is submitted. Server reconciliation should be scroll-position-aware.
- **Action bar disabled state during editing**: While a message is in inline-edit mode, all action buttons (Reply, Reaction/Emoji, Delete, and the Edit toggle itself) are disabled or hidden so the user cannot trigger conflicting flows.
- **Deleted message persistence after refresh**: Fix the root cause that allows a soft-deleted message to re-appear after page reload — either the delete API is not setting the flag correctly on the backend, the client hydration is not filtering out deleted messages, or both.

## Capabilities

### New Capabilities
- `inline-edit-interaction`: Inline edit UX rules — alignment of edited badge, scroll stability on confirm, and action-bar disabled state while editing is active.

### Modified Capabilities
- `message-refresh-consistency`: Add requirement that soft-deleted messages are excluded from all hydration windows and do not reappear after page reload.

## Impact

- **Frontend**: `MessageItem.tsx` (edit UI, action disabled states, edited badge rendering), chat scroll container (scroll lock during edit confirm), chat store hydration filter.
- **Backend**: `chat-service` — message query that backs room history must exclude `deleted = true` rows; verify `DeleteMessagePipelineStep` correctly persists the deleted flag.
- **No breaking API changes** — purely display/interaction and persistence correctness fixes.
