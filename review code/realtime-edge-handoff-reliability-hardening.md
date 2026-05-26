# Realtime Edge Handoff Reliability Hardening

## Scope
This hardening pass improves infrastructure reliability for cross-instance websocket delivery in `realtime-edge-service` without starting Phase C domain migration.

## Implemented Changes

### 1) Session ownership freshness (lease model)
- Added lease freshness APIs on session registry contract:
  - `refreshSessionLease(sessionId)`
  - `evictStaleSessions()`
  - `cleanupOrphanIndexes()`
  - `getActiveLocalSessionCount()`
- Extended `RealtimeSession` metadata with `leaseExpiresAt`.
- Redis registry now:
  - persists `leaseExpiresAt` on register
  - refreshes lease on websocket activity and subscription updates
  - evicts stale sessions in bounded batches
  - performs orphan index cleanup for stale references

### 2) Deterministic maintenance scheduling
- Enabled scheduling in application bootstrap.
- Added scheduled maintenance job:
  - stale session eviction
  - orphan index cleanup
  - counters for cleanup activity
  - structured log line when work is done

### 3) Handoff publish reliability
- Added configurable publish retry/backoff:
  - `realtime.dispatch.handoff.publish.max-attempts`
  - `realtime.dispatch.handoff.publish.initial-backoff-ms`
  - `realtime.dispatch.handoff.publish.backoff-multiplier`
- Added publish success/failure counters.
- Added structured success/retry/fail logs.

### 4) Consume and delivery observability
- Handoff consumer now records consume success/failure counts by delivery type.
- Delivery services now record local websocket send success/failure counters by delivery type.
- Added ownership/session gauges:
  - `realtime.sessions.local.active`
  - `realtime.sessions.global.active`

### 5) Configuration hardening
Added explicit config keys in `application.yaml` for:
- session lease TTL
- cleanup enable/interval/batch size
- publish retry/backoff settings

## Validation
- `:realtime-edge-service:compileJava` -> BUILD SUCCESSFUL
- `:realtime-edge-service:test` -> BUILD SUCCESSFUL

## Operational Notes
- Cleanup is safe in in-memory mode (registry methods are no-op/default semantics).
- Retry/backoff is bounded and non-infinite.
- Cleanup metrics are incremented only when positive work is performed to avoid noise.

## Non-goals in this pass
- No Phase C domain migration started.
- No semantic changes to domain event contracts.
- No cross-service schema/version evolution introduced.

## Remaining Risks
- Lease freshness depends on app-level activity/update paths; unusual idle-but-valid session patterns should be monitored.
- Handoff is still at-least-once publish/listen style; consumer idempotency remains an application concern.
- Current counters are aggregate-focused; if cardinality-safe tracing is needed, use sampling and correlation ids in logs/traces.
