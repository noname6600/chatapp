## Context

Current authentication behavior has two user-impacting inconsistencies:
1. Invalid login attempts are surfaced primarily as HTTP 401 without a stable, user-facing wrong-credentials contract.
2. First-time account creation can complete account identity creation before linked user profile provisioning, allowing temporary session issuance and then forced logout during frontend bootstrap.

This change spans auth-service, user-service integration, gateway error propagation, and frontend auth/bootstrap handling. Existing capabilities already define auth error passthrough and bootstrap resilience, but they do not explicitly require atomic account+user readiness before session finalization.

## Goals / Non-Goals

**Goals:**
- Guarantee invalid credential attempts return a deterministic message contract suitable for direct UI display.
- Prevent successful login/register session issuance when account-to-user linkage is incomplete.
- Ensure frontend bootstrap handles incomplete-account responses as controlled session failures without logout loops.
- Improve observability for incomplete-account and invalid-credential outcomes.

**Non-Goals:**
- Redesigning the full authentication domain model.
- Introducing new identity providers or MFA.
- Reworking unrelated websocket reconnect strategies beyond auth-state handling requirements.

## Decisions

1. Normalize auth failure contract for invalid credentials.
- Decision: Auth-service returns a consistent structured error payload (`code`, `message`) for invalid credentials, with `message` suitable for direct UI display.
- Rationale: Frontend already has an error passthrough capability and should not infer intent from status code alone.
- Alternative considered: Frontend-side mapping of 401 to a generic string. Rejected because it loses backend context and diverges across clients.

2. Gate token issuance on complete identity provisioning.
- Decision: Login/register success responses are emitted only after both account identity and linked user profile are confirmed present.
- Rationale: Session tokens are a contract for usable authenticated state; issuing them before user profile readiness causes bootstrap failures.
- Alternative considered: Issue token immediately and let bootstrap retry user creation lazily. Rejected due to race conditions and poor UX.

3. Treat partial provisioning as recoverable domain error, not success.
- Decision: If account exists but linked user is missing or creation failed, return explicit incomplete-account error contract and avoid token issuance.
- Rationale: Clear separation between authentication failure and provisioning inconsistency enables deterministic frontend handling and safer retries.
- Alternative considered: Silent server-side retries with delayed success. Rejected because retry windows still leak inconsistent states and increase complexity.

4. Frontend bootstrap performs controlled session reset on incomplete-account signal.
- Decision: Bootstrap handlers classify incomplete-account responses as session-invalid-for-use, clear auth state once, and display actionable guidance.
- Rationale: Prevents repeated reconnect/teardown loops while keeping behavior observable.
- Alternative considered: Ignore bootstrap error and keep session active. Rejected because user remains in broken authenticated state.

## Risks / Trade-offs

- [Risk] Additional auth/user coordination may increase latency in login/register paths.
  -> Mitigation: Keep linkage check minimal and indexed; instrument timing to detect regressions.
- [Risk] Existing clients might rely on old 401 body shape.
  -> Mitigation: Preserve status semantics while versioning/standardizing error payload fields.
- [Risk] Recovery logic for partial provisioning may create duplicate retries under load.
  -> Mitigation: Use idempotent user-creation guard keyed by account identity and emit metrics on retry outcomes.
- [Risk] Frontend could over-handle non-auth errors as auth failures.
  -> Mitigation: Restrict controlled reset to explicit incomplete-account code(s), not all 4xx responses.

## Migration Plan

1. Introduce/standardize backend error code constants for invalid credentials and incomplete account provisioning.
2. Update auth-service login/register flow to verify account-user linkage before constructing success token response.
3. Add rollback/compensation behavior for first-time provisioning failures so success response is never emitted in partial states.
4. Update frontend auth error extraction and bootstrap handlers to recognize and surface standardized messages/codes.
5. Roll out with logs/metrics dashboards for invalid credential count, incomplete-account count, and bootstrap session-reset count.
6. Rollback strategy: revert to previous auth flow while preserving new logs; frontend remains compatible because it still handles generic errors.

## Open Questions

- Should incomplete-account return HTTP 409 or 412 for better semantic distinction, or remain 401 with explicit code?
- Should backend attempt bounded automatic repair (single retry) before returning incomplete-account to client?
- Do we require one-time user-facing copy for incomplete-account recovery (e.g., "Please retry in a moment") across all clients?
