# Security Review — chatappBE
> Deep-dive audit | Date: 2026-05-20 | Reviewer: multi-agent analysis

---

## 1. Credential & Secret Management

### SEC-01 — CRITICAL | Live Credentials Committed to Source Control
**Affected files:**
- `auth-service/src/main/resources/application-local.yaml`
- `user-service/src/main/resources/application-local.yaml`
- `chat-service/src/main/resources/application.yaml` (hardcoded fallback default)

| Secret | Exposure | Impact |
|--------|----------|--------|
| Google OAuth Client Secret (`GOCSPX-U_I6V5Q8mi...`) | `application-local.yaml` | Forge OAuth tokens, impersonate any user |
| Google OAuth Client ID (`168817576886-ve2mr7d3mt741...`) | `application-local.yaml` | Phishing, OSINT |
| Resend API Key (`re_gJP3mbc7_GpmKp1Ls...`) | `application-local.yaml` | Send emails from your domain |
| Cloudinary API Key (`877947325938691`) | `application-local.yaml` | Read/write media assets |
| Cloudinary API Secret (`KfHivMv0wUQ9D-89rVx8Wu5U12w`) | `application-local.yaml` AND `chat-service/application.yaml` default | Full Cloudinary account compromise |

The `chat-service/application.yaml` embeds the Cloudinary secret as:
```yaml
api-secret: ${CLOUDINARY_API_SECRET:KfHivMv0wUQ9D-89rVx8Wu5U12w}
```
Any JAR built from this repo carries the secret even if the env var is overridden at runtime — it is baked into the classpath resource.

**Immediate Actions Required:**
1. Rotate ALL exposed credentials immediately via each provider's console.
2. Remove ALL fallback defaults from YAML files. Use env vars with no defaults for secrets.
3. Add `application-local.yaml` to `.gitignore` across all services.
4. Run `git filter-repo` or BFG Repo Cleaner to scrub git history.
5. Add CI secret-scanning (Gitleaks, truffleHog) to block future commits.

---

### SEC-02 — CRITICAL | RSA Private Keys Stored in Plaintext in Database
**Affected files:**
- `auth-service/src/main/java/com/chatweb/auth/entity/JwtKeyEntity.java`
- `auth-service/src/main/java/com/chatweb/auth/jwt/impl/KeyManager.java`

The JWT signing RSA private key is persisted as a Base64-encoded `TEXT` column in `jwt_keys`. Any DB read access (backup file, read replica, SQL injection, DBA) yields the key, enabling arbitrary JWT forgery at every downstream service.

**Recommended Fix:**
- Short-term: encrypt the column with `pgcrypto` using an application-managed envelope key stored separately from the DB.
- Production: store private keys in AWS KMS, HashiCorp Vault Transit Secrets Engine, or an HSM. The DB should hold only the public key for JWKS serving.

---

## 2. Authentication Architecture

### SEC-03 — HIGH | JwtAuthenticationFilter Double-Registered as Servlet Filter
**Affected files:**
- `auth-service/src/main/java/com/chatweb/auth/configuration/JwtAuthenticationFilter.java`
- `auth-service/src/main/java/com/chatweb/auth/configuration/SecurityConfig.java`

`JwtAuthenticationFilter` is `@Component`, causing Spring Boot to auto-register it as a servlet filter AND the explicit `addFilterBefore` in `SecurityConfig` registers it a second time. It executes twice per `apiFilterChain` request and once on the `oauth2FilterChain` paths (`/oauth2/**`, `/login/**`), interfering with browser OAuth flows when a stale JWT header is present.

**Fix:** Remove `@Component`. Create an explicit `@Bean` and add a `FilterRegistrationBean<JwtAuthenticationFilter>` with `setEnabled(false)` to prevent auto-registration.

---

### SEC-04 — CRITICAL | WebSocket Route `/ws/**` is Public at the Gateway
**Affected files:**
- `gateway-service/src/main/resources/application.yaml`
- `gateway-service/src/main/java/com/chatweb/gateway/config/SecurityConfig.java`

`/ws/**` is in `PUBLIC_GENERAL_PATHS`. Any unauthenticated actor can attempt WebSocket upgrade. The realtime-edge-service has its own ticket validation, but there is zero defense-in-depth at the gateway layer.

