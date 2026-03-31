## Context

The chat client currently loads a fixed initial history page and relies on user-generated upward scrolling inside the message container to request older messages. In edge cases where initial content height does not exceed container height, no scroll event is fired, so pagination cannot start. A second issue appears when users switch away from a room and return, where room-local guard state can block subsequent history loads. The change spans UI trigger logic and room-scoped pagination state handling.

## Goals / Non-Goals

**Goals:**
- Ensure history pagination can start even when the initial message batch does not create overflow.
- Preserve room-local pagination correctness when navigating between rooms.
- Keep scroll restoration stable after prepending older messages.
- Make pagination blockers observable for debugging and tests.

**Non-Goals:**
- Redesigning chat layout or replacing current message windowing strategy.
- Changing backend pagination APIs or message ordering contract.
- Introducing virtualized rendering in this change.

## Decisions

1. Add no-overflow auto-prefetch in the message list component.
- Rationale: Triggering additional fetches when container cannot scroll resolves the deadlock where scroll-based pagination cannot start.
- Alternative considered: Increasing initial page size globally. Rejected because it increases network and memory costs for all rooms, even when unnecessary.

2. Keep pagination guards room-aware and resilient to room transitions.
- Rationale: Room switching should not reuse stale guard outcomes that prevent further loads after returning to a room.
- Alternative considered: Reset all pagination refs on every route change. Rejected because it can cause redundant fetching and unnecessary state churn.

3. Retain prepend-based scroll restoration.
- Rationale: Existing offset-diff restoration is already aligned with the UX goal of preserving reading position while extending history upward.
- Alternative considered: Jump-to-anchor restoration by message id. Rejected for now due to added complexity and no immediate requirement.

4. Add explicit diagnostic events for blocked and terminal pagination paths.
- Rationale: Distinguishes UI trigger issues from store-level no-more-history behavior during regressions.
- Alternative considered: Silent guards with tests only. Rejected because runtime observability is needed during rollout and verification.

## Risks / Trade-offs

- [Auto-prefetch loop risk] Repeated no-overflow fetches could over-request history in sparse rooms. → Mitigation: add max-attempt and no-progress stop conditions.
- [Guard-state drift] Incorrect room-scoped guard updates can still block valid loads. → Mitigation: tie guard resets to room transitions and validate with room-switch regression tests.
- [Log noise] Added diagnostics can be verbose in development. → Mitigation: confine to targeted pagination events and remove or reduce after stabilization.

## Migration Plan

- Implement the UI auto-prefetch fallback and room-transition-safe guard handling.
- Add or update unit tests for no-overflow and room-switch return scenarios.
- Verify behavior in local environment by reproducing the non-overflow room case.
- Rollback strategy: disable auto-prefetch path and revert to prior scroll-only trigger behavior if instability appears.

## Open Questions

- Should pagination diagnostics remain enabled in non-development builds, or be gated behind environment checks?
- Should max auto-prefetch attempts be configurable per room type in a future change?
