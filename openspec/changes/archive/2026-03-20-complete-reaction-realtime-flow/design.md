## Context

Reaction events exist in the backend and are delivered through websocket channels, but the frontend reaction state handling is incomplete for full realtime UX. Current behavior can miss immediate visual clarity for who reacted, and toggle behavior can become inconsistent under repeated clicks or concurrent updates.

The frontend currently derives message UI from centralized room/message stores and websocket event handlers. This change should leverage that architecture without introducing new backend contracts.

## Goals / Non-Goals

**Goals:**
- Ensure reaction add and remove operations propagate in realtime to all connected clients in the room
- Guarantee reaction toggling behavior for the same emoji is deterministic and idempotent from the user perspective
- Highlight reactions containing the current user, with reliable state during rapid updates
- Keep message reaction counts and reacted-by-me state consistent across message list and room views

**Non-Goals:**
- Introduce new reaction types beyond emoji
- Change backend event transport technology or room subscription semantics
- Add persistent per-user reaction history views
- Redesign message UI layout beyond reaction-related state presentation

## Decisions

**Decision 1: Treat reaction events as partial updates applied by messageId**
- Apply reaction updates by locating the target message in store and mutating only reaction fields.
- Rationale: avoids replacing full messages and prevents collateral field corruption.
- Alternative considered: remap full message snapshots from events; rejected because event payloads are partial.

**Decision 2: Define explicit toggle semantics in frontend state reducer**
- If user reacts with an emoji already reacted by them, remove their reaction entry; otherwise add it.
- Rationale: keeps UX intuitive and stable even if backend response ordering varies briefly.
- Alternative considered: rely only on server-computed lists; rejected due to increased UI latency and flicker.

**Decision 3: Derive self-highlight from reactedByMe or userId membership**
- Use canonical current user id and reaction membership to drive highlight state in ReactionGroup.
- Rationale: local derivation is robust and avoids extra API calls.
- Alternative considered: separate highlight flag from server; rejected as redundant.

**Decision 4: Keep optimistic updates minimal and reconcile with incoming events**
- Apply immediate visual toggle locally, then reconcile with websocket reaction updates.
- Rationale: responsive UX while maintaining authoritative eventual consistency.
- Alternative considered: no optimistic reaction update; rejected due to sluggish interaction feel.

## Risks / Trade-offs

[Risk] Out-of-order reaction events under poor network conditions → Mitigation: apply updates using messageId and deterministic toggle merge rules; reconcile with latest event timestamp/order when available.

[Risk] Duplicate updates from optimistic action plus server event → Mitigation: idempotent merge that prevents double increment/decrement for the same user-emoji pair.

[Risk] Missing current user id during initialization → Mitigation: defer self-highlight calculation until user profile is available and re-render when loaded.

[Risk] Group chat high-frequency reaction churn causing rerender cost → Mitigation: narrow updates to affected message objects and memoize reaction display components.

## Migration Plan

1. Implement reaction update merge logic and highlight derivation in frontend store/component.
2. Add unit and integration tests for toggle, cross-user updates, and rapid click scenarios.
3. Validate behavior in one-on-one and group chats across two active sessions.
4. Rollback by reverting reaction merge/highlight changes if regression appears.

## Open Questions

- Should self-highlight use a dedicated visual token (background chip) or a subtle border to align with current design language?
- Do we need debounce/throttle for extremely rapid reaction toggles from a single client?
- Is there any backend ordering metadata we can consume to further harden out-of-order event handling?
