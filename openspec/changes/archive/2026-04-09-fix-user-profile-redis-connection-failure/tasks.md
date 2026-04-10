## 1. Service Resilience Implementation

- [x] 1.1 Add guarded cache access helpers in user-profile service for read/write operations used by `/api/v1/users/me`.
- [x] 1.2 Update self-profile read flow to continue with repository lookup when cache read throws runtime Redis/cache exceptions.
- [x] 1.3 Preserve existing not-found error mapping when repository has no profile after cache miss/failure fallback.

## 2. Observability and Diagnostics

- [x] 2.1 Add warning-level structured logs for cache failure events in self-profile read path with operation label and account context.
- [x] 2.2 Ensure cache failure telemetry excludes sensitive profile payload fields.

## 3. Verification and Regression Coverage

- [x] 3.1 Add unit tests for cache hit and cache miss fallback behavior in user-profile service.
- [x] 3.2 Add tests for cache exception fallback-to-repository success path.
- [x] 3.3 Add tests confirming missing-profile path still returns existing not-found response semantics.
- [x] 3.4 Run user-service test suite and verify `/api/v1/users/me` behavior under simulated Redis unavailability.
