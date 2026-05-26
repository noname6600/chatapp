# Phase 3 - Auth Decouple From User Readiness

Date: 2026-05-14
Execution source of truth: review code/service-fix-plan.md
Scope: auth-service only

## Objective
Remove synchronous user-profile readiness polling from auth token issuance/session flow so identity and token issuance remain available even when user-service readiness probes are slow or unavailable.

## Implemented Changes

1. Removed blocking readiness gate from token issuance path
- Updated auth session orchestration to always issue tokens first.
- Replaced blocking method with non-blocking issuance method:
  - AuthSessionService.issueTokens(UUID accountId)
- Auth flow no longer throws INCOMPLETE_ACCOUNT when user profile readiness cannot be confirmed.

2. Converted readiness logic to decoupled snapshot hint
- Updated readiness contract to a snapshot model:
  - IUserProfileReadinessService.getProfileReadinessSnapshot(UUID accountId): Optional<Boolean>
- Removed retry loop and sleep-based polling behavior from UserProfileReadinessService.
- Readiness data is now best-effort and non-blocking.

3. Added explicit user-service client abstraction
- Added new client class:
  - auth-service/src/main/java/com/example/auth/client/UserServiceClient.java
- Responsibilities:
  - Single probe to user-service internal exists endpoint
  - Fail-safe behavior: return Optional.empty() on probe failures
  - No token issuance impact on downstream failures

4. Exposed profile completion status safely in auth response
- Extended AuthResponse with optional field:
  - Boolean profileReady
- Maintained backward compatibility via existing 3-argument constructor.
- AuthSessionService enriches token response with profileReady when available; otherwise null.

5. Updated auth session call sites
- Replaced issueTokensWhenProfileReady(...) with issueTokens(...) in:
  - AuthService
  - BrowserOAuthService

6. Updated and added focused tests
- Updated AuthServiceTest to assert non-blocking issuance semantics.
- Added AuthSessionServiceTest:
  - issues tokens when readiness is unavailable
  - includes readiness hint when available
- Added UserProfileReadinessServiceTest:
  - returns empty when client cannot resolve status
  - returns value when client resolves status

## Verification Gate Run
Most relevant service-local gate for affected scope:
- Command: ./gradlew :auth-service:test
- Result: BUILD SUCCESSFUL

## Safety Notes
- Change is incremental and localized to auth-service.
- No auth architecture redesign.
- No user-service changes required.
- Token issuance/session ownership remains in auth-service; profile readiness is treated as decoupled auxiliary state.
