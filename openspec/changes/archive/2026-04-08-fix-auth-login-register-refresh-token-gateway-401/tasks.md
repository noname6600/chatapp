## 1. Auth Response Contract Stabilization

- [x] 1.1 Audit login and register success handlers in auth-service (and any delegated register flow) to locate refresh-token omission paths.
- [x] 1.2 Implement a shared auth success DTO/mapper so login and register return the same token payload fields.
- [x] 1.3 Add contract tests asserting successful login/register always include non-empty `accessToken` and `refreshToken`.
- [x] 1.4 Emit structured operational event when a success response would be produced without refresh token.

## 2. Gateway Route Policy Corrections

- [x] 2.1 Define explicit route policy classification (`PUBLIC_AUTH`, `PUBLIC_GENERAL`, `PROTECTED`) in gateway configuration.
- [x] 2.2 Ensure login, register, and refresh endpoints bypass JWT enforcement while preserving backend forwarding.
- [x] 2.3 Add gateway integration tests verifying auth bootstrap endpoints do not return 401 without Authorization header.
- [x] 2.4 Add deny-path tests verifying protected endpoints return 401 when token is missing or invalid.

## 3. Token Validation Alignment

- [x] 3.1 Compare gateway JWT validation settings with token issuer settings (signing key, issuer, audience, skew) and document mismatches.
- [x] 3.2 Implement config alignment and bounded clock-skew tolerance to prevent immediate post-login false 401.
- [x] 3.3 Add end-to-end test: login/register, then immediate protected endpoint call succeeds with issued access token.

## 4. Observability and Rollout

- [x] 4.1 Add structured 401 rejection reason codes in gateway logs/metrics (`missing_token`, `expired_token`, `invalid_signature`, `claim_mismatch`, `route_misclassified`).
- [x] 4.2 Create staging smoke script for login/register plus immediate protected-call validation through gateway.
- [x] 4.3 Define rollout checklist and rollback trigger thresholds based on 401 rate and reason-code distribution.
