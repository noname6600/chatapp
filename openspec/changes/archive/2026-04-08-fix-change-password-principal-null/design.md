## Context

`AuthController.changePassword` currently dereferences `principal.getAccountId()` directly. In real requests where authentication is missing, expired, or not resolved into `JwtPrincipal`, `principal` is null and the endpoint throws `NullPointerException`, returning HTTP 500. This is a security and reliability issue because unauthorized calls should return a deterministic auth error response.

## Goals / Non-Goals

**Goals:**
- Prevent `NullPointerException` in password-change requests when principal is absent.
- Ensure password-change endpoint returns an authorization failure response instead of server error for unauthenticated requests.
- Preserve successful behavior for authenticated users and existing password policy/service checks.
- Add test coverage for missing-principal and authenticated flows.

**Non-Goals:**
- Changing password policy rules.
- Refactoring full authentication architecture.
- Changing unrelated auth endpoints.

## Decisions

- Add explicit null-principal guard in controller before calling service.
Rationale: this is the narrowest and safest fix for the reported crash path.
Alternative: rely solely on security filter chain. Rejected because runtime evidence shows null principal still reaches controller in some paths.

- Return deterministic business auth error on missing principal.
Rationale: keeps API behavior predictable and aligned with unauthorized semantics.
Alternative: let framework throw generic 401/403 without endpoint guard. Rejected due to inconsistency across entry paths.

- Add targeted controller/service tests for principal-null and success path.
Rationale: prevents regressions and validates that 500 is eliminated.
Alternative: manual-only validation. Rejected due to fragility.

## Risks / Trade-offs

- [Risk] Double-layer auth checks (security + controller) may be seen as redundant.
  → Mitigation: keep guard minimal and specific to null-principal dereference safety.

- [Risk] Error code/message mismatch with existing frontend handling.
  → Mitigation: reuse existing unauthorized business error conventions already used in auth-service.

## Migration Plan

1. Add null-principal handling in `AuthController.changePassword`.
2. Ensure the endpoint returns unauthorized business error instead of NPE.
3. Add/adjust tests for missing principal and normal authenticated password change.
4. Run auth-service tests and verify no regression for existing password change flow.

Rollback:
- Revert controller guard and related tests if unexpected integration behavior appears.

## Open Questions

- Should missing principal return 401 via framework exception mapping or current business exception wrapper format used by auth-service endpoints?
- Should this same null-principal guard be standardized across other endpoints using `@AuthenticationPrincipal`?
