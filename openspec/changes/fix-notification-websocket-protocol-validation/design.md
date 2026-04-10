## Context

The notification WebSocket endpoint (`/ws/notifications`) is rejecting client connections during the JWT handshake validation phase, returning HTTP 1002 (Protocol Error). The backend JWT handshake interceptor validates tokens extracted from query parameters, but when validation fails, it simply returns `false` from the `beforeHandshake` method without detailed error logging.

This causes:
1. Client receives protocol error and immediately closes connection
2. Client enters exponential backoff retry loop
3. Browser logs show repeated rapid connect/disconnect cycles
4. No clear indication of why the handshake is failing (missing token, invalid token, etc.)

**Current Flow:**
- Client connects: `ws://localhost:8080/ws/notifications?token=<JWT>`
- Gateway routes to: `backend:8084/ws/notifications?token=<JWT>`
- Backend HandshakeInterceptor extracts token and validates JWT
- If validation fails → handshake returns false → connection rejected
- No structured logging of failure reason

**Known Unknowns:**
- Is the token parameter being correctly propagated through the gateway?
- Is the JWT decoder properly configured for the current keys?
- Are tokens being expired or invalidated between successful HTTP calls and WebSocket attempts?

## Goals / Non-Goals

**Goals:**
- Provide comprehensive logging of handshake validation failures with specific reasons
- Ensure token parameter is correctly extracted and propagated through gateway routing
- Add metrics/counters to distinguish successful vs. failed handshakes
- Create clear error messages that distinguish between missing tokens, invalid tokens, expired tokens, and token decode failures
- Enable operators to troubleshoot connection issues from backend logs

**Non-Goals:**
- Change the authentication mechanism itself (still using JWT in query param)
- Modify client-side retry logic (that's a separate concern)
- Change access control policies (still require valid JWT)

## Decisions

### 1. Detailed rejection reason logging
**Decision:** Extend the handshake interceptor to log specific rejection reasons with structured fields.

**Rationale:** When a handshake fails, distinguishing between "missing token", "invalid token", "decode failed", and "null userId" is critical for troubleshooting. Structured logging enables filtering and alerting.

**Alternatives Considered:**
- Silent rejection (current): rejected because no diagnostics
- Return HTTP error codes: not applicable for WebSocket protocol
- Generic "auth failed" message: insufficient for troubleshooting

**Implementation:**
- Add an enum for rejection reasons: `MISSING_TOKEN`, `INVALID_FORMAT`, `DECODE_FAILED`, `NULL_USER_ID`, `SERVLET_NOT_FOUND`
- Log rejection reason + exception details (if applicable)
- Include token prefix for correlation (first 30 chars of token hash)

### 2. Add handshake metrics
**Decision:** Use Micrometer metrics to count successful/failed handshakes by reason.

**Rationale:** Metrics enable alerting on repeated handshake failures and provide visibility into error patterns over time.

**Implementation:**
- Counter: `websocket.handshake.success`
- Counter: `websocket.handshake.rejected` (tagged with rejection reason)
- Gauge: `websocket.connections.active`

### 3. Add HTTP response headers to indicate rejection reason
**Decision:** When beforeHandshake returns false, set response headers explaining why (for debugging in browser dev tools).

**Rationale:** While the WebSocket connection will be rejected regardless, these headers provide visibility in the Network tab for developers troubleshooting locally.

**Implementation:**
- Header: `X-WebSocket-Rejection-Reason: <reason>` when handshake fails
- Header: `X-WebSocket-Token-Present: true|false` to indicate token was found

### 4. Validate token extraction from query parameters
**Decision:** Add explicit logging and validation for query parameter extraction to catch gateway routing issues.

**Rationale:** If the gateway is not correctly propagating query parameters, tokens may be stripped before reaching the backend. This decision makes that visible.

**Implementation:**
- Log all query parameter names received
- Log whether "token" parameter key exists
- If no "token" key found, log other parameters present (for diagnostics)

## Risks / Trade-offs

- [Risk] Logging token prefix (even hashed) could expose token timing information. → Mitigation: Use fixed-length token prefix hash, truncate at 30 chars, never log full token.
- [Risk] Detailed error messages could leak authentication scheme info to attackers. → Mitigation: Only expose detailed messages in dev/staging logs, return generic rejection to clients.
- [Risk] Adding metrics could increase backend memory footprint if cardinality is high. → Mitigation: Limit rejection reason values to a known enum (5 values).

## Migration Plan

1. Add logging enhancements and metrics to JWT handshake interceptor (non-breaking)
2. Deploy to staging and test; verify logs show rejection reasons
3. Deploy to production; monitor handshake success/failure metrics
4. If token missing issue appears in logs → investigate gateway routing
5. If token decode issue appears → check JWT signing keys are synchronized
6. If null userId issue appears → check JWT subject claim is being set

**Rollback:**
- Revert interceptor changes and metrics registration; restart backend
- No database migrations or config changes to revert

## Open Questions

- Should we implement a circuit breaker that stops accepting connections after N consecutive handshake failures (to avoid wasting resources on broken tokens)?
- Should we add a health check endpoint that validates JWT decoder without requiring a WebSocket connection?
- Should we log the JWT subject claim (userId) on successful handshake for correlation with UserService?
