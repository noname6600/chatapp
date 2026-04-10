## Context

The notification client currently performs convergence sync (`GET /api/v1/notifications`) on websocket open/reopen and may enter high-frequency loops when websocket reconnect churn occurs. In unstable conditions this creates two coupled spam patterns: frequent reconnect attempts and frequent notification list fetches. The current flow lacks request coalescing and cooldown protection, so transport churn can amplify HTTP load.

## Goals / Non-Goals

**Goals:**
- Eliminate infinite fixed-interval websocket reconnect spam for notification channel instability.
- Bound and coalesce notification sync HTTP calls under reconnect churn.
- Preserve eventual consistency by still reconciling after recoverable reconnect.
- Add clear diagnostics for retry/fetch decision paths.

**Non-Goals:**
- Changing backend notification payload semantics or storage model.
- Replacing websocket realtime with polling-only architecture.
- Modifying unrelated websocket channels (chat, presence, friendship).

## Decisions

### 1. Reconnect with backoff + suppression for unstable loops
- Decision: use capped exponential backoff and session-signature suppression after repeated non-recoverable failures (pre-open or rapid post-open closes).
- Rationale: prevents endless 3-second loops while retaining recovery for transient failures.
- Alternatives considered:
  - Keep fixed delay retries: rejected because it causes sustained spam and backend pressure.
  - Disable retries entirely: rejected because transient outages would require manual reload.

### 2. Add in-flight dedupe and cooldown for `/notifications` sync fetch
- Decision: introduce a single-flight guard for sync requests and a minimum sync interval window for reconnect-driven fetches.
- Rationale: multiple open/reopen events should not produce parallel or near-duplicate GET calls.
- Alternatives considered:
  - Debounce only: rejected because it does not prevent concurrent in-flight duplicates.
  - Queue all fetch triggers: rejected as unnecessary complexity for this flow.

### 3. Prioritize fetch triggers by reason
- Decision: classify sync triggers (`initial_load`, `socket_reconnect`, `manual_action`, `post-mark-read`) and apply stricter rate limits only to reconnect triggers.
- Rationale: user-intent actions must remain responsive while reconnect churn must be bounded.
- Alternatives considered:
  - One global throttle for all fetches: rejected because it can delay explicit user actions.

### 4. Emit structured diagnostics for both reconnect and fetch decisions
- Decision: log reconnect attempt/backoff/suppression and sync fetch execute/skip/coalesce decisions with reason and elapsed time.
- Rationale: allows quick root-cause diagnosis for network spam reports.
- Alternatives considered:
  - Minimal logs: rejected due to poor observability during churn.

## Risks / Trade-offs

- [Risk] Excessive cooldown may delay notification freshness after recovery. → Mitigation: keep cooldown short and bypass for explicit user actions.
- [Risk] Suppression thresholds may over-classify transient instability. → Mitigation: reset on stable open and token/signature change.
- [Risk] Additional control flow could regress sync correctness. → Mitigation: targeted tests for dedupe, cooldown, and reset interactions.

## Migration Plan

1. Update notification websocket state machine with backoff and suppression.
2. Add sync single-flight + cooldown logic in notification store sync path.
3. Add trigger-reason routing for sync calls.
4. Extend unit tests for reconnect and fetch throttling interactions.
5. Validate with forced close-loop scenario and confirm bounded WS retries and bounded `/api/v1/notifications` calls.

Rollback strategy:
- Revert notification websocket retry-policy changes and notification sync guards.
- Restore prior behavior if functional regressions appear.

## Open Questions

- Should reconnect-trigger cooldown be static or adaptive based on recent failure density?
- Should diagnostics be gated behind a dev flag once behavior is stable?