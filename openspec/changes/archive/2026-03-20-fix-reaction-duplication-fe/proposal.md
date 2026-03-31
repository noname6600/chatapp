## Why

The reaction feature is showing duplicate reactions from the same user and displaying both optimistic and real reactions simultaneously, causing visual confusion and incorrect reaction counts. Additionally, the toggle behavior is unreliable—clicks to add or remove reactions don't work consistently. Users cannot easily distinguish their own reactions from others', and the feature feels broken due to the inconsistent state management between optimistic updates and real backend responses.

## What Changes

- **Deduplicate reactions**: Merge optimistic and real reactions by messageId and userId, removing duplicates from the same user
- **Fix toggle reliability**: Ensure add/remove reactions work consistently on every click with proper state reconciliation
- **Highlight user reactions**: Visually distinguish the current user's reactions (e.g., different background, accent, or badge)
- **Improve state consistency**: Ensure the frontend reaction state matches the backend after toggle

## Capabilities

### New Capabilities

- `reaction-deduplication`: Merge optimistic and backend reactions to eliminate duplicates on the same message. When a real reaction arrives, remove the optimistic placeholder and update with the server version.
- `user-reaction-highlight`: Apply visual styling (background highlight, badge, or accent color) to reactions added by the current user to make them instantly recognizable.

### Modified Capabilities

- `realtime-reaction-toggle-highlight`: Enhance the existing reaction toggle to be more reliable and properly reconcile optimistic state with backend responses; ensure toggle always works on click.

## Impact

**Frontend files affected:**
- `src/components/chat/MessageItem.tsx` (render reactions with dedup and highlight)
- `src/store/chat.store.tsx` (merge reaction events properly)
- `src/hooks/useReaction.ts` (toggle reliability)
- `src/utils/reactionState.ts` (deduplication and merge logic)

**Backend Integration:**
- Reaction payloads from WebSocket must include userId to identify user reactions
- Ensure toggle events are idempotent and consistent

**Breaking Changes:** None (internal state management only)
