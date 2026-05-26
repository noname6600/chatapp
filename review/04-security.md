# 04. Security

## Security Model Summary
Primary model is bearer JWT resource-server validation at gateway and service boundaries, with websocket ticket indirection at realtime edge.

## Authentication
- Access token issuance: `auth-service/service/impl/TokenService.java` (RS256 + `kid`).
- Access token validation in auth service internals: `auth-service/jwt/impl/JwtVerifierService.java`.
- Gateway JWT validation: `gateway-service/config/SecurityConfig.java`.
- Service JWT validation: each service `SecurityConfig` uses oauth2 resource server.

## Refresh Token Strategy
- Refresh tokens are random opaque strings; only SHA-256 hash stored (`TokenServiceFacade`).
- Rotation on refresh: old token revoked then new token issued.
- Replay defense: if already revoked/used race detected, account-wide revoke is triggered.

Strength:
- one-time rotation pattern is good practice.

Limitation:
- no explicit device/session metadata or selective session management beyond token rows.

## Websocket Auth
- FE requests realtime ticket via `/api/v1/realtime/ticket` with bearer token.
- Ticket stores `userId:accessToken` in Redis for 30s.
- Handshake interceptor validates ticket and stores token reference key (`ws:session-token:*`).
- Realtime handler closes session if token reference expires.

Strength:
- avoids relying on browser websocket custom auth headers.

Risks:
- temporary storage of access token in Redis session-token key.
- ticket payload format is string-concatenated; parsing safety depends on strict token form.

## Authorization
- Gateway adds `X-User-Id` after JWT validation.
- Services mostly derive user id from JWT subject.
- Room authorization checks exist in presence edge command path and chat membership guards.

Risk:
- some controller methods expose broad internal/bulk APIs that rely on higher-layer trust; strict network/service auth is required.

## CORS/CSRF/XSS
- CORS configurable via `common.security.cors` properties.
- CSRF disabled for stateless APIs (expected).
- XSS primarily frontend rendering concern; backend sends plain data and does not enforce content sanitization globally.

## Secret Management
- local/dev defaults exist in YAML and compose.
- production requires env substitution (`RESEND_API_KEY`, cloudinary secrets, internal auth tokens).

Risk:
- permissive defaults can leak into non-prod-like staging if environment hygiene is weak.

## Gateway Protection
- rate limiting via Redis request-rate-limiter.
- retries and circuit breakers reduce cascading failure from edge.
- jwt skew configurable (`gateway.security.jwt.clock-skew-seconds`, default 60).

Risk:
- oversized skew can extend effective token lifetime under clock drift.

## Internal Service Trust Model
- Some services add internal token filter for internal endpoints (`InternalServiceAuthFilter` variants).
- This is additive to OAuth JWT checks in many cases.

Risk:
- if internal token value is weak/default, internal API trust can be bypassed in flat networks.

## Critical Risks
1. Non-durable Redis pub/sub used for important realtime updates can silently drop events under subscriber pressure.
2. `ddl-auto: update` in production-like deployments can create uncontrolled schema drift and security review blind spots.
3. Mixed trust paths (JWT + internal token + gateway header) increase policy complexity and misconfiguration risk.
4. Websocket auth token lifecycles depend on Redis key TTL and cleanup correctness.
