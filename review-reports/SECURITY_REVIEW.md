# Security Review

## Findings (ordered by severity)

### 1) CRITICAL - Cloudinary credentials are embedded as defaults in chat-service config
- Severity: CRITICAL
- Exact location: `chat-service/src/main/resources/application.yaml` (`cloudinary.api-key` and `cloudinary.api-secret` default values)
- Root cause: Secret-like values present in config defaults.
- Impact: Credential exposure risk and key leakage propagation.
- Reproduction risk: High (static exposure).
- Scalability risk: High (blast radius across environments).
- Recommended fix direction: Remove credential defaults and enforce secret manager/env-only injection.
- Shared/common changes required: No

### 2) HIGH - WebSocket handshake stores bearer token in server-side session attributes
- Severity: HIGH
- Exact location: `realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/JwtHandshakeInterceptor.java`
- Root cause: Access token persisted in memory for session lifetime.
- Impact: Secret exposure in memory diagnostics and broader compromise impact.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Persist minimal identity claims only; avoid raw token retention.
- Shared/common changes required: No

### 3) HIGH - Internal service credential controls are inconsistent across services
- Severity: HIGH
- Exact location: `user-service` and `friendship-service` have dedicated internal auth filters; `notification-service` equivalent internal ingress filter not present in security chain
- Root cause: Trust-boundary protection pattern is not uniformly implemented.
- Impact: Uneven defense for internal-only endpoints and cross-service command surfaces.
- Reproduction risk: Medium.
- Scalability risk: High as service-to-service calls expand.
- Recommended fix direction: Standardize internal service auth policy for all internal command endpoints.
- Shared/common changes required: Potentially Yes (policy standardization), but service-local hardening possible

### 4) MEDIUM - Gateway permits `/ws/**` anonymously by policy
- Severity: MEDIUM
- Exact location: `gateway-service/src/main/java/com/chatweb/gateway/config/SecurityConfig.java` (`PUBLIC_GENERAL_PATHS` includes `/ws/**`)
- Root cause: WebSocket path exempted from gateway JWT enforcement, relying on edge ticket + Authorization header.
- Impact: Increased reliance on downstream handshake controls only.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Keep strict ticket validation and add explicit abuse controls/monitoring at ingress.
- Shared/common changes required: No

### 5) MEDIUM - JWT validation horizon for websocket sessions is handshake-only
- Severity: MEDIUM
- Exact location: `realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/websocket/JwtHandshakeInterceptor.java` and runtime handler flow
- Root cause: No explicit post-handshake token validity enforcement.
- Impact: Long-lived sessions may continue after token expiry.
- Reproduction risk: Medium.
- Scalability risk: Medium.
- Recommended fix direction: Enforce session/token expiry policy and re-auth strategy.
- Shared/common changes required: No

### 6) LOW - Upload service prepare-token secret may be left blank by env
- Severity: LOW
- Exact location: `upload-service/src/main/resources/application.yaml` (`upload.confirm.prepare-token-secret` default empty)
- Root cause: Security-sensitive setting can be unset depending on deployment discipline.
- Impact: Weak request confirmation guarantees if not configured.
- Reproduction risk: Low to Medium.
- Scalability risk: Medium.
- Recommended fix direction: Fail fast in production when secret is missing.
- Shared/common changes required: No
