# Phase C Presence Final Readiness

## Readiness

Presence migration is ready for local and staging validation.

## Why

- The edge now has controller-level coverage for the new presence HTTP bridge surface.
- Presence edge calls are bounded by explicit timeouts and non-fatal failure handling.
- The connect snapshot race has a minimal convergence follow-up rule.
- The old websocket presence path still exists, so rollback remains available.

## Blockers

- No code blockers remain for local/staging validation.
- The only remaining limitations are deliberate tradeoffs, not release blockers.

## Next Coding Step

- Yes, the next coding step should be Chat migration.
- Presence Phase C should not expand further before Chat starts unless staging exposes a concrete bug.

## Rollback Assumptions

- Clients can still be routed back to the direct presence websocket path.
- The new presence HTTP bridge is additive, not destructive.
- Presence-service still owns the domain state and Redis fanout contract.
- If edge forwarding misbehaves, turning off the edge route should restore the previous behavior.

## Residual Risks

- Snapshot convergence is lightweight, not strongly consistent.
- Typing and heartbeat still rely on best-effort forwarding.
- Failure throttling lowers noise but does not eliminate all transient logging.