Additionally, `docker-compose.yml` exposes `realtime-edge-service:8090` directly to the host — bypassing the gateway entirely.

**Fix:**
- Apply a JWT pre-auth filter to `/ws/**` at the gateway.
- Remove `ports: "8090:8090"` from production `docker-compose.yml`.

---

### SEC-05 — HIGH | Issuer Claim Not Validated at the Gateway JWT Decoder
**Affected file:** `gateway-service/src/main/java/com/chatweb/gateway/config/SecurityConfig.java`

```java
OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
    new JwtTimestampValidator(Duration.ofSeconds(clockSkewSeconds))
    // NO JwtIssuerValidator added
);
```
Any JWT signed with a registered `kid` is accepted regardless of the `iss` claim.

**Fix:**
```java
new JwtIssuerValidator("http://auth-service:8081")
```
Add as a second element in the delegating validator.

---

### SEC-06 — HIGH | `LocalValidationJwtDecoderConfig` Can Bypass All Auth in Production
**Affected files:**
- `realtime-edge-service/src/main/java/com/chatweb/realtime/config/LocalValidationJwtDecoderConfig.java`
- `notification-service/src/main/java/com/chatweb/notification/configuration/LocalValidationJwtDecoderConfig.java`

This `@Primary @Profile("local")` bean accepts any string as a valid JWT, treating it as a userId UUID. If `phaseb.local.validation.enabled=true` lands in a production ConfigMap or env var override, authentication is completely bypassed for the service.

**Fix:** Remove `@Primary`. Rename the property gate to require explicit opt-in (`ENABLE_AUTH_BYPASS_DO_NOT_USE_IN_PRODUCTION=true`) that is blocked by CI pipelines.

---

### SEC-07 — HIGH | Downstream Services Trust `X-User-Id` Header Without JWT Verification
**Affected files:** `gateway-service/.../filter/JwtAuthFilterGatewayFilterFactory.java` + all downstream `SecurityConfig.java` files

The gateway strips and re-sets `X-User-Id` from the validated JWT. Downstream services read this header for identity. If any service port is reachable directly (realtime-edge-service:8090 is confirmed exposed), an attacker with a valid JWT can send arbitrary `X-User-Id` to impersonate any user.

**Fix:** Downstream services must derive user identity exclusively from validated JWT claims (`authentication.getName()`), not from forwarded headers.

---

### SEC-08 — HIGH | Gateway JwtAuthFilter Falls Through on Empty Security Context
**Affected file:** `gateway-service/.../filter/JwtAuthFilterGatewayFilterFactory.java`

```java
.switchIfEmpty(chain.filter(exchange));
```
If the security context is empty (auth filter misconfiguration), the request is forwarded without `X-User-Id`, causing downstream controllers to throw 400 (bad request) instead of 401.

**Fix:** Replace with an explicit 401 rejection:
```java
.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED)))
```

---

## 3. OAuth Security

### SEC-09 — HIGH | OAuth Exchange Code Exposed in URL Query Parameter
**Affected file:** `auth-service/src/main/java/com/chatweb/auth/configuration/GoogleOAuthAuthenticationSuccessHandler.java`

The redirect after Google callback:
```
https://frontend/auth/oauth/google/callback?code=<exchangeCode>
```
The raw exchange code appears in browser history, nginx access logs, server logs, and referrer headers sent to any third-party resource the frontend loads. The 120-second expiry mitigates but does not eliminate the risk given log persistence.

**Fix:** Deliver the code via a POST-redirect, or complete the token exchange server-side and set JWT in an HttpOnly cookie. Never put security tokens in URL query parameters.

---

### SEC-10 — HIGH | OAuth Exchange + Token Issuance in Separate Transactions
**Affected file:** `auth-service/src/main/java/com/chatweb/auth/service/impl/BrowserOAuthService.java`

`consume()` and `issueTokens()` are separate `@Transactional` methods. If `issueTokens()` fails after `consume()` commits, the exchange code is permanently consumed but no tokens are issued. The user's OAuth flow is irrecoverably broken.

**Fix:** Wrap both in a single `@Transactional` boundary.

---

### SEC-11 — MEDIUM | No Account Lockout or Per-Account Rate Limiting
**Affected:** `auth-service/.../service/impl/LocalAuthService.java`

The gateway's 20 req/s global rate limiter allows ~1,200 password attempts per minute per IP. No per-account lockout exists.

