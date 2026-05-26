# Detailed Fix Backlog From All Review Reports

Date: 2026-05-21
Source files:
- ARCHITECTURE_REVIEW.md
- CODE_QUALITY_REVIEW.md
- DATABASE_REVIEW.md
- DEVOPS_REVIEW.md
- FINAL_SYSTEM_REVIEW.md
- KAFKA_REVIEW.md
- PERFORMANCE_REVIEW.md
- REDIS_REVIEW.md
- SECURITY_REVIEW.md
- WEBSOCKET_REVIEW.md

## Why only a subset was fixed in the previous pass

The previous implementation pass intentionally focused on fix-now, low-blast-radius items that were:
- service-local or config-only,
- testable quickly,
- safe without broad common module refactor,
- critical blockers (compile/runtime/security) with immediate production value.

That is why high-risk, cross-service redesign items (durable dedupe architecture, async websocket pipeline overhaul, broad common-core standardization, infra platform programs) were not force-applied in one pass.

## Current status snapshot

### Already fixed in current change

1. Cloudinary secret fallback removal in chat-service config.
2. Gateway websocket rewrite to realtime handler contract.
3. Friendship compile blocker (missing TraceContext import).
4. Notification compose Redis key alignment to Spring Redis env names.
5. Notification DLQ partition routing correction.
6. Gateway issuer configuration alignment.
7. Container hardening (non-root user + JVM memory flags).
8. Per-service .dockerignore files.

### Verified existing (no new delta required)

1. Realtime session registry already uses TTL controls.
2. Realtime session cleanup already uses cursor SCAN approach.
3. Blocked-pair pipeline already fail-open (prevents false blocked-send).
4. Gateway rate limiting applies as default filter (including ws ingress path).

## Full detailed issue backlog

## A. Fix now (service-local, high ROI, low-to-medium risk)

1. SEC-001 (Critical)
Problem: WebSocket handshake stores bearer token in session attributes (memory exposure).
Fix:
- Update JwtHandshakeInterceptor to store only required claims (userId, scoped info), not raw token.
- Update RealtimeWebSocketHandler to use principal/claims, never token attribute.
- Add test to assert session attributes exclude token.
Targets:
- realtime-edge-service websocket interceptor + handler.
Validation:
- Unit test and heap/session attribute inspection.
Effort: 1 day.

2. DB-002 (High)
Problem: Room owner transfer can race under concurrent leave/remove operations.
Fix:
- Make owner transfer atomic with proper transaction isolation or explicit row lock.
- Add concurrent test for dual leave/remove race.
Targets:
- chat-service room service transaction logic.
Validation:
- Concurrency test, owner invariant checks.
Effort: 1.5 days.

3. GATEWAY-001 (High)
Problem: Gateway readiness can pass while realtime-edge is unavailable.
Fix:
- Include realtime-edge dependency in readiness required services.
- Add readiness test with realtime-edge down scenario.
Targets:
- gateway-service readiness config/tests.
Validation:
- readiness endpoint test.
Effort: 1 day.

4. ARCH-002 (High)
Problem: Realtime-edge cloud BOM version drift from platform baseline.
Fix:
- Align realtime-edge dependency BOM to platform version.
- Run dependency and compile verification.
Targets:
- realtime-edge-service build.gradle.
Validation:
- dependency tree + compile/tests.
Effort: 1 day.

5. KAFKA-005 (Medium)
Problem: Notification dedupe allows null/malformed event IDs.
Fix:
- Reject/quarantine invalid IDs before dedupe decision.
- Add tests for null and malformed IDs.
Targets:
- notification-service dedupe guard.
Validation:
- unit tests + invalid-ID metrics.
Effort: 0.5 day.

6. SEC-004 (Medium)
Problem: Upload prepare-token secret can be empty at runtime.
Fix:
- Add startup fail-fast validation for required secret in production profile.
Targets:
- upload-service config bootstrap.
Validation:
- startup failure test when secret absent.
Effort: 0.5 day.

7. INFRA-001 (Medium)
Problem: Redis keyspace notification setting inconsistent between compose profiles.
Fix:
- Align redis startup flags in local and main compose profiles.
Targets:
- compose profile files.
Validation:
- redis INFO check.
Effort: 0.25 day.

8. INFRA-003 (Medium)
Problem: Redis listener enablement not strictly enforced.
Fix:
- Add startup assertion + health indicator when listener disabled.
Targets:
- realtime-edge redis listener config.
Validation:
- health endpoint + startup behavior.
Effort: 0.5 day.

9. ARCH-003 (Medium)
Problem: Naming drift in docs (media-service vs upload-service).
Fix:
- Standardize naming across architecture/ops docs.
Targets:
- review and architecture docs.
Validation:
- repo-wide search for legacy naming.
Effort: 0.5 day.

## B. Next sprint (cross-service behavior/refactor, moderate risk)

1. REALTIME-001 (High)
Problem: Blocking HTTP calls in websocket frame path.
Fix:
- Add async command queue and bounded backpressure path.
- Move side effects out of websocket read loop.
Validation:
- p95/p99 latency, queue depth metrics.
Effort: 3 days.

