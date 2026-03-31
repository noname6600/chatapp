## Context

The reaction toggle pipeline context currently includes roomId, even though reaction actions are fundamentally message-scoped and message records already contain roomId. This duplicates source-of-truth fields across request/context flow and introduces maintenance risk.

The backend reaction flow includes context construction, persistence toggle logic, and reaction event publishing. Some steps may currently read roomId directly from context. This change removes that direct dependency and ensures room resolution comes from persisted message state.

## Goals / Non-Goals

**Goals:**
- Remove roomId field from ToggleReactionContext and all dependent reaction-toggle step usages
- Ensure room association is derived from authoritative message data retrieved by messageId
- Keep toggle behavior and emitted reaction events functionally unchanged from client perspective
- Update tests to validate flow correctness without context roomId

**Non-Goals:**
- Redesign reaction API contract unless strictly required by existing controller signatures
- Change reaction event topics, transport layers, or frontend event schema
- Modify unrelated message send/edit/delete flows

## Decisions

**Decision 1: Derive roomId from persisted message entity**
- In steps that need room scope, fetch the message by messageId and use message.getRoomId().
- Rationale: removes redundant input while preserving exact room routing correctness.
- Alternative considered: keep roomId optional in context for backward compatibility; rejected due to continued ambiguity.

**Decision 2: Keep controller endpoint shape stable where possible**
- External API should remain compatible if clients do not need to change.
- Rationale: minimizes integration impact and reduces rollout risk.
- Alternative considered: breaking endpoint change to remove roomId input immediately; rejected unless currently required by API route.

**Decision 3: Refactor tests to assert source-of-truth behavior**
- Update unit tests to verify no step depends on context roomId and that events still use message-derived roomId.
- Rationale: prevents regression and documents intended architecture.

## Risks / Trade-offs

[Risk] Hidden dependency on context roomId in existing steps → Mitigation: run focused grep and update all callsites; add tests for event publishing route key.

[Risk] Additional repository lookups for room resolution → Mitigation: reuse already-loaded message from context when available; avoid duplicate fetches.

[Risk] Mismatch during partial refactor across modules → Mitigation: compile and test whole chat-service reaction pipeline before merge.

## Migration Plan

1. Remove roomId field from ToggleReactionContext and compile to identify impacted callsites.
2. Update reaction pipeline/controller/step usages to derive room from message data.
3. Update tests for context construction and publish steps.
4. Run chat-service test suite and frontend smoke tests for realtime reactions.
5. Roll back by restoring roomId field if unexpected production issues occur.

## Open Questions

- Does any external client currently send roomId specifically for reaction toggle (beyond messageId route)?
- Are there any performance-sensitive code paths where an extra message lookup would be problematic?
- Should room derivation be centralized in a single step to avoid repeated lookups?
