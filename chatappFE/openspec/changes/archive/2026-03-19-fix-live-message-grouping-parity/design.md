## Context

The chat UI currently performs optimistic insertion for sent messages and later reconciles with server-confirmed messages from the WebSocket stream. Grouping is calculated in the message list based on sender/time and optimistic status. In practice, live rendering diverges from refreshed rendering because replacement timing and grouping guards are not fully deterministic across both flows.

## Goals / Non-Goals

**Goals:**
- Ensure live send flow and refresh flow produce identical grouping for the same message sequence.
- Keep duplicate-prevention during optimistic replacement.
- Define deterministic reconciliation rules that do not rely on transient client-only markers.
- Preserve current two-minute sender/time grouping behavior and attachment split behavior.

**Non-Goals:**
- No backend API or WebSocket protocol changes.
- No redesign of chat UI styles.
- No change to message ordering semantics from server sequence values.

## Decisions

- Decision: Use server sequence ordering as canonical ordering after reconciliation.
  - Rationale: Refresh already reflects canonical ordering from persisted data, so live must converge to this ordering immediately after replacement.
  - Alternative considered: Keep temporary client marker windows to force separation; rejected because it diverges from refresh behavior.

- Decision: Replace optimistic message in-place when matching server-confirmed message arrives, then sort once.
  - Rationale: Avoid duplicate entries while converging to canonical order.
  - Alternative considered: Delete temp and insert new; rejected due to extra state churn and potential flicker.

- Decision: Match optimistic and real message by senderId + content + replyToMessageId + roomId.
  - Rationale: Works with current payload shape and avoids protocol changes.
  - Alternative considered: Add clientMessageId to protocol; rejected for this change scope.

- Decision: Keep grouping logic stateless and based on message data only (optimistic detection by temp id/MAX seq; sender/time/attachment rules).
  - Rationale: Guarantees parity between live and refresh for same data.

## Risks / Trade-offs

- [Risk] Matching by sender/content/reply may collide for identical rapid sends.
  - Mitigation: Include roomId and prefer first unresolved optimistic entry; future enhancement can introduce protocol-level clientMessageId.

- [Risk] Sorting after replacement may visually move the message when server seq differs.
  - Mitigation: This is expected and aligns live order with refresh order.

- [Risk] Race conditions if multiple socket events arrive quickly.
  - Mitigation: Keep reconciliation inside a single state update path and avoid double-upsert after successful replace.

## Migration Plan

- Deploy frontend update with reconciler adjustments.
- No data migration required.
- Rollback by reverting frontend change if unexpected grouping regressions appear.

## Open Questions

- Should protocol add client-generated id echo for exact reconciliation in a later change?
- Should grouping threshold be configurable per room type in future?