**Fix:** Add a Redis-based per-account failed-attempt counter. Lock account for N minutes after 10 failures. Add a stricter gateway `RequestRateLimiter` route for `/api/v1/auth/login` (e.g., 5 req/min per IP).

---

### SEC-12 — MEDIUM | Password Strength Not Validated at Registration
**Affected:** `auth-service/.../service/impl/LocalAuthService.java`

`ForgotPasswordService.resetPassword()` validates with `STRONG_PASSWORD_PATTERN` (8-72 chars, upper/lower/digit). Registration has no validation — a 1-character password is accepted.

**Fix:** Apply the same validator in `RegisterRequest` using a `@Pattern` Bean Validation annotation.

---

## 4. Internal Service Authentication

### SEC-13 — HIGH | Internal Auth Token Defaults to Empty String
**Affected:** `user-service/src/main/java/com/chatweb/user/configuration/InternalServiceAuthFilter.java` + equivalents in friendship, presence

`token: ${INTERNAL_AUTH_TOKEN:}` — the fallback is `""`. The `failFastInProductionWhenUnconfigured()` check only fires for profiles named exactly `prod` or `production`. Any other production profile name (`staging`, `production-k8s`, `production-eu`) silently passes the check and uses the empty token guard.

**Fix:** Remove the empty string default entirely. Use `@PostConstruct` validation that throws regardless of profile name if the token is blank.

---

### SEC-14 — MEDIUM | `InternalServiceAuthFilter` Duplicated Across 3 Services
**Affected:** `user-service`, `friendship-service`, `presence-service` — three independent copies

A security fix must be applied to all three manually; divergence is inevitable.

**Fix:** Move to `common-security` as a shared `@ConditionalOnProperty`-gated autoconfigured filter.

---

## 5. WebSocket Security

### SEC-15 — HIGH | New JWT Accepted in WebSocket `handleTokenRefresh` Without Validation
**Affected file:** `realtime-edge-service/.../adapter/in/websocket/RealtimeWebSocketHandler.java`

```java
session.getAttributes().put("accessToken", newToken);
```
Any string is stored and forwarded to downstream services as `Authorization: Bearer <string>` with no validation.

**Fix:** Validate the new token with `JwtDecoder`. Verify the `sub` claim matches the existing session's userId.

---

### SEC-16 — HIGH | Full JWT Stored as Plaintext in Redis Ticket
**Affected:** `RealtimeTicketController.java`, `JwtHandshakeInterceptor.java`

Ticket value = `<userId>|<accessToken>` in plaintext Redis with 30-second TTL. Any actor with Redis read access harvests valid session tokens.

**Fix:** Store only `userId` in the ticket. The client sends the JWT in the `Authorization` header during the WebSocket upgrade; validate it at that point.

---

### SEC-17 — MEDIUM | No WebSocket Frame Size Limit — OOM DoS
**Affected file:** `realtime-edge-service/src/main/java/com/chatweb/realtime/config/WebSocketConfig.java`

No `setMaxTextMessageBufferSize`. A single oversized message is buffered entirely in heap.

**Fix:**
```java
registry.addHandler(handler, "/ws")
        .setMaxTextMessageBufferSize(65536);
```

---

## 6. API Security Gaps

### SEC-18 — HIGH | `GET /users/search` Has No Authentication Guard
**Affected file:** `user-service/.../controller/UserProfileController.java`

`findByUsernameContainingIgnoreCase` with `ILIKE '%query%'` is accessible to unauthenticated callers — full username enumeration with no cost.

**Fix:** Add `@AuthenticationPrincipal Jwt jwt`. Add a stricter rate-limit route at the gateway.

---

### SEC-19 — HIGH | `POST /rooms/members/bulk` — No Auth, No Size Cap
**Affected file:** `chat-service/.../modules/room/controller/RoomController.java`

No authentication principal is extracted. Any caller can enumerate member lists for arbitrary rooms.

**Fix:** Require authentication. Verify the caller is a member of each requested room. Cap input list at 50.

---

### SEC-20 — MEDIUM | `GET /presence/global` — Reveals All Online Users
**Affected:** `presence-service/.../controller/PresenceController.java`

Returns all online users system-wide — a privacy violation and a scalability hazard (no pagination, could be megabytes).

