## Context

**Current State:**
- Reactions are displayed in `MessageItem.tsx` from `chat.store.tsx`
- Optimistic updates are applied immediately, then real updates arrive via WebSocket
- When both optimistic and real reactions for the same (messageId, userId, emoji) exist, both are rendered, causing duplicates
- User reactions aren't visually distinguished from others
- Toggle logic in `useReaction.ts` doesn't properly reconcile state, causing unreliable add/remove behavior
- Reaction merge logic in `reactionState.ts` exists but doesn't handle deduplication

**Constraints:**
- Must maintain backward compatibility with WebSocket payload format
- Must work with existing backend reaction events
- Cannot break existing reaction features (counting, highlighting by emoji)

## Goals / Non-Goals

**Goals:**
- Eliminate duplicate reactions from the same user on the same message
- Make toggle behavior predictable and 100% reliable
- Visually highlight the current user's reactions for clarity
- Properly merge optimistic and real reactions without duplication

**Non-Goals:**
- Change WebSocket payload format
- Implement new reaction validation rules
- Add server-side deduplication (this is client-side state management)
- Modify reaction count display logic

## Decisions

### 1. Deduplicate Reactions in Store Layer
**Decision:** Add a `deduplicateReactions()` function to normalize reaction arrays by (messageId, userId, emoji) tuple. Keep only the *newest* (backend > optimistic) reaction per tuple.

**Rationale:** Deduplication at the store layer ensures all components receive clean data. This prevents duplicates from ever reaching the UI and is easier to test.

**Alternative Considered:** Deduplicate in the component layer. Rejected because components would need to re-implement the logic and the store would still contain invalid state.

### 2. Optimistic → Real Transition via Merge
**Decision:** When a real reaction event arrives via WebSocket, merge it using reaction state merge logic that replaces the optimistic copy with the real one (same messageId, userId, emoji = same reaction).

**Rationale:** Using message-level merge and message-level deduplication ensures optimistic updates immediately show to the user, then are cleanly swapped for the real update without duplication.

**Alternative Considered:** Use clientMessageId to track optimistic reactions. Rejected because reactions are keyed by messageId, not clientMessageId.

### 3. User Reaction Visual Highlight
**Decision:** In `MessageItem.tsx`, check if reaction.userId === currentUserId, and apply a `user-reaction` CSS class that adds background highlight, border, or accent color.

**Rationale:** Simple CSS-based solution, no component refactoring. User's own reactions instantly visually distinguish from others.

**Alternative Considered:** Add a badge/sticker icon for user reactions. Rejected as more visual complexity; highlight is cleaner.

### 4. Toggle Reliability via Optimistic + State Check
**Decision:** In `useReaction` hook, before sending toggle request, check if reaction exists in local state. If exists → delete. If not exists → add. Make toggle idempotent (retry-safe) by re-checking state after each operation.

**Rationale:** Optimistic updates combined with proper state reconciliation makes toggle immediately reflect intent. Idempotency prevents race conditions if user clicks repeatedly or network is slow.

**Alternative Considered:** Use server-side idempotency keys. Rejected because client-side dedup is simpler and sufficient.

## Risks / Trade-offs

**[Risk]** Race condition if user toggles reaction twice before first completes → **[Mitigation]** Disable toggle button during request, or debounce clicks.

**[Risk]** Optimistic update shows immediately even if toggle will fail on backend → **[Mitigation]** If toggle fails, revert optimistic state and show error toast.

**[Risk]** Deduplication could hide legitimate duplicate reactions (if backend allows) → **[Mitigation]** Backend enforces uniqueness constraint (uniq_user_message_emoji), so deduplication is correct by design.

**[Trade-off]** Highlighting user reactions uses CSS classes instead of semantic data attributes → **[Rationale]** Simpler implementation, no component changes needed, easy to theme.

## Migration Plan

1. **Deploy deduplication logic** to store layer (non-breaking)
2. **Deploy user reaction highlight** CSS (non-breaking)
3. **Move toggle logic** to use deduplication check (non-breaking, internal only)
4. **Test end-to-end** toggle add/remove and optimistic merge
5. **Monitor** for duplicate reactions in production (metrics, user reports)

**Rollback:** Each step is independent. Can revert changes module-by-module without affecting others.

## Open Questions

- What CSS variable should be used for user reaction highlight color? (Suggest: `--color-user-reaction-bg`)
- Should user reactions get a different emoji style (e.g., larger, bold) in addition to background?
- Should toggle button be disabled during request, or use optimistic feedback only?
