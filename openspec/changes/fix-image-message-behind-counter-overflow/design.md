## Context

The sender-side chat UI currently inserts optimistic outgoing messages with `seq: Number.MAX_SAFE_INTEGER` so pending messages stay visually at the tail before server confirmation arrives. That temporary value leaks into `latestSeqByRoom` metadata inside the chat store, and `MessageList` uses that metadata to calculate `distanceToLatest` for the top unread/newest indicator. After a self-sent image or attachment message is reconciled with its real server sequence, the stored `latestSeq` can remain pinned to the synthetic max value, which yields overflow-like text such as `9007199254740982+ messages behind latest` even though only one message was sent.

The receiver-side unread behavior is already correct: other participants should still get a real unread increment for the newly sent message. The fix therefore needs to preserve optimistic send UX and recipient unread updates while separating sender-only temporary state from authoritative unread/navigation math.

## Goals / Non-Goals

**Goals:**
- Keep optimistic text/image sends visible immediately for the sender.
- Prevent pending optimistic placeholders from becoming authoritative `latestSeq` state.
- Ensure sender-side behind-latest and unread indicators stay bounded, non-negative, and semantically correct after self-sent attachment messages.
- Preserve existing receiver unread increment behavior for real server-confirmed messages.
- Add regression coverage around optimistic attachment sends and top-indicator rendering.

**Non-Goals:**
- Changing backend unread-count semantics or message sequencing contracts.
- Redesigning the unread banner UX or jump-to-latest interaction.
- Reworking unrelated pagination, reply navigation, or attachment upload flows.

## Decisions

### Decision: Treat server-confirmed sequences as the only authoritative source for `latestSeq`
`latestSeqByRoom` should be updated only from server-confirmed messages and message-window fetches, not from optimistic placeholders. This removes the root cause instead of trying to mask large numbers later in the UI.

Alternative considered: clamp the rendered `distanceToLatest` in `MessageList` only. Rejected because the poisoned `latestSeq` would still distort other metadata such as `hasNewer`, unread boundary decisions, and future reconciliation.

### Decision: Allow optimistic placeholders to render at the tail without persisting synthetic maxima into window metadata
The sender still needs immediate local feedback for pending sends, including image sends. The placeholder can keep a client-only ordering strategy for the rendered list, but window meta used by unread/jump indicators must derive from confirmed sequence values.

Alternative considered: stop assigning any temporary tail-ordering sequence at all. Rejected because that risks unstable optimistic placement and would complicate pending-message ordering when multiple local sends are queued.

### Decision: Keep a defensive UI bound on behind-latest display
Even after the store fix, the unread indicator should continue to guard against invalid or non-finite sequence gaps so user-visible labels never show overflow-like values.

Alternative considered: rely entirely on store invariants. Rejected because the indicator is the final presentation boundary and should fail safely if metadata is ever malformed again.

### Decision: Add regression tests at the store and indicator layers
The bug spans both metadata derivation and UI label rendering. Tests should cover optimistic self-sent attachment flow in the chat store and the `MessageList` label state after reconciliation.

Alternative considered: test only the store. Rejected because the user-facing regression is the incorrect banner text, and UI-level protection is part of the contract.

## Risks / Trade-offs

- [Risk] Optimistic ordering changes could alter how pending messages appear relative to confirmed messages. → Mitigation: preserve tail rendering behavior while decoupling that ordering from authoritative unread metadata.
- [Risk] Recomputing latest/newest metadata from confirmed messages could miss edge cases during reconnect or retry flows. → Mitigation: cover optimistic send, retry, and confirmation reconciliation cases with regression tests.
- [Risk] UI clamping could hide a deeper state regression if used alone. → Mitigation: treat store-level metadata correction as primary fix and UI bounding as secondary defense only.

## Migration Plan

No data migration is required. Deploy as a frontend-only change. If a regression appears, rollback is a standard frontend revert of the metadata and indicator logic.

## Open Questions

- None at proposal time; the likely implementation path is to stop optimistic placeholders from advancing authoritative latest-sequence metadata and to keep a defensive bound in the indicator renderer.
