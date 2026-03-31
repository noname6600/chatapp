## Context

Unread state currently drifts between websocket updates, in-memory room store state, and refresh hydration. Users observe two failures: real-time room/message unread indicators stop updating, and refresh does not correctly recover unread truth. The frontend already receives room-level unreadCount from backend and can mark read through `/rooms/{roomId}/read`, but event ordering and merge logic can still leave stale UI state.

## Goals / Non-Goals

**Goals:**
- Restore deterministic unread synchronization across websocket events, room list state, and refresh reload.
- Ensure sender exclusion: outbound messages from current user do not increment unread for that same user.
- Recover unread boundary UI from backend state after refresh/reconnect.
- Prevent stale or double-applied unread changes caused by race conditions.

**Non-Goals:**
- Introduce new backend endpoints or change existing API contracts.
- Redesign room list UI or message item visual system beyond unread correctness.
- Add new persistence in frontend storage for unread (backend remains source of truth).

## Decisions

### 1. Backend as source of truth for recovery
Decision: Treat `/rooms/my` response values (`unreadCount`, last message metadata) as authoritative on refresh and periodic reload.

Rationale:
- Prevents client drift from long-lived websocket sessions.
- Existing API already computes unread from `lastSeq - lastReadSeq`.

Alternative considered:
- Fully client-derived unread from message arrays. Rejected because partial windows and pagination make it unreliable.

### 2. Event-time optimistic update with guarded merge
Decision: Keep websocket-driven optimistic increment/decrement but apply guards:
- Ignore unread increment when event sender is current user.
- Reconcile with server snapshot on load/reload to correct drift.

Rationale:
- Preserves real-time responsiveness.
- Converges to backend truth after reconnect/refresh.

Alternative considered:
- Disable optimistic updates and rely only on polling. Rejected due to poor UX latency.

### 3. Unread boundary derived from room unreadCount + loaded message window
Decision: Render boundary line and banner using backend unreadCount and currently loaded messages; if unread extends outside current page, show top banner/jump affordance.

Rationale:
- Works with paging and partial history.
- Keeps display aligned with backend unread semantics.

Alternative considered:
- Persist a client boundary sequence in local storage. Rejected as brittle during multi-device usage.

### 4. Mark-read trigger based on user interaction boundary crossing
Decision: Mark read when user meaningfully reaches unread region (scroll crossing/jump-to-latest), not merely by mounting room view.

Rationale:
- Matches intent of unread UX and prevents immediate banner disappearance.
- Reduces false read acknowledgements.

Alternative considered:
- Mark on room open. Rejected for this flow because it hides unread context too early.

## Risks / Trade-offs

[Risk] Out-of-order websocket events can temporarily show wrong unread counts.
→ Mitigation: periodic/server-triggered room reload reconciliation and monotonic merge rules.

[Risk] Boundary placement may be imprecise if unread range starts outside loaded page.
→ Mitigation: show banner/jump affordance and avoid fake exact divider when first unread is not in window.

[Risk] Scroll-triggered mark-read can fire multiple times.
→ Mitigation: one-shot guard per room session and idempotent backend endpoint.

## Migration Plan

1. Update room store websocket merge logic for sender exclusion and guarded unread updates.
2. Update message list boundary rendering and interaction-driven mark-read behavior.
3. Reconcile unread state on room reload/refresh from backend snapshot.
4. Run FE tests and build.
5. Execute manual smoke checks for real-time events and refresh recovery.

Rollback:
- Revert frontend unread merge/boundary logic commits; backend contracts unchanged.

## Open Questions

- Should unread boundary be hidden when unread start is outside loaded window, or shown as approximate marker?
- Should mark-read also trigger when user pauses at bottom for a threshold duration?
