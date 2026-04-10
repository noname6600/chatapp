## 1. Controller Safety Guard

- [x] 1.1 Update `AuthController.changePassword` to guard against null `JwtPrincipal` before dereferencing account ID
- [x] 1.2 Return deterministic unauthorized/auth failure response when principal is missing instead of allowing server error

## 2. Behavior Preservation

- [x] 2.1 Keep authenticated password change flow unchanged for valid principal and payload
- [x] 2.2 Ensure existing password service validation and error mapping remain intact after controller guard update

## 3. Test Coverage

- [x] 3.1 Add/update test: missing principal on password-change endpoint returns auth failure and does not throw NPE
- [x] 3.2 Add/update test: authenticated password-change request still succeeds and invokes service with principal account ID

## 4. Verification

- [x] 4.1 Run auth-service tests for password-change controller/service paths
- [x] 4.2 Manually verify endpoint behavior no longer returns 500 for unauthenticated/missing-principal request
