## Why

The `POST /api/v1/auth/password/change` endpoint currently throws a `NullPointerException` when `@AuthenticationPrincipal JwtPrincipal principal` is null, causing a 500 instead of a controlled auth error. This must be fixed now to prevent production crashes and to enforce consistent unauthorized behavior for password change requests.

## What Changes

- Add explicit principal-null handling for the password change endpoint so requests without an authenticated principal return a deterministic auth error (not server error).
- Ensure the endpoint enforces authentication guard behavior consistently before accessing `principal.getAccountId()`.
- Add/extend tests covering missing principal and normal authenticated password change flow.
- Preserve existing successful behavior for authenticated users and existing password policy validation.

## Capabilities

### New Capabilities
- `password-change-principal-guard`: Defines required behavior for password-change requests when authentication principal is missing or invalid.

### Modified Capabilities
- None.

## Impact

- Backend auth-service controller and possibly auth guard integration points.
- Backend test coverage for controller/service auth error paths.
- API behavior for unauthorized password-change requests (500 -> auth error response).