2. REALTIME-002 (High)
Problem: Synchronized per-session send lock causes throughput collapse under slow clients.
Fix:
- Replace with per-session outbound queue + controlled flush workers.
Validation:
- slow-client chaos test, throughput benchmarks.
Effort: 3 days.

3. REDIS-001 (High)
Problem: Membership cache staleness window allows temporary unauthorized access.
Fix:
- Event-driven invalidation on membership changes.
- Shorter interim TTL and metrics.
Validation:
- remove-member timing test (<1s invalidation).
Effort: 2 days.

4. KAFKA-002 (High)
Problem: In-memory dedupe lost on restart/rebalance.
Fix:
- Durable dedupe store (Redis TTL keys or dedicated store).
Validation:
- restart replay integration test.
Effort: 3 days.

5. KAFKA-003 (High)
Problem: Fixed shallow retry policy for all consumers.
Fix:
- Exponential backoff with jitter and env-based tuning.
Validation:
- outage simulation and retry storm metrics.
Effort: 2 days.

6. DB-001 (High)
Problem: Auth service schema mutation via ddl-auto update.
Fix:
- Move to Flyway baseline + validate mode.
Validation:
- idempotent migration + startup validation.
Effort: 2 days.

7. PERF-001 (Medium)
Problem: O(n) dedupe cleanup overhead.
Fix:
- Scheduled or batched cleanup instead of per-event full scan.
Validation:
- CPU profile under high cardinality.
Effort: 1.5 days.

8. PERF-002 (Medium)
Problem: Route timeout profile too coarse.
Fix:
- Per-route timeout policy with SLO-aligned values.
Validation:
- route-level timeout chaos tests.
Effort: 1 day.

9. PERF-003 (Medium)
Problem: Auth/cache miss bursts can amplify external load.
Fix:
- selective prefetch, metrics, and resilience controls.
Validation:
- burst join load test.
Effort: 1.5 days.

10. KAFKA-004 (Medium)
Problem: Hardcoded topic/group literals in some consumers.
Fix:
- central constants + lint/audit.
Validation:
- static check and scan.
Effort: 0.75 day.

11. KAFKA-006 (Medium)
Problem: Earliest offset default creates replay risk in production.
Fix:
- env-specific policy (prod latest, bootstrap earliest).
Validation:
- group startup behavior tests.
Effort: 1 day.

12. SEC-002 (Medium)
Problem: No mid-session token expiry enforcement for websocket.
Fix:
- periodic validation + disconnect with clear reason.
Validation:
- expiry injection test.
Effort: 1.5 days.

13. SEC-003 (Medium)
Problem: Internal ingress auth pattern inconsistent.
Fix:
- standardize internal filter usage and endpoint protection.
Validation:
- service matrix security tests.
Effort: 2 days.

14. CODE-003 (Medium)
Problem: Delivery pipeline duplicated across realtime delivery services.
Fix:
- extract edge-local shared base delivery flow.
Validation:
- shared delivery behavior tests.
Effort: 1.5 days.

15. ARCH-004 (Medium)
Problem: Legacy phase docs coexist with current architecture and create ambiguity.
Fix:
- archive obsolete phase docs and publish current canonical architecture doc.
Validation:
- docs consistency review.
Effort: 1 day.

16. DEVOPS-001 (Low)
Problem: Gradle deprecations remain.
Fix:
- run warnings-all pass and remove deprecated usage.
Validation:
- warning-free build target.
Effort: 1.5 days.

17. KAFKA-007 (Low)
Problem: Auto topic creation enabled, typo risk.
Fix:
- disable auto-create and use explicit bootstrap topics.
Validation:
- typo topic creation rejection test.
Effort: 1 day.

18. REDIS-002 (Low)
Problem: Dedupe key naming not centralized.
Fix:
- define key namespace constants and migrate guards.
Validation:
- key naming audit.
Effort: 0.5 day.

19. WS-002 (Low)
Problem: Protocol ping/pong handling not explicit enough.
Fix:
- explicit pong handling + heartbeat metrics.
Validation:
- heartbeat protocol test.
Effort: 1 day.

20. CODE-004 (Low)
Problem: Migration TODO debt is scattered.
Fix:
- create migration ledger with owners and deadlines.
Validation:
- TODO linkage audit.
Effort: 0.5 day.

## Recommended execution order (minimal common-module touching)

Phase 1 (immediate): SEC-001, DB-002, GATEWAY-001, ARCH-002, KAFKA-005, SEC-004, INFRA-001, INFRA-003, ARCH-003.

Phase 2 (reliability): KAFKA-002, KAFKA-003, REALTIME-001, REALTIME-002, REDIS-001, DB-001, PERF-002.

Phase 3 (quality standardization): SEC-003, CODE-003, KAFKA-004, PERF-001, PERF-003, SEC-002.

Phase 4 (ops/docs hardening): KAFKA-006, KAFKA-007, ARCH-004, DEVOPS-001, REDIS-002, WS-002, CODE-004.

## Estimated total remaining effort

Approximately 35-40 engineering days after excluding already-fixed items.

## Notes

- This backlog intentionally keeps most work service-local first.
- Common module changes should be introduced only after Phase 1/2 stability, and only for standardization items where reuse benefit is clear.
