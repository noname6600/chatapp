## Context

Current reply jump flow is single-attempt: if target is in DOM, scroll; otherwise call around API once. This is fragile when the message window is far from target, and it does not clearly distinguish temporary unload from permanent unavailability (deleted or inaccessible target).

## Goals / Non-Goals

**Goals:**
- Make reply jump deterministic for unloaded targets.
- Avoid unbounded API loops when target cannot be found.
- Provide explicit terminal UI state when original target is unavailable.
- Preserve viewport stability during jump resolution and paging updates.

**Non-Goals:**
- Redesign room-open unread anchoring behavior.
- Add new backend endpoints unless existing contracts are proven insufficient.
- Rework message list virtualization architecture.

## Decisions

1. Multi-step reply jump resolver in FE
- Decision: replace one-shot jump with staged resolver:
  1) Find target in loaded map/DOM.
  2) Query around target (`getMessagesAround`).
  3) If still unresolved, run bounded `getMessagesBefore` backfill attempts through store paging.
  4) End in explicit unavailable/deleted state.
- Rationale: handles common unloaded-history paths while preventing infinite fetch behavior.
- Alternative considered: always fetch complete history until found. Rejected due to large API cost and poor UX on large rooms.

2. Bounded search budget and termination rules
- Decision: cap fallback attempts by both page count and sequence floor detection (`hasOlder=false`), then stop.
- Rationale: avoids runaway requests and keeps interaction predictable.
- Alternative considered: time-based cancel only. Rejected because request counts still spike under fast responses.

3. Explicit terminal fallback state for deleted/unavailable targets
- Decision: when search exhausts without target, set reply preview state to unavailable and keep UI interactive.
- Rationale: users need a clear explanation, especially when original was deleted above current window.
- Alternative considered: silent no-op. Rejected because it feels broken.

4. Scroll safety during resolver updates
- Decision: maintain scroll anchors while prepending/backfilling; only smooth-scroll after concrete target is in DOM.
- Rationale: prevents snap-to-top regressions during jump handling.

## Risks / Trade-offs

- [Risk] Extra API calls for difficult jumps. -> Mitigation: strict request budget and early stop on `hasOlder=false`.
- [Risk] Race with realtime incoming messages during resolver flow. -> Mitigation: resolve by message id map after each fetch step and keep dedupe in store merge path.
- [Risk] Different backend error semantics for deleted target. -> Mitigation: normalize not-found and empty-result outcomes to one terminal unavailable state.
