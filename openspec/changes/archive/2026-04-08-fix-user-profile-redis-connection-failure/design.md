## Context

`GET /api/v1/users/me` currently depends on Redis-backed cache access in the user-profile service path. When Redis is down or unreachable, Spring Cache can throw `RedisConnectionFailureException`, which bubbles up through service and controller layers and returns 500.

This endpoint is called during authenticated bootstrap, so instability here blocks profile-dependent UI surfaces even when the primary profile record exists in the database.

## Goals / Non-Goals

**Goals:**
- Keep `/api/v1/users/me` available when Redis/cache is degraded.
- Preserve current contract for missing profiles (`404` / existing domain error mapping).
- Keep cache as a performance optimization, not a hard dependency.
- Make cache-failure fallback behavior observable and test-covered.

**Non-Goals:**
- Redesigning profile schema/DTO or response envelope.
- Introducing new cache infrastructure or replacing Redis.
- Changing gateway JWT/auth semantics for self-profile access.

## Decisions

### Decision 1: Guard cache interactions in profile read path
Introduce defensive wrappers around cache operations used by self-profile lookup (`cache get`, optional `cache put`) so runtime cache exceptions are caught and handled locally.

Rationale:
- Cache failures should not be treated as data-not-found or service-fatal.
- Localized handling avoids broad exception mapping side effects.

Alternatives considered:
- Global exception handler for Redis errors: rejected because it still returns failure and can mask endpoint-specific intent.
- Removing cache usage entirely: rejected due to avoidable performance regression.

### Decision 2: Repository read remains authoritative fallback
If cache lookup fails or misses, continue with repository lookup for the authenticated account ID and preserve existing not-found behavior.

Rationale:
- Repository is source of truth.
- Keeps API semantics backward-compatible while fixing availability.

Alternatives considered:
- Return empty/default profile on any failure: rejected as contract-breaking and correctness-risky.

### Decision 3: Add explicit observability for degraded mode
Emit warning logs (and metric hooks where available) when cache failures occur during profile read, with non-sensitive identifiers and operation label.

Rationale:
- Prevents silent degradation and improves incident response.

Alternatives considered:
- Silent fallback only: rejected because ongoing Redis problems become hard to detect.

### Decision 4: Add deterministic automated tests for fallback behavior
Add tests for:
- cache hit success,
- cache miss + repository success,
- cache exception + repository success,
- profile missing behavior unchanged.

Rationale:
- Ensures this incident pattern does not regress.

Alternatives considered:
- Manual verification only: rejected as insufficient for reliability guarantees.

## Risks / Trade-offs

- [Risk] Fallback can hide recurring Redis incidents. -> Mitigation: warning logs/metrics with clear cache-failure tags.
- [Risk] Additional try/catch paths may slightly increase code complexity. -> Mitigation: keep wrappers small and reuse helper methods.
- [Risk] If repository is also unavailable, endpoint still fails. -> Mitigation: this change is scoped to cache-resilience; broader datastore resilience remains separate work.

## Migration Plan

1. Implement guarded cache-read/write behavior in user-profile service.
2. Route `/users/me` profile fetch through fallback-safe flow.
3. Add warning-level observability for cache failures.
4. Add/update unit and integration tests.
5. Roll out as backward-compatible patch release.

Rollback:
- Revert service-level cache-guard changes; no schema migration or API contract migration required.

## Open Questions

- Should cache-failure metrics be required for release or logging-only for this patch?
- Should identical fallback guards be applied to other profile endpoints (`/users/{id}`) in the same change or follow-up?
