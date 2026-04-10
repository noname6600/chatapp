## Context

The frontend notification websocket (`/ws/notifications`) currently retries every 3 seconds whenever the socket closes unexpectedly. In practice, some sessions enter a post-open close loop where the connection is accepted, then closed repeatedly, producing continuous reconnect noise in browser network logs and obscuring the true failure mode.

Current protection only suppresses repeated pre-open failures for the same connection signature. It does not suppress unstable post-open cycles, and it uses fixed retry timing instead of backoff. This can cause infinite retry spam under persistent backend or gateway instability.

## Goals / Non-Goals

**Goals:**
- Stop infinite fixed-interval reconnect spam for unstable notification websocket sessions.
- Preserve deterministic recovery for transient failures via retry with bounded exponential backoff.
- Add actionable lifecycle diagnostics (connect/open/error/close/retry/suppress/reset) for fast troubleshooting.
- Ensure retry suppression resets correctly when session context changes (token change) or connection stabilizes.

**Non-Goals:**
- Redesigning notification event payloads or backend notification domain behavior.
- Replacing websocket transport with polling.
- Changing unrelated chat, presence, or friendship websocket policies.

## Decisions

### 1. Unify failure tracking across pre-open and rapid post-open closes
- Decision: treat both pre-open closes and rapid post-open closes (within a stability window) as retry-failure signals for suppression.
- Rationale: the observed spam is caused by post-open close loops, not only handshake failures.
- Alternatives considered:
  - Keep pre-open-only suppression: rejected because it misses the dominant failure mode.
  - Suppress all post-open closes immediately: rejected because transient closes should still recover.

### 2. Replace fixed 3s retry with capped exponential backoff
- Decision: compute reconnect delay using exponential backoff with a max cap and optional jitter.
- Rationale: reduces retry pressure/noise while preserving automatic recovery.
- Alternatives considered:
  - Fixed delay forever: rejected as noisy and wasteful during outages.
  - No reconnect: rejected because transient network issues would require manual refresh.

### 3. Introduce session-signature scoped suppression with explicit reset rules
- Decision: suppression remains scoped to `endpoint + token` signature and is reset by successful stable open, token/signature change, or manual disconnect/login cycle.
- Rationale: prevents poisoning future valid sessions while avoiding repeated retries for the same broken session.
- Alternatives considered:
  - Global suppression: rejected because it can block legitimate reconnect after auth changes.

### 4. Add structured diagnostics for reconnect state machine
- Decision: log lifecycle transitions with key fields (close code/reason, connection lifetime, attempt number, chosen delay, suppression reason).
- Rationale: makes client-side diagnosis immediate and reproducible.
- Alternatives considered:
  - Keep minimal logs: rejected due to low debugging value.

## Risks / Trade-offs

- [Risk] Aggressive suppression may delay recovery if backend returns quickly. → Mitigation: reset suppression on token change and stable open; use capped backoff before suppression.
- [Risk] Additional logs can increase console noise. → Mitigation: keep logs structured and state-transition only, not per-message.
- [Risk] Overly short stability window may misclassify normal closes. → Mitigation: define and tune a clear stability threshold and cover with tests.

## Migration Plan

1. Update notification socket reconnect state machine with failure classification and capped backoff.
2. Add suppression logic for repeated unstable closes per connection signature.
3. Add lifecycle diagnostics and reset conditions.
4. Extend websocket unit tests for backoff/suppression/reset scenarios.
5. Validate in local runtime by forcing close loops and confirming retries stop after threshold.

Rollback strategy:
- Revert websocket state-machine changes in `notification.socket.ts` and related tests.
- Restore existing fixed-delay reconnect behavior if regressions appear.

## Open Questions

- Should jitter be enabled by default for local development logs, or only for production builds?
- What stability-window duration best separates transient close events from non-recoverable loops for this app?