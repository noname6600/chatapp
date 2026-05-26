# Phase 1D - Presence Internal Ingress Protection

## Files changed
- chatappBE/presence-service/src/main/java/com/example/presence/configuration/InternalServiceIngressAuthFilter.java
- chatappBE/presence-service/src/main/java/com/example/presence/configuration/SecurityConfig.java
- chatappBE/presence-service/src/main/resources/application.yaml
- chatappBE/presence-service/src/test/java/com/example/presence/configuration/InternalServiceIngressAuthFilterTest.java

## Security risk fixed
- Edge command ingress under `/api/v1/presence/ws/**` was callable by any client with JWT if endpoint path was reachable.
- Added trusted internal credential gate so edge-only command endpoints are not publicly invokable by ordinary clients.

## How internal auth works
- New filter `InternalServiceIngressAuthFilter` is applied in the security chain.
- Filter scope: only `/api/v1/presence/ws` and subpaths.
- Validation:
  1. expected token must be configured (`internal.auth.token`), otherwise fail closed (`401`).
  2. request must include configured header (`internal.auth.header`), otherwise `401`.
  3. header token must match expected token via constant-time compare, otherwise `403`.
- On success, request proceeds to normal JWT resource-server authentication and controller logic.
- Filter intentionally does not overwrite SecurityContext authentication, so existing `@AuthenticationPrincipal Jwt` behavior remains intact.

## Required config/env
- `internal.auth.header` (default `X-Internal-Service-Token`)
  - env override: `INTERNAL_AUTH_HEADER`
- `internal.auth.token` (required in non-dev runtimes)
  - env: `INTERNAL_AUTH_TOKEN`

Added to `application.yaml`:
- `internal.auth.header: ${INTERNAL_AUTH_HEADER:X-Internal-Service-Token}`
- `internal.auth.token: ${INTERNAL_AUTH_TOKEN:}`

## Regression risks
- Edge -> presence calls to `/api/v1/presence/ws/**` must send internal header/token or will be rejected.
- Missing `INTERNAL_AUTH_TOKEN` in deployment will fail closed and block edge ingress.
- Token/header mismatch across services can cause intermittent 401/403 on presence edge command forwarding.

## Test commands to run
Executed:
- `./gradlew.bat :presence-service:compileJava :presence-service:compileTestJava --no-daemon`
- `./gradlew.bat :presence-service:test --tests "com.example.presence.configuration.InternalServiceIngressAuthFilterTest" --tests "com.example.presence.controller.PresenceEdgeCommandControllerIntegrationTest" --tests "com.example.presence.websocket.handler.PresenceWebSocketHandlerRoomAuthorizationTest" --no-daemon`

Result:
- `BUILD SUCCESSFUL`
