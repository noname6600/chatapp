## Why

Users currently see a raw HTTP 401 experience during login failures instead of a clear invalid-credentials message, which creates confusion and support churn. There is also a first-time account creation inconsistency where authentication can succeed before the linked user profile is fully available, causing a brief login followed by forced sign-out.

## What Changes

- Standardize login failure behavior so invalid email/password attempts return a consistent, user-facing wrong-credentials message contract instead of an opaque 401-only experience.
- Enforce account bootstrap consistency so auth success is only finalized when both account and linked user profile are present.
- Add failure handling for partial account creation paths to avoid issuing a session for incomplete identities.
- Surface deterministic error codes/messages for incomplete-account states so the frontend can show actionable feedback rather than silently kicking users out.
- Add targeted tests and observability for login error mapping and initial account provisioning edge cases.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `email-password-auth-verification`: Refine login failure response requirements for invalid credentials and incomplete-account outcomes.
- `auth-session-token-lifecycle`: Require token/session issuance only when account-to-user linkage is complete.
- `fe-auth-error-passthrough`: Ensure user-visible login errors preserve backend invalid-credential semantics.
- `fe-auth-bootstrap-resilience`: Define bootstrap handling for incomplete-account failures without silent logout loops.

## Impact

- Affected backend services: auth-service, user-service, gateway-service (error propagation and bootstrap coordination).
- Affected frontend areas: login error rendering, auth bootstrap/session initialization, forced-logout handling.
- Affected APIs/contracts: login response error payloads and account-creation completion semantics.
- Testing impact: authentication integration tests, first-login bootstrap scenarios, and frontend auth flow regression coverage.
