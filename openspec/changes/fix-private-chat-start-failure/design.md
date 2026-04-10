## Context

Private chat initiation currently fails in user-facing flows where a user expects to start a direct conversation from friend/profile contexts. The system spans frontend initiation UI, gateway-routed REST calls, and backend direct-room create-or-open logic, so the failure can occur at multiple boundaries (request composition, validation, or response mapping). This change needs a single, deterministic contract so initiation either opens an existing private room or creates one and then navigates to it.

## Goals / Non-Goals

**Goals:**
- Define a deterministic create-or-open initiation path for private chats.
- Ensure initiation failures are surfaced as explicit, user-visible outcomes.
- Preserve gateway-centric REST routing and authentication behavior for initiation requests.
- Add regression coverage across backend and frontend boundaries.

**Non-Goals:**
- Redesigning the room list or chat layout UX beyond initiation/error feedback.
- Introducing a new transport mechanism (WebSocket-first room creation).
- Changing unrelated group chat creation behavior.

## Decisions

1. Use create-or-open semantics for private chat initiation.
Rationale: Users should not need to care whether a prior direct room exists; initiation always resolves to a room id.
Alternatives considered: split endpoints for "lookup" and "create" (rejected due to extra round-trip and race windows).

2. Enforce backend validation for target eligibility and return stable error categories.
Rationale: Validation at service boundary prevents inconsistent frontend behavior and avoids client-only rule drift.
Alternatives considered: frontend-only validation (rejected as bypassable and incomplete).

3. Keep frontend integration on existing gateway REST clients and map known initiation error responses to actionable UI feedback.
Rationale: Maintains existing architecture and improves debuggability without adding new infra.
Alternatives considered: generic toast-only fallback for all failures (rejected for poor diagnosability and UX).

4. Add contract-focused tests at backend service/API level and frontend initiation flow level.
Rationale: The bug manifests as a workflow regression, so tests must verify end-to-end initiation semantics, not only helper functions.
Alternatives considered: unit-only coverage (rejected as insufficient for route/response integration failures).

## Risks / Trade-offs

- Backend and frontend may diverge on error-code interpretation -> Mitigation: document exact failure categories in spec scenarios and assert them in tests.
- Existing data inconsistencies (duplicate private rooms) can create ambiguous routing -> Mitigation: define deterministic room selection precedence and add guard assertions.
- Additional validation may reject flows previously allowed by accident -> Mitigation: provide clear user-facing messaging and monitor failed initiation counts.

## Migration Plan

1. Implement backend create-or-open path hardening and validation outcomes.
2. Align frontend initiation call sites to consume the stable response contract.
3. Add regression tests for success and failure scenarios.
4. Deploy with standard service rollout; no data migration required.
5. Rollback by restoring previous service/frontend versions if severe regressions appear.

## Open Questions

- Should blocked/removed-friend relationships return a distinct error code vs a generic forbidden outcome?
- Should initiation retry behavior be automatic for transient gateway failures or explicitly user-triggered?
