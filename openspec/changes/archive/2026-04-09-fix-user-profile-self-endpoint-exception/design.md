/op## Context

`GET /api/v1/users/me` currently fails intermittently with a runtime exception surfacing from `UserProfileService.getSelf(...)` during authenticated profile bootstrap. The stack trace passes through Spring AOP and transaction interceptors before reaching controller return handling, which indicates the failure likely occurs inside service dependencies (cache/repository/mapping path) rather than controller routing.

Current `UserProfileService` behavior:
- Reads from `profileCache` first.
- Falls back to repository read only when cache returns `null`.
- Does not guard cache access exceptions, so cache layer failures can bubble up as 500.

This endpoint is high-impact because many authenticated surfaces call it at startup (settings/profile/sidebar identity). A single failure causes cascading UI instability.

## Goals / Non-Goals

**Goals:**
- Make `/api/v1/users/me` deterministic and resilient under cache degradation.
- Ensure cache-layer exceptions do not break valid self-profile reads.
- Preserve existing auth and domain semantics: return 404 only when profile truly missing.
- Add test coverage for success path, cache-failure fallback, and missing-profile behavior.

**Non-Goals:**
- Changing JWT principal extraction contract in gateway or user-service controller.
- Redesigning profile DTO fields or API response envelope.
- Migrating cache technology or introducing new infrastructure.

## Decisions

### Decision 1: Add defensive cache-read/write wrappers in self-profile path
Wrap cache interactions (`get`, `put`, `evict`) in small guarded helpers that catch runtime cache exceptions, emit warning logs, and continue with repository-backed behavior.

Rationale:
- Cache is performance optimization, not source of truth.
- Prevents transient Redis/serialization/cache adapter failures from causing endpoint outage.

Alternatives considered:
- Remove cache entirely from `/me` path: rejected due to unnecessary performance regression.
- Global exception handler mapping cache errors: rejected because it still returns error instead of successful fallback.

### Decision 2: Keep repository as authoritative fallback with unchanged not-found semantics
If cache read fails or misses, query `repo.findById(accountId)` and preserve current `RESOURCE_NOT_FOUND` behavior.

Rationale:
- Maintains existing contract for true missing-profile cases.
- Minimizes behavioral risk while fixing runtime instability.

Alternatives considered:
- Returning empty/default profile when missing: rejected because it hides data integrity issues and changes API semantics.

### Decision 3: Add focused observability for fallback execution
Log structured warning entries for cache failure events with accountId and operation (`cache_get_failed`, `cache_put_failed`, etc.) without leaking sensitive data.

Rationale:
- Supports diagnosis of infra/cache incidents while keeping endpoint available.

Alternatives considered:
- Silent fallback with no logs: rejected because root cause would remain opaque.

### Decision 4: Add targeted tests at service + controller boundary
Add tests covering:
- successful cache-backed read,
- cache-get exception with repository fallback success,
- profile missing returns mapped error,
- endpoint returns success for authenticated principal when fallback path used.

Rationale:
- Prevents regression of this incident pattern.

Alternatives considered:
- Manual-only verification: rejected as insufficient for recurring reliability issue.

## Risks / Trade-offs

- [Risk] Cache failures may be hidden by fallback and persist longer unnoticed. -> Mitigation: warning logs with explicit failure markers and account context.
- [Risk] Additional try/catch in hot path may add minor overhead. -> Mitigation: wrappers are lightweight and only affect failure path materially.
- [Risk] Broader hidden dependency issues (e.g., repository transaction errors) still produce 500. -> Mitigation: keep this change scoped to cache-induced instability and add explicit failure telemetry for next iteration.

## Migration Plan

1. Implement guarded cache access helpers in `UserProfileService`.
2. Route `getSelf/getOther` profile reads through fallback-safe logic.
3. Keep existing error code mapping for missing profile.
4. Add/adjust unit tests and controller-level tests for fallback behavior.
5. Deploy as backward-compatible patch release.

Rollback:
- Revert service fallback wrapper changes; no schema or API contract migrations required.

## Open Questions

- Should cache fallback warnings increment a dedicated metric counter (e.g., Micrometer) in this change or a follow-up hardening change?
- Should identical guarded-cache behavior be applied uniformly to other profile read/write endpoints in the same patch, or strictly to `/me`-critical path first?