**Fix:** Restrict scope to friends/room-mates. Add mandatory pagination (`Pageable`).

---

### SEC-21 — MEDIUM | `GET /friends/blocks/by-others` — Privacy Violation
**Affected:** `friendship-service/.../controller/FriendController.java`

User B can discover that User A has blocked them. This violates user expectations around block privacy.

**Fix:** Remove the endpoint or return only a count, not the IDs.

---

## 7. Infrastructure Security

### SEC-22 — CRITICAL | All Docker Containers Run as Root
**Affected:** All 9 Dockerfiles

No `USER` directive is present in any Dockerfile. A container escape or path traversal gives root on the host.

**Fix (apply to all Dockerfiles):**
```dockerfile
RUN addgroup -S app && adduser -S app -G app
USER app
```

---

### SEC-23 — MEDIUM | No Security Headers at Gateway or Nginx
Missing headers: `X-Content-Type-Options`, `X-Frame-Options`, `Content-Security-Policy`, `Strict-Transport-Security`, `Referrer-Policy`.

**Fix:** Add a `SecurityHeaders` filter bean in the gateway. Add `add_header` directives to nginx.

---

### SEC-24 — MEDIUM | CORS Allows Wildcard Headers With `allowCredentials: true`
**Affected file:** `common-web/src/main/java/com/chatweb/common/web/cors/CorsProperties.java`

`allowed-headers: "*"` with `allowCredentials: true` is invalid per the Fetch spec and may allow cross-origin custom header injection.

**Fix:** Enumerate explicit headers: `Authorization, Content-Type, X-Requested-With`.

---

### SEC-25 — LOW | JWT Clock Skew Tolerance of 60 Seconds is Excessive
**Affected:** `gateway-service/src/main/resources/application.yaml` → `GATEWAY_JWT_CLOCK_SKEW_SECONDS: 60`

Extends effective token lifetime by 1 minute post-expiry. Containers in the same Docker network have ≤2s NTP drift.

**Fix:** Reduce to `5`.

---

## Summary Table

| ID | Severity | Category | One-line description |
|----|----------|----------|---------------------|
| SEC-01 | CRITICAL | Secrets | Live credentials in source control + JAR |
| SEC-02 | CRITICAL | Key Mgmt | RSA private keys in plaintext DB TEXT column |
| SEC-04 | CRITICAL | Gateway | `/ws/**` fully public at gateway |
| SEC-22 | CRITICAL | Infra | All 9 containers run as root |
| SEC-03 | HIGH | Auth | JWT filter double-registered via @Component |
| SEC-05 | HIGH | JWT | Issuer claim not validated at gateway |
| SEC-06 | HIGH | Auth | LocalValidationJwtDecoder bypasses auth if enabled in prod |
| SEC-07 | HIGH | Trust | Downstream services trust forwarded X-User-Id header |
| SEC-08 | HIGH | Gateway | Gateway filter falls through on empty security context |
| SEC-09 | HIGH | OAuth | Exchange code exposed in URL query parameter |
| SEC-10 | HIGH | OAuth | Exchange + token issuance in separate transactions |
| SEC-13 | HIGH | Internal | Internal auth token defaults to empty string |
| SEC-15 | HIGH | WebSocket | Token refresh accepted without validation |
| SEC-16 | HIGH | WebSocket | Full JWT stored as plaintext in Redis |
| SEC-18 | HIGH | API | `/users/search` unauthenticated — username enumeration |
| SEC-19 | HIGH | API | `/rooms/members/bulk` no auth, no size limit |
| SEC-11 | MEDIUM | Rate Limit | No per-account lockout, weak auth rate limits |
| SEC-12 | MEDIUM | Validation | Password strength not checked at registration |
| SEC-14 | MEDIUM | Coupling | InternalServiceAuthFilter duplicated 3x |
| SEC-17 | MEDIUM | WebSocket | No WebSocket frame size limit — OOM DoS |
| SEC-20 | MEDIUM | Privacy | `/presence/global` reveals all online users |
| SEC-21 | MEDIUM | Privacy | `/friends/blocks/by-others` reveals who blocked you |
| SEC-23 | MEDIUM | Headers | No security headers at any layer |
| SEC-24 | MEDIUM | CORS | Wildcard headers with credentials |
| SEC-25 | LOW | JWT | 60s clock skew is excessive |
