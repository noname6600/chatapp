## Context

Current chat send UX inserts optimistic messages immediately, but intermittent transport/server failures can leave placeholders that look sent even when no durable server record exists. Users then refresh and discover missing messages. The system already supports `clientMessageId` idempotency, but local delivery-state transitions and refresh reconciliation are not strict enough to prevent false-success states.

Constraints:
- Keep existing REST/WS send APIs and `clientMessageId` model.
- Avoid duplicate sends while enabling explicit retry.
- Preserve responsive UX while guaranteeing eventual consistency with server history.

## Goals / Non-Goals

**Goals:**
- Make optimistic send state machine explicit: `pending -> sent | failed`.
- Require server-correlated confirmation (`clientMessageId`) before marking sent.
- Mark unresolved optimistic sends as failed after timeout and expose retry.
- Reconcile optimistic entries against latest history so non-persisted entries cannot appear as durable.
- Preserve idempotent behavior by reusing the same `clientMessageId` during retry.

**Non-Goals:**
- Redesign chat transport protocol.
- Introduce new message schema versions.
- Replace idempotency strategy or add distributed transactions.

## Decisions

1. Define a strict client delivery state machine
- Decision: represent local send lifecycle with `pending`, `sent`, `failed`.
- Rationale: prevents UI ambiguity and allows deterministic user feedback.
- Alternative considered: binary pending/complete only. Rejected because failures become invisible.

2. Confirmation source is server message correlated by `clientMessageId`
- Decision: optimistic entries transition to `sent` only when matching server-confirmed message appears via REST response/WS event/history refresh.
- Rationale: correlation key already exists and is idempotency-safe.
- Alternative considered: mark sent on HTTP 200 only. Rejected because race conditions and dual transport can still desync UI from persisted history.

3. Timeout-based failure transition for unresolved pending sends
- Decision: if no confirmation is observed within a bounded timeout window, transition to `failed` and show retry.
- Rationale: bounds uncertainty and surfaces recoverable action to user.
- Alternative considered: infinite pending. Rejected because it hides failure indefinitely.

4. Retry reuses original `clientMessageId`
- Decision: retry action resubmits payload with same `clientMessageId`.
- Rationale: preserves idempotent dedupe semantics and avoids duplicates when first write actually succeeded but ack path failed.
- Alternative considered: new ID per retry. Rejected due to duplicate risk.

5. History refresh reconciles stale optimistic entries
- Decision: after `latest` loads, unresolved optimistic entries older than timeout without server match are marked `failed` (or removed if superseded by matched server message).
- Rationale: refresh is authoritative for persistence visibility and closes the false-success gap.
- Alternative considered: never touch optimistic entries during refresh. Rejected due to current bug pattern.

## Risks / Trade-offs

- [Risk] Slow network causes premature `failed` state. -> Mitigation: choose conservative timeout and allow one-click retry.
- [Risk] Duplicate visual entries during race between WS and REST confirmations. -> Mitigation: single reconciliation path keyed by `clientMessageId`.
- [Risk] Legacy messages without `clientMessageId` are harder to reconcile. -> Mitigation: keep existing fallback matching logic for legacy payloads.
- [Risk] Added state complexity increases test surface. -> Mitigation: add focused store/component tests for each transition.

## Migration Plan

1. Introduce delivery-state field and transition helpers in FE message store.
2. Update send handlers (REST + WS) to set `pending` first, then resolve via correlated confirmation.
3. Add pending timeout scheduler and retry action.
4. Integrate reconciliation on latest-history refresh.
5. Roll out with test coverage; rollback by disabling timeout-to-failed transition and reverting to current optimistic behavior.

## Open Questions

- What timeout value gives best UX under current latency profile?
- Should failed optimistic messages remain visible indefinitely or auto-expire after a retention period?
- Should retry be automatic (with backoff) or strictly user-triggered for this phase?
