# Realtime Edge Final Readiness Decision

## Decision

**Decision: OPTION 3 - Not ready for full local/staging validation. Perform a minimal hardening pass first, then validate.**

This is a strict gate decision based on code-level evidence, not design preference.

## Why This Decision

The migration is close, but three gate-relevant issues prevent credible full validation right now:

1. `chat-service` compile failure blocks full-domain buildability.
2. Friendship edge Kafka consumer topic contract does not match friendship producer topic publication behavior, breaking expected friendship realtime event flow to edge.
3. Friendship legacy tests currently fail compile and can block validation-task execution under normal Gradle test selection.

Proceeding directly to staging/local validation now would produce noisy or misleading results and weaken confidence in migration readiness.

## What Should Happen Next

Execute a tightly scoped hardening pass only (no redesign, no new migration phase):

1. Fix `chat-service` syntax error in `ChatWebSocketHandler` and confirm `:chat-service:compileJava` passes.
2. Fix friendship topic contract alignment between producer and edge consumer, then verify friendship event reaches edge delivery path.
3. Resolve or isolate failing legacy friendship test sources so selected validation test tasks can compile and run.
4. Re-run gate commands:
   - compile sweep with edge + all migrated domains
   - focused migration tests (edge/notification/presence/friendship)
   - optional full edge tests

If all above are green, switch to full local/staging validation immediately.

## Coding vs Validation Posture

- **Do not begin full validation yet.**
- **Do one minimal hardening pass first** to eliminate known hard blockers.
- After hardening, validation should become the primary activity, not more feature coding.

## Strict Blocker/Tradeoff Split

Blockers (must clear now):
- chat compile error
- friendship producer/consumer topic mismatch
- friendship compileTest debt (for strict test-gated entry)

Acceptable tradeoffs (can defer):
- best-effort realtime semantics (no exactly-once)
- retained legacy websocket endpoints for rollback safety
- transitional inactive/skeleton components not on active path
- notification router timeout/config style normalization

## Exit Criteria To Move Decision To "Ready"

All of the following must be true:
1. Multi-domain compile command including `chat-service` succeeds.
2. Friendship end-to-end event path into edge is verified with matched topic contract.
3. Validation-selected tests compile and execute without friendship legacy compile breaks.
4. No new cross-domain contract mismatches are introduced by fixes.

When those are satisfied, the decision can move from OPTION 3 to OPTION 1 (ready for full validation).
