## Context

The issue is scoped to frontend behavior, not backend generation rules. In the current FE flow, join input can be transformed before request submission, and room code display can be overwritten by shared or stale state when users navigate across rooms.

This design keeps API contracts unchanged and only improves frontend data handling and state ownership boundaries.

## Goals / Non-Goals

**Goals:**
- Preserve exact user-entered join code in outbound FE API call.
- Ensure displayed room code is scoped by roomId in FE state.
- Prevent stale async responses from writing code into the wrong room UI.
- Add deterministic FE tests for case preservation and cross-room isolation.

**Non-Goals:**
- Any backend service, repository, generator, or database schema changes.
- Changing API endpoint contracts for join-by-code or room-code retrieval.
- Modifying unrelated messaging or room membership features.

## Decisions

### 1. Input value is submitted exactly as typed
Join form state will preserve raw input string and submit it without case normalization transforms.

Alternative considered: normalize to one case before submit. Rejected because user explicitly requires exact code preservation in FE behavior.

### 2. Room code state keyed by roomId
FE state will store fetched room codes in a map keyed by roomId (or equivalent scoped structure), not in a single shared mutable value.

Alternative considered: single activeCode field in component/store. Rejected because fast room switching can leak one room's code into another room view.

### 3. Stale response protection by request ownership
Code-fetch requests are tagged with request ownership (active room snapshot or request token). Responses update state only when ownership still matches.

Alternative considered: always accept latest network response regardless of room context. Rejected because out-of-order responses cause cross-room contamination.

### 4. FE-only scope enforcement
No backend files are changed in this change set; fixes are limited to FE input handling, room code state, and FE tests.

Alternative considered: include backend guards in same change. Rejected to honor user request "only fixing it in FE and no touching BE".

## Risks / Trade-offs

- [Risk] Some components still normalize join code implicitly. → Mitigation: centralize submit transformation policy and add tests asserting raw value submission.
- [Risk] Legacy shared state paths still render active room code globally. → Mitigation: migrate to roomId-keyed selector and remove/shared-field references.
- [Risk] Out-of-order fetch responses in rapid room switching. → Mitigation: ignore stale responses using request ownership checks.

## Migration Plan

1. Update FE join flow to submit input as typed.
2. Refactor FE room-code state to roomId-scoped structure.
3. Add stale-response guard for room-code fetch.
4. Add FE tests for case preservation and room isolation.
5. Run FE build/tests and smoke-check room switching and join flow.

Rollback:
- Revert FE state and join submit handlers to previous implementation if regressions appear.
- No backend rollback is required because backend is unchanged.

## Open Questions

- Which FE module should own room-code state long-term (room store vs room details hook)?
- Should join input preserve leading/trailing whitespace exactly, or trim only whitespace while preserving case?
- Should we add telemetry for stale-response drops to monitor room-switch race frequency?
