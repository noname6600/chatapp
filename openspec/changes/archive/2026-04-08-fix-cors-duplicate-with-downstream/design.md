## Context

Browser clients connect only to the API gateway origin. In that topology, CORS must be enforced and answered at the edge where the browser receives responses. Today, missing gateway CORS handling can block preflight requests, while enabling CORS both at gateway and downstream can lead to duplicate `Access-Control-*` headers in proxied responses.

The system must preserve existing downstream CORS configurations because services can be reused outside gateway paths and because each service already owns local security defaults.

## Goals / Non-Goals

**Goals:**
- Ensure preflight `OPTIONS` requests succeed for browser calls routed through gateway.
- Prevent duplicate CORS response headers when downstream services also emit CORS headers.
- Keep CORS origins configurable by environment for production deployment.
- Preserve downstream CORS settings without requiring service-by-service rewrites.

**Non-Goals:**
- Replacing downstream CORS implementations in auth, user, chat, or other services.
- Supporting wildcard origins with credentials enabled.
- Redesigning unrelated security controls (JWT, RBAC, rate limiting).

## Decisions

1. Gateway remains browser-facing CORS owner for routed HTTP traffic.
- Rationale: Browser only evaluates the gateway response, so edge-level handling is required to avoid blocked preflights.
- Alternative considered: Downstream-only CORS. Rejected because gateway-auth and gateway security layers can intercept preflight and still fail before downstream is reached.

2. Keep downstream CORS enabled, but deduplicate gateway response headers.
- Rationale: This avoids breaking non-gateway use while preventing invalid duplicate CORS headers in browser responses.
- Alternative considered: Disable all downstream CORS. Rejected due to wider service coupling and migration risk.

3. Configure allowed origins from environment (`CORS_ALLOWED_ORIGINS`) and parse comma-separated values.
- Rationale: Enables production-safe origin control without code edits.
- Alternative considered: Static origins in config files. Rejected due to operational rigidity and environment drift.

4. Add response header deduplication filter for CORS headers at gateway route level.
- Rationale: Guarantees single-value CORS headers even if downstream emits matching headers.
- Alternative considered: Custom global response filter in Java. Rejected for now in favor of built-in gateway filter for lower complexity.

## Risks / Trade-offs

- [Risk] Misconfigured `CORS_ALLOWED_ORIGINS` blocks legitimate frontend origins.
  Mitigation: Provide explicit deployment defaults per environment and include smoke tests for preflight and login.

- [Risk] Over-deduplication could hide downstream header differences needed by some clients.
  Mitigation: Limit deduplication to known CORS headers and use RETAIN_FIRST strategy.

- [Risk] Non-browser clients may rely on downstream CORS behavior directly.
  Mitigation: Downstream CORS is preserved; gateway dedupe only affects proxied gateway responses.

- [Trade-off] CORS logic is split across layers (gateway + downstream), increasing conceptual overhead.
  Mitigation: Document gateway as authoritative edge CORS layer and keep downstream as compatibility layer.

## Migration Plan

1. Update gateway configuration to keep edge CORS enabled and add CORS header dedup filters.
2. Deploy to staging with `CORS_ALLOWED_ORIGINS` set to staging frontend origins.
3. Run smoke checks:
   - Browser preflight to `/api/v1/auth/login` returns `200/204` with exactly one `Access-Control-Allow-Origin`.
   - Login request succeeds from approved origin.
   - Request from non-approved origin is rejected by browser policy.
4. Roll out to production with production frontend origins.
5. Rollback strategy: remove dedupe filter and revert gateway CORS config to prior known-good commit if regressions occur.

## Open Questions

- Should websocket handshake routes (`/ws/**`) use the same allowed origin list or a dedicated one?
- Do we need environment-specific method/header restrictions beyond `*` for stricter compliance?
