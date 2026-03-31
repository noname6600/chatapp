## Context

The frontend currently shows fresh notifications only after a full page reload in some sessions, which indicates websocket notification events are not being applied reliably to live state. The existing message realtime flow works, but notification state convergence depends too heavily on refresh-time fetches.

## Goals / Non-Goals

**Goals:**
- Ensure notification bell count and panel list update immediately when notification websocket events arrive.
- Ensure realtime notification handling survives transient socket disconnect/reconnect.
- Ensure room unread behavior and bell behavior remain consistent when realtime notification events are processed.
- Add deterministic automated coverage for realtime updates and reconnect continuity.

**Non-Goals:**
- Redesign notification UI visuals or information architecture.
- Introduce push notifications, email, or SMS channels.
- Change backend notification persistence model unless required to expose missing event fields.

## Decisions

1. Client-side event-path hardening in notification store
- Route all realtime notification updates through a single state transition path in the notification store to avoid divergence between websocket handlers and refresh handlers.
- Alternative considered: keep scattered per-component event updates. Rejected due to drift risk and duplicate logic.

2. Reconnection-safe subscription lifecycle
- Re-register notification event handlers on websocket reconnect and run a lightweight reconciliation fetch after reconnect success.
- Alternative considered: fetch-on-interval polling only. Rejected because it restores correctness but not realtime UX.

3. Explicit room/bell sync contract
- Define deterministic behavior for notification-event-driven unread updates so bell and room unread counters converge in the same interaction cycle where applicable.
- Alternative considered: treat room and bell independently with eventual consistency. Rejected because it reproduces stale UI windows and user confusion.

4. Focused regression tests for realtime and reconnect paths
- Add tests around websocket event ingestion and reconnect replay/reconciliation behavior in store-level tests.
- Alternative considered: rely only on existing broad suites. Rejected because current failure mode is path-specific and can regress silently.

## Risks / Trade-offs

- [Risk] Reconnect fetch may briefly override optimistic unread updates with stale backend values.
  - Mitigation: use monotonic merge rules and event timestamp ordering when reconciling.
- [Risk] Multiple websocket handler registrations can double-increment unread counts.
  - Mitigation: enforce idempotent listener registration and cleanup on unmount/reconnect.
- [Risk] Tight coupling between room unread and bell unread could over-constrain future behavior.
  - Mitigation: keep a clear sync contract limited to realtime event handling only.

## Migration Plan

1. Implement notification-store realtime subscription hardening and reconnect lifecycle handling.
2. Add/adjust tests for realtime event ingestion and reconnect reconciliation.
3. Validate locally with websocket events and full FE test suite.
4. Deploy frontend change.

Rollback:
- Revert frontend notification-store changes and test updates; backend APIs/events remain compatible.

## Open Questions

- Should reconnect reconciliation always call notifications fetch, or only when websocket uptime exceeded a threshold?
- Should room list unread update be driven directly from notification events, or remain message-event-driven with strict bell sync only?
