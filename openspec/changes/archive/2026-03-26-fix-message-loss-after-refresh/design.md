## Context

A reproducible cross-user flow shows message-visibility divergence between live websocket updates and refresh hydration: user A sends messages 1-5, user B joins and only sees 1-4, user B sends 5-10, and after refresh both clients can lose the latest persisted message. Existing optimistic-send and websocket paths are mostly correct during steady-state, but room activation, pagination window composition, and reconnect/gap recovery can still produce inconsistent latest windows.

## Goals / Non-Goals

**Goals:**
- Guarantee that persisted latest messages remain visible after room activation and browser refresh.
- Define deterministic reconciliation between websocket stream and latest-page hydration.
- Ensure active-room initialization is idempotent and does not create handler churn or stale closure races.
- Define gap/reconnect recovery behavior that restores missing persisted messages without duplicates.

**Non-Goals:**
- Re-architecting message transport protocol or replacing websocket delivery model.
- Changing message ordering semantics beyond existing per-room sequence guarantees.
- Introducing server-side fanout redesign unrelated to visibility correctness.

## Decisions

1. Stable active-room initialization contract
- Decision: Active-room setup must be idempotent and callback-stable under incoming message churn.
- Rationale: Re-registration or repeated init during high event volume can create visibility races.
- Alternative considered: Debounce room initialization in UI only. Rejected because it hides symptoms but does not establish deterministic store behavior.

2. Latest-window preservation on merge operations
- Decision: Any merge involving boundary/range hydration and latest hydration must be union-based and sequence-sorted, never replacement-based.
- Rationale: Replacement allows older boundary windows to evict newer persisted messages.
- Alternative considered: Fetch larger latest page only. Rejected because it is not sufficient for unread-anchor or around-boundary use-cases.

3. Gap and reconnect recovery as first-class behavior
- Decision: Detect sequence gaps for active room and trigger latest reconciliation; also reconcile on socket reopen.
- Rationale: Temporary disconnects or event loss must not rely on manual refresh to recover.
- Alternative considered: Trust websocket delivery alone. Rejected due to observed missing-last-message incidents.

4. Sender/receiver post-send visibility invariants
- Decision: After server-confirmed send, both sender and receivers must converge to the same persisted latest window after refresh.
- Rationale: Message durability trust depends on cross-user convergence, not only local optimistic success.
- Alternative considered: Sender-only confirmation guarantees. Rejected because user-visible inconsistency remains.

## Risks / Trade-offs

- [Risk] More reconciliation fetches during reconnect/gap events may increase API load -> Mitigation: trigger only for active room and only on detected gap/open events.
- [Risk] Additional merge logic could introduce duplicate entries -> Mitigation: deduplicate by stable message identifier and maintain monotonic sequence ordering.
- [Risk] Strict invariants may expose existing backend sequence defects faster -> Mitigation: retain backend sequence initialization safeguards and add regression tests for concurrent sends.

## Migration Plan

1. Land spec and task updates describing visibility invariants and reconciliation behavior.
2. Implement frontend store reconciliation hardening and stable handler lifecycle updates.
3. Validate backend sequence continuity in concurrent send scenarios.
4. Run end-to-end multi-user flow tests (send/join/send/refresh) and deploy.
5. Rollback strategy: revert change set if message duplication or severe load regression appears; keep previous stable send path.

## Open Questions

- Should active-room reconnect reconciliation fetch latest only, or fetch latest plus around-unread boundary when unread is non-zero?
- What threshold (if any) should trigger telemetry escalation for repeated sequence-gap recovery in the same room?
