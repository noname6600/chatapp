## Rollout Checklist (Task 4.3)

### Pre-Deploy
- Confirm gateway config includes `/api/v1/auth/**` in route + permit list.
- Confirm FE auth requests target `/api/v1/auth/*` exactly once (no duplicate `/auth`).
- Confirm auth-service contract tests pass for non-empty `accessToken` and `refreshToken`.
- Confirm gateway security tests pass for public auth routes and protected route 401 behavior.

### Staging Validation
- Run smoke script: `chatappBE/scripts/auth-gateway-smoke.ps1`.
- Verify structured 401 reason logs are present when forcing invalid token.
- Verify login/register success payload always includes refresh token.

### Production Rollout
- Deploy gateway-service and auth-service first, then FE.
- Observe gateway 401 rate for first 30 minutes.
- Track 401 reason-code distribution by endpoint.

### Rollback Triggers
- Trigger rollback if 401 rate rises >30% above baseline for 10+ minutes.
- Trigger rollback if login/register success responses missing refresh token >0 occurrences.
- Trigger rollback if `route_misclassified` appears on auth bootstrap endpoints.

### Rollback Steps
- Revert gateway-service to previous stable image/config.
- Revert FE auth path changes if necessary.
- Re-run smoke script to verify baseline behavior restored.
