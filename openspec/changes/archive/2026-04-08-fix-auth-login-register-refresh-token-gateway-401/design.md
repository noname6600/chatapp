## Context

Authentication flows now pass through gateway-service before reaching backend services. After that integration, two regressions appeared in production-like runs: successful login/register responses may omit refresh tokens, and immediate post-auth API calls return 401 despite successful credential exchange. The change spans gateway route security, auth response contract, and token validation across services, so a cross-service design is required before implementation.

## Goals / Non-Goals

**Goals:**
- Ensure login and register success responses always include a valid refresh token and access token according to a single contract.
- Ensure gateway allows unauthenticated access to auth bootstrap endpoints and enforces authentication only on protected routes.
- Ensure issued access tokens are immediately accepted by gateway and downstream services without clock-skew or claim-mismatch false negatives.
- Improve diagnosability of 401 outcomes with structured rejection reasons.

**Non-Goals:**
- Replacing the existing token format or identity provider.
- Redesigning frontend session UX beyond adapting to a stable response contract.
- Introducing long-term multi-device session management policy changes.

## Decisions

1. Define a canonical auth success payload shared by login and register.
- Decision: both endpoints MUST return the same schema for success, including non-empty `accessToken`, non-empty `refreshToken`, token type, and expiry metadata.
- Rationale: frontend and gateway behavior becomes deterministic and removes branching that currently risks missing refresh-token writes.
- Alternatives considered:
  - Keep endpoint-specific payloads and normalize in frontend: rejected because contract drift can reintroduce breakage.
  - Add a separate "session bootstrap" endpoint: rejected for added latency and complexity.

2. Enforce explicit gateway route policy tiers.
- Decision: classify routes as `PUBLIC_AUTH`, `PUBLIC_GENERAL`, and `PROTECTED`; login/register/refresh endpoints are always `PUBLIC_AUTH` bypassing JWT validation middleware.
- Rationale: avoids accidental security filter application that causes 401 for authentication bootstrap endpoints.
- Alternatives considered:
  - Regex-based allowlist only in one filter: rejected due to fragility and poor auditability.
  - Permit all `/api/auth/**`: partially acceptable but too broad for future tightening.

3. Standardize token acceptance checks between gateway and auth issuer.
- Decision: gateway validation MUST use the same signing key configuration, issuer/audience expectations, and bounded clock-skew tolerance as token issuer config.
- Rationale: resolves immediate-after-login 401 caused by strict skew or claim mismatch.
- Alternatives considered:
  - Increase token TTL significantly: rejected because it hides validation mismatch and weakens security posture.
  - Retry failed requests in frontend automatically: rejected because it masks backend misconfiguration.

4. Introduce structured auth rejection telemetry.
- Decision: 401 responses from gateway include stable machine-readable reason codes in logs/metrics (not sensitive details in client body), e.g., `missing_token`, `expired_token`, `invalid_signature`, `route_misclassified`.
- Rationale: improves mean-time-to-diagnosis without leaking security internals to clients.
- Alternatives considered:
  - Keep generic 401 only: rejected due to poor observability.

## Risks / Trade-offs

- [Risk] Route misclassification can accidentally expose protected endpoints. -> Mitigation: add route policy tests and explicit deny-by-default for unmatched protected prefixes.
- [Risk] Strict schema enforcement can break older clients expecting legacy fields only. -> Mitigation: keep backward-compatible fields during transition window and document deprecation.
- [Risk] Clock-skew tolerance increases acceptance window slightly. -> Mitigation: keep skew minimal (e.g., <=60s) and monitor replay-related anomalies.

## Migration Plan

1. Introduce shared auth response DTO/contract tests in auth-service.
2. Apply gateway route policy map and add integration tests for auth/public/protected paths.
3. Align gateway JWT validation config with issuer config and deploy behind environment flag if needed.
4. Roll out to staging with synthetic login->protected-call checks.
5. Deploy to production gradually; monitor 401 rate and reason-code distribution.
6. Rollback: revert gateway policy and validation config to prior known-good release if 401 or auth bypass anomalies spike.

## Open Questions

- Should refresh token be returned in response body only, or body plus HttpOnly cookie for web clients?
- Which service owns register token minting in all pathways (auth-service vs delegated user-service flow)?
- What is the exact gateway path prefix strategy to avoid per-environment mismatch (`/api/v1` vs `/api`)?
