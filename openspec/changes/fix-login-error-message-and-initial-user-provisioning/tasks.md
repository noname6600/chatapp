## 1. Backend Error Contract Alignment

- [x] 1.1 Define/standardize auth error codes and messages for `invalid_credentials` and `incomplete_account` in auth-service response models
- [x] 1.2 Update login handler to map credential failures to deterministic user-facing message payload while preserving secure semantics
- [x] 1.3 Ensure gateway propagation preserves backend auth error payload fields needed by frontend (`code`, `message`)

## 2. Account and User Provisioning Consistency

- [x] 2.1 Refactor register/first-login flow so success tokens are issued only after linked user profile readiness is confirmed
- [x] 2.2 Add idempotent guard/compensation for partial account-created user-missing states
- [x] 2.3 Return explicit incomplete-account error response when linkage cannot be completed in the auth path

## 3. Frontend Auth Error and Bootstrap Handling

- [x] 3.1 Update auth API error extraction so login/register views display backend `message` verbatim for auth failures
- [x] 3.2 Add frontend handling for incomplete-account code to perform controlled session reset and show actionable message
- [x] 3.3 Prevent repeated bootstrap reconnect/logout loops by limiting incomplete-account reset to one controlled cycle

## 4. Verification and Regression Coverage

- [x] 4.1 Add backend integration tests for invalid credentials message contract and incomplete-account token-blocking behavior
- [x] 4.2 Add frontend tests for wrong-credentials message rendering and incomplete-account bootstrap handling
- [x] 4.3 Add regression test ensuring first-time account provisioning cannot produce authenticated session with missing user profile

## 5. Observability and Rollout Safety

- [x] 5.1 Add structured logs/metrics for auth failure code distribution and incomplete-account occurrences
- [x] 5.2 Document rollout and rollback steps for auth/user provisioning changes in service runbooks
- [ ] 5.3 Validate staging behavior with end-to-end manual checks for wrong-credentials and first-account bootstrap flows
