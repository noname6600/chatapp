## 1. Analysis & Setup

- [x] 1.1 Review current reaction store in `src/store/chat.store.tsx` and identify where duplicates can occur
- [x] 1.2 Review `src/utils/reactionState.ts` merge logic and existing deduplication attempts
- [x] 1.3 Review WebSocket event structure in `src/websocket/chat.socket.ts` to confirm userId is in reaction payload
- [x] 1.4 Review `src/hooks/useReaction.ts` to understand current toggle flow and race conditions
- [x] 1.5 Identify CSS framework in use (Tailwind/styled-components/etc) for highlight styling

## 2. Store Layer - Deduplication Utilities

- [x] 2.1 Create `deduplicateReactions(reactions: Reaction[])` function in `src/utils/reactionState.ts` that:
  - Takes array of reactions (may have duplicates)
  - Groups by (messageId, userId, emoji)
  - Keeps backend reaction over optimistic (based on reaction.id matching pattern or source flag)
  - Returns cleaned array
- [x] 2.2 Create test file `src/utils/reactionState.dedup.test.ts` covering:
  - Dedup single duplicate
  - Dedup multiple duplicates from same user
  - Prefer backend over optimistic
  - Empty/single reaction arrays (edge cases)
- [x] 2.3 Export deduplication function and document usage

## 3. Store Layer - Merge & Reconciliation

- [x] 3.1 In `src/store/chat.store.tsx`, update reaction event handlers to call `deduplicateReactions()` after merge:
  - For `REACTION_UPDATED` events
  - After merging reaction into message
  - Before storing back to messages array
- [x] 3.2 Test the dedup flow with a unit test combining merge + dedup
- [x] 3.3 Verify no side effects on other store operations

## 4. Component Layer - User Reaction Highlight

- [x] 4.1 In `src/components/chat/MessageItem.tsx`, get `currentUserId` from auth context or props
- [x] 4.2 For each reaction rendered, check if `reaction.userId === currentUserId`:
  - Add CSS class `user-reaction` to reaction element if true
  - Leave unstyled if false
- [x] 4.3 Create CSS classes for `user-reaction` highlight:
  - Add to `src/components/chat/MessageItem.css` or global styles
  - Define highlight color (e.g., background color, border, or opacity) that matches design
  - Ensure contrast is accessible (WCAG AA minimum)
- [x] 4.4 Test that highlight appears on user's own reaction and disappears on others'
- [x] 4.5 Test highlight persistence through optimistic and real updates

## 5. Hook/Logic - Toggle Reliability

- [x] 5.1 In `src/hooks/useReaction.ts`, refactor toggle function to:
  - Check local store before sending request: does reaction exist?
  - If exists → request DELETE
  - If not exists → request ADD
  - Make idempotent: re-check store state after response
- [x] 5.2 Add debounce or button disable during toggle request to prevent double-clicks
- [x] 5.3 Add error handling: if toggle fails, revert optimistic state and show error toast
- [x] 5.4 Add optimistic update: show reaction immediately before server responds (if not already present)
- [x] 5.5 Create test file `src/hooks/useReaction.test.ts` covering:
  - Toggle add when not exists
  - Toggle remove when exists
  - Rapid clicks don't create duplicates
  - Error handling reverts optimistic state
- [x] 5.6 Test integration with store deduplication

## 6. Integration - Message Load & Fetch

- [x] 6.1 In message fetch/load logic (likely in chat.store.tsx or a query hook), apply deduplication to messages loaded from API:
  - When fetching message history, run deduplication on reactions before storing
  - When loading message from cache, ensure deduplication
- [x] 6.2 Test that old messages with potential duplicates are cleaned on load

## 7. Testing - End-to-End Scenarios

- [x] 7.1 Manual test: Add reaction, reload page, verify no duplicates and highlight persists
- [x] 7.2 Manual test: Rapid toggle clicks (5+ times) - verify final state is correct and no duplicates
- [x] 7.3 Manual test: Add reaction optimistically, watch real event arrive, verify dedup and no double-render
- [x] 7.4 Manual test: Open same room in 2 tabs, add/remove reaction in one, verify other tab updates without duplicates
- [x] 7.5 Manual test: Network throttle (slow connection), toggle reaction, verify eventual consistency

## 8. Documentation & Polish

- [x] 8.1 Add JSDoc comments to `deduplicateReactions()` explaining parameters, return value, and dedup rules
- [x] 8.2 Add inline comments in store merge logic explaining why dedup happens there
- [x] 8.3 Document CSS class `user-reaction` in component or style guide
- [x] 8.4 Update any relevant README or contribution guides if reaction logic is documented there
- [x] 8.5 Review for console warnings/errors and clean up

## 9. Optional Enhancements (if time permits)

- [x] 9.1 Add visual feedback (animation or flash) when user adds/removes own reaction
- [x] 9.2 Add ability to customize highlight color via CSS variable (e.g., `--reaction-user-highlight-bg`)
- [x] 9.3 Add analytics event when toggle occurs to track reliability
- [x] 9.4 Consider moving dedup logic to store action to cache result for performance
