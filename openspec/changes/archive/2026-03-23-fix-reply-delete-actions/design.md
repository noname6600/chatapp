## Context

Reply and delete actions are initiated from `MessageItem`, but their effects span multiple layers: list rendering state, input reply preview state, and message lifecycle mutations. Recent boundary refactors reduced prop surfaces, which is beneficial, but action wiring regressed. The fix must restore end-to-end interaction behavior without reintroducing list-wide prop leakage into item components.

## Goals / Non-Goals

**Goals:**
- Restore reliable reply action flow from item action click to reply preview and send metadata.
- Restore reliable delete action flow from item action click to confirmation and message removal.
- Preserve list-vs-item ownership boundaries while repairing action integration.
- Add targeted regression coverage for reply/delete item interactions.

**Non-Goals:**
- Redesigning message UI layout or action affordance visuals.
- Changing backend contracts for message send or delete APIs.
- Refactoring unrelated chat store modules.

## Decisions

1. Keep action triggers in `MessageItem`, but route side effects through dedicated hooks/stores.
- Rationale: item owns local interactions; shared effects belong in centralized state/hooks.
- Alternative considered: move all action handlers to list layer. Rejected because it would over-couple list rendering to item button logic.

2. Keep reply target resolution and list-wide derivations in `MessageList`.
- Rationale: reply lookup requires list context and should not reintroduce `allMessages` dependency into item.
- Alternative considered: item-side lookup by full list. Rejected for boundary and performance reasons.

3. Use explicit delete callback contract from list to item for confirmation dialog orchestration.
- Rationale: item should trigger deletion intent, while list/dialog layer controls modal lifecycle.
- Alternative considered: item-owned modal instance per message. Rejected due to duplication and state complexity.

4. Add regression tests for room switch + action availability to prevent boundary refactor regressions.
- Rationale: current failures surfaced during integration between boundaries and behavior.
- Alternative considered: rely only on manual verification. Rejected due to repeated regressions.

## Risks / Trade-offs

- [Action wiring drift] Multiple stores/hooks can diverge from expected action flow. → Mitigation: contract tests for reply and delete triggers.
- [Boundary erosion] Quick fixes may reintroduce broad prop passing. → Mitigation: enforce item-scoped prop contract in tests and review.
- [Modal state coupling] Delete confirmation orchestration can become brittle. → Mitigation: keep a single source of truth for delete target state.

## Migration Plan

- Patch reply/delete wiring in `MessageItem` and list/dialog integration points.
- Ensure reply state fields exist and are exposed in chat context contract.
- Verify send flow consumes reply state and clears it after successful send/cancel.
- Add/update tests for reply/delete actions and run full frontend validation.

## Open Questions

- Should delete confirmation be controlled by `MessageList` state or a dedicated delete-dialog store long term?
- Should reply metadata assertions be added to integration tests around outgoing payload construction?
