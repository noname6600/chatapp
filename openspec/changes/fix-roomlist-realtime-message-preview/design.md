## Context

Room list state is maintained in frontend stores and updated from both websocket events and snapshot API reloads. Current behavior updates unread counters and sorting in some paths, but last-message preview data for background rooms can remain stale until a full reload. This creates a user-visible mismatch: message list can be current while room list preview remains outdated.

The change is frontend-focused and must preserve existing backend contracts (`MESSAGE_SENT` event payload and room snapshot endpoints). It also must not regress muted-room unread behavior, sender exclusion rules, or room ordering guarantees.

## Goals / Non-Goals

**Goals:**
- Update room list preview fields (sender name, content summary, latest timestamp) immediately on incoming realtime messages.
- Keep room ordering and unread updates consistent in the same realtime transaction.
- Ensure snapshot reconciliation does not overwrite fresher websocket preview data with stale values.
- Eliminate the need for manual browser refresh to see latest room list message previews.

**Non-Goals:**
- Introducing new backend APIs or websocket event types.
- Redesigning room list UI layout/styling.
- Changing message persistence semantics or unread business rules.

## Decisions

### Decision 1: Treat realtime room preview update as part of the same event reducer path
- **Decision**: Update `latestMessageAt`, last-message preview text, and sender metadata in the same room-store websocket handler that currently updates unread/sort state.
- **Rationale**: Single-path updates prevent partial state transitions where unread updates but preview does not.
- **Alternative considered**: Trigger a follow-up room snapshot fetch after each message event. Rejected due to avoidable network churn and race windows.

### Decision 2: Keep room preview derivation deterministic from normalized message payload
- **Decision**: Derive preview content from normalized message blocks/content using existing formatting rules, then write into room list state.
- **Rationale**: Keeps room list preview consistent with message rendering semantics and avoids custom formatting drift.
- **Alternative considered**: Store raw event text directly. Rejected because mixed block messages (assets/invites) need normalized summaries.

### Decision 3: Preserve websocket-first freshness during reconciliation
- **Decision**: During snapshot reconciliation, only replace preview/timestamp when snapshot is newer than current room-store value.
- **Rationale**: Prevents stale API responses from rolling back just-applied realtime preview updates.
- **Alternative considered**: Always replace from snapshot. Rejected due to observed stale overwrite behavior.

### Decision 4: Verify behavior with targeted store/component tests
- **Decision**: Add or adjust tests covering background-room incoming events, preview refresh, and no-refresh visibility in room list.
- **Rationale**: This bug is state synchronization-centric and requires regression protection at store level.
- **Alternative considered**: Rely only on manual QA. Rejected because bug is timing-sensitive and may recur.

## Risks / Trade-offs

- **[Risk]** Preview derivation for structured blocks may diverge from timeline text if formatting helpers drift.  
  **Mitigation**: Reuse shared normalization helper for preview strings and add test cases for TEXT/ASSET/ROOM_INVITE payloads.

- **[Risk]** Overly strict timestamp guards could ignore valid snapshot corrections.  
  **Mitigation**: Compare sequence/timestamp consistently and allow replacement when snapshot is strictly newer.

- **[Risk]** Increased reducer complexity in room store may impact maintainability.  
  **Mitigation**: Keep preview-update logic isolated in helper functions and cover with focused unit tests.

## Migration Plan

1. Update room-store realtime event reducer to atomically apply unread, preview, and ordering updates.
2. Update reconciliation logic to avoid stale snapshot overwrites.
3. Add tests for background room incoming message preview updates and reconciliation correctness.
4. Validate in manual two-user scenario: user A sends while user B stays on another room; user B sees immediate room list preview/unread update without refresh.

Rollback: revert frontend room-store reducer/reconciliation changes; backend remains unchanged.

## Open Questions

- Should room list preview show sender prefix for all room types or keep type-specific formatting currently in place?
- If websocket and snapshot timestamps are equal but content differs, should client trust websocket payload or snapshot payload?
