# Phase 1A - user-service internal endpoint auth hardening

## Scope
- Service: `user-service` only
- Goal: protect `/api/v1/users/internal/**` with service credential auth
- Constraints followed:
  - no `common-*` changes
  - no full security-stack redesign
  - public JWT/resource-server behavior kept for non-internal APIs

## Files changed
1. `chatappBE/user-service/src/main/java/com/example/user/configuration/InternalServiceAuthFilter.java`
2. `chatappBE/user-service/src/main/java/com/example/user/configuration/SecurityConfig.java`
3. `chatappBE/user-service/src/main/resources/application.yaml`

## Exact reason for change
- Before change, `/api/v1/users/internal/**` was effectively public at service level via `permitAll` in `SecurityConfig`.
- This violated the service boundary hardening requirement from Phase 1A and allowed internal APIs to be called without a service credential if the service port was reachable.

## Security risk fixed
- Risk fixed: unauthenticated access to internal user endpoints (boundary bypass risk).
- Example affected endpoint:
  - `GET /api/v1/users/internal/{accountId}/exists`
- New behavior:
  - missing credential -> `401 Unauthorized`
  - invalid credential -> `403 Forbidden`
  - valid credential -> request is authenticated as internal service principal and proceeds

## How the new internal auth works
1. `InternalServiceAuthFilter` runs for paths under `/api/v1/users/internal/**`.
2. It reads a configured header (default `X-Internal-Service-Token`).
3. It compares token value to configured expected token using constant-time comparison (`MessageDigest.isEqual`).
4. If valid, it sets an internal authenticated principal in `SecurityContext`.
5. `SecurityConfig` now requires `.authenticated()` for `/api/v1/users/internal/**`.
6. Public behavior remains unchanged for existing public paths (`/api/v1/auth/**`, docs, health, etc.), and normal APIs still use JWT resource server auth.

## Config / env required
Added config keys in `application.yaml`:
- `internal.auth.header: ${INTERNAL_AUTH_HEADER:X-Internal-Service-Token}`
- `internal.auth.token: ${INTERNAL_AUTH_TOKEN:}`

Required runtime env for internal calls:
- `INTERNAL_AUTH_TOKEN` must be set to a non-empty secret value.
- Caller must send header matching `INTERNAL_AUTH_HEADER` (or default `X-Internal-Service-Token`) with exact token value.

## Regression risks
1. Internal callers that previously used no credential will now receive 401/403 until they add the configured header token.
2. If `INTERNAL_AUTH_TOKEN` is not set in an environment, internal endpoints are intentionally unavailable (fail closed).
3. If an environment already injects JWT-only internal calls, those calls must now either include the internal credential header for `/api/v1/users/internal/**` or be moved to non-internal API paths.

## Verification gate run (service-only)
Command executed:
- `./gradlew.bat :user-service:compileJava :user-service:compileTestJava --no-daemon`

Result:
- `BUILD SUCCESSFUL`

## Suggested follow-up test commands
1. Build gate:
- `./gradlew.bat :user-service:compileJava :user-service:compileTestJava --no-daemon`

2. Optional focused runtime check (manual):
- No header to internal endpoint -> expect 401
- Wrong header token -> expect 403
- Correct header token -> expect 200 for existing internal endpoint usage
