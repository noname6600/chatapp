## Why

After integrating the API gateway, login/register flows are unstable: some successful authentications do not return refresh tokens, and follow-up calls are rejected with 401. This breaks first-session continuity and blocks normal onboarding.

## What Changes

- Standardize login and register success contracts so both return the expected token payload (including refresh token) for all successful authentication paths.
- Define gateway authentication behavior so public auth endpoints remain accessible while protected endpoints enforce token validation consistently.
- Establish token propagation and validation rules across gateway and backend services to prevent false 401 responses immediately after authentication.
- Add observability requirements for auth failures (missing refresh token, gateway denial reason, token validation mismatch) to speed diagnosis.

## Capabilities

### New Capabilities
- `auth-session-token-lifecycle`: Defines required login/register token response contract, refresh token issuance conditions, and immediate usability of issued tokens.
- `gateway-auth-route-enforcement`: Defines gateway route-level auth policy for public vs protected endpoints and required 401 diagnostics.

### Modified Capabilities
- None.

## Impact

- Affected systems: gateway-service, auth-service, user-service (if registration/auth orchestration is split), and frontend auth client integration.
- Affected interfaces: login/register API responses, gateway route security configuration, and downstream token validation behavior.
- Operational impact: improved troubleshooting via structured auth/gateway failure telemetry.
