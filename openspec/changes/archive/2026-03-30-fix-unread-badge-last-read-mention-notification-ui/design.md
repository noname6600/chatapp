## Context

Unread and newest-jump indicators currently derive from mixed client counters and event timing, which can diverge from backend last-read state and produce invalid counts in the room header. Notification behavior also mixes generic message events with mention semantics, so mention-specific signals can appear for non-mentioned users. In parallel, notification overlay positioning can conflict with room list layout, causing visual overlap and interaction bugs.

## Goals / Non-Goals

**Goals:**
- Make unread badge, last-read anchor, and newest-jump values converge to a single deterministic state model.
- Ensure mention notifications are delivered only to explicitly mentioned users.
- Stabilize notification UI layering and container behavior so it does not overlap or hide behind room list regions.
- Keep realtime behavior responsive while preserving backend truth as final authority.

**Non-Goals:**
- Redesign the full chat layout visual system.
- Introduce a new notification transport protocol.
- Change unrelated reaction, attachment, or message composition behavior.

## Decisions

1. Canonical unread state uses backend lastReadSeq plus room message sequence bounds.
- Decision: Compute visible "messages behind latest" and unread badges from sequence math anchored by lastReadSeq, with bounded fallback on reconnect.
- Rationale: Eliminates counter drift from event ordering differences and refresh cycles.
- Alternative considered: Keep incremental client-only counters. Rejected because it is prone to drift during reconnect and pagination jumps.

2. Mention notification fanout is explicitly targeted.
- Decision: Mention notification events are emitted only for mentionedUserIds; non-mentioned participants receive normal message notifications only.
- Rationale: Prevents noisy mention alerts and aligns user expectation with @mention semantics.
- Alternative considered: Client-side filtering of broad mention events. Rejected because incorrect events would already be persisted and broadcast.

3. Notification UI renders in an isolated overlay layer with deterministic stacking.
- Decision: Notification panel and badges use a dedicated container and z-index contract above room list content but below global modal layer.
- Rationale: Resolves overlap and clipping issues without fragile per-component z-index tuning.
- Alternative considered: Patch individual component z-index values. Rejected because it is brittle and regresses with layout changes.

4. Realtime convergence remains mandatory after reconnect.
- Decision: After websocket reconnect, force a reconciliation fetch for room unread state and notification summary before trusting local counters.
- Rationale: Guarantees eventual consistency if events are missed in transit.
- Alternative considered: Trust buffered client state after reconnect. Rejected because missed events can still produce incorrect unread and bell counts.

## Risks / Trade-offs

- Risk: Additional reconciliation fetches increase API load after reconnect.
  - Mitigation: Debounce reconnect reconciliation and scope requests to active room plus lightweight summary endpoints.

- Risk: Sequence-based calculations may expose edge cases for deleted or redacted messages.
  - Mitigation: Clamp derived counts to non-negative values and reconcile against server-provided unread where available.

- Risk: Tight mention targeting may miss mentions if parser/token mapping is inconsistent.
  - Mitigation: Validate mention token extraction in backend pipeline and add contract tests on mentionedUserIds fanout.

- Risk: Overlay layer adjustments may affect existing notification interactions.
  - Mitigation: Add regression tests for click-through, focus trapping, and room-list interaction while panel is open.

## Migration Plan

1. Introduce deterministic unread derivation behind a feature flag in FE state layer.
2. Deploy backend mention targeting changes and validate event payloads in staging.
3. Enable notification overlay container and remove conflicting local z-index overrides.
4. Turn on reconnect reconciliation flow by default and monitor unread/bell mismatch metrics.
5. Rollback path: disable feature flag to revert to previous unread rendering while keeping non-breaking spec and test updates.

## Open Questions

- Should newest-jump show exact count or capped display (for example 9999+) for very large deltas?
- Should mention-only notifications have a distinct bell-badge segment or remain merged with general unread counts?
- Is Safari-specific stacking behavior requiring separate overlay fallback logic?
