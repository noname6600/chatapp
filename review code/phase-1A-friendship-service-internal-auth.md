# Phase 1A - Friendship Service Internal Endpoint Auth

## Scope
- Service: `friendship-service` only
- Goal: protect `/api/v1/internal/**` with a service credential (header/token)
- Constraint: keep normal JWT resource-server behavior intact for non-internal APIs

## Changes Implemented

### 1) Added internal auth filter
- File: `chatappBE/friendship-service/src/main/java/com/example/friendship/configuration/InternalServiceAuthFilter.java`
- Behavior:
  - Applies only to `/api/v1/internal` and subpaths
  - Reads credential from configurable header (`internal.auth.header`)
  - Validates against configured expected token (`internal.auth.token`) with constant-time comparison
  - Fail-closed when expected token is unset
  - Response codes:
    - `401 Unauthorized` for missing credential or missing configured token
    - `403 Forbidden` for invalid credential
  - On success, sets internal authenticated principal in security context

### 2) Updated security chain
- File: `chatappBE/friendship-service/src/main/java/com/example/friendship/configuration/SecurityConfig.java`
- Changes:
  - Removed `/api/v1/internal/**` from `permitAll`
  - Added `.requestMatchers("/api/v1/internal/**").authenticated()`
  - Registered `InternalServiceAuthFilter` bean with env-backed config
  - Added filter into chain before `UsernamePasswordAuthenticationFilter`

### 3) Added runtime config keys
- File: `chatappBE/friendship-service/src/main/resources/application.yaml`
- Added:
  - `internal.auth.header: ${INTERNAL_AUTH_HEADER:X-Internal-Service-Token}`
  - `internal.auth.token: ${INTERNAL_AUTH_TOKEN:}`

## Risk Fixed
- Internal endpoints were previously exposed by `permitAll`, allowing unauthenticated access.
- Internal endpoint access now requires explicit service credential and is fail-closed when token is not configured.

## Public/Auth Behavior Kept Intact
- Existing OAuth2 JWT resource-server behavior remains unchanged for normal non-internal APIs.
- Publicly permitted routes (auth/docs/health/websocket as currently configured) remain unchanged except internal path hardening.

## Required Environment
- `INTERNAL_AUTH_TOKEN` must be set in friendship-service runtime environment.
- Optional override: `INTERNAL_AUTH_HEADER` (default: `X-Internal-Service-Token`).

## Regression Considerations
- Internal callers must send the configured header and token.
- Misconfigured or empty `INTERNAL_AUTH_TOKEN` now blocks internal traffic by design.

## Verification
Executed:
- `./gradlew.bat :friendship-service:compileJava :friendship-service:compileTestJava --no-daemon`

Result:
- `BUILD SUCCESSFUL`
