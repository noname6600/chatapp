## Why

`GET /api/v1/users/me` can fail with `RedisConnectionFailureException` when Redis is unavailable, causing authenticated app bootstrap to return 500 even when profile data exists in the primary database. This endpoint is startup-critical, so cache outages should degrade performance, not availability.

## What Changes

- Add graceful fallback behavior for self-profile reads when Redis/cache operations fail.
- Ensure user-profile read flow treats cache as optional and repository as source of truth.
- Preserve existing not-found semantics (404 when profile truly does not exist).
- Add observability for cache failure fallback paths so incidents remain diagnosable.
- Add automated tests for cache-hit, cache-miss, cache-failure fallback, and missing-profile paths.

## Capabilities

### New Capabilities
- `user-profile-cache-resilience`: Defines resilient `/api/v1/users/me` behavior when cache/Redis is unavailable, including fallback, error semantics, and observability.

### Modified Capabilities
- None.

## Impact

- Affected backend area: `chatappBE/user-service` profile read service and related controller path (`/api/v1/users/me`).
- Runtime behavior: Redis outages no longer cause endpoint-wide 500 for valid profiles.
- Dependencies/systems: Spring Cache / Redis integration (`Lettuce`), user profile repository path, and service logging/metrics.
- Testing: requires service and API-level tests that simulate Redis/cache failures.
