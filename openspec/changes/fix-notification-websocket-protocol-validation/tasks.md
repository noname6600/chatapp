## 1. Backend Handshake Diagnostics - Logging

- [x] 1.1 Create rejection reason enum in `AbstractJwtHandshakeInterceptor.java` with values: `MISSING_TOKEN`, `INVALID_FORMAT`, `DECODE_FAILED`, `NULL_USER_ID`, `SERVLET_NOT_FOUND`
- [x] 1.2 Add structured logging for token missing case - log all query parameters present and rejection reason
- [x] 1.3 Add structured logging for token decode failures - include exception type and message
- [x] 1.4 Add structured logging for null userId cases - include the JWT subject claim value
- [x] 1.5 Add structured logging for successful handshakes - include userId and correlation ID

## 2. Backend Handshake Diagnostics - HTTP Response Headers

- [x] 2.1 Add `X-WebSocket-Token-Present` response header (true/false) in `beforeHandshake` method
- [x] 2.2 Add `X-WebSocket-Rejection-Reason` response header when handshake fails with specific rejection reason
- [x] 2.3 Add `X-WebSocket-Error-Details` optional response header for decode failures with exception type

## 3. Backend Handshake Diagnostics - Query Parameter Validation

- [x] 3.1 Add logging for all query parameter names in the WebSocket request (to detect gateway stripping)
- [x] 3.2 Add logging for token parameter length to verify token not truncated by gateway
- [x] 3.3 Add early exit with specific log message if using local dev without token (catch MISSING_TOKEN early)

## 4. Backend Metrics - Micrometer Integration

- [x] 4.1 Add Micrometer dependency to notification-service `build.gradle` if not present
- [x] 4.2 Inject `MeterRegistry` into `AbstractJwtHandshakeInterceptor`
- [x] 4.3 Implement `websocket.handshake.success` counter increment on successful handshake
- [x] 4.4 Implement `websocket.handshake.rejected` counter with `reason` tag on rejected handshake
- [x] 4.5 Implement `websocket.connections.active` gauge increment on open, decrement on close

## 5. Backend JWT Validation - Token Extraction Testing

- [x] 5.1 Add unit test for token extraction from query parameters (valid token case)
- [x] 5.2 Add unit test for missing token parameter rejection
- [x] 5.3 Add unit test for empty token value rejection
- [x] 5.4 Add unit test for invalid JWT signature rejection with logging
- [x] 5.5 Add unit test for null userId rejection

## 6. Integration Testing

- [ ] 6.1 Start notification service locally and test WebSocket connection with valid token
- [ ] 6.2 Verify rejection logging appears in console/logs for missing token case
- [ ] 6.3 Verify response headers appear in browser Network tab for rejected connections
- [ ] 6.4 Test that valid connections increment `websocket.handshake.success` metric
- [ ] 6.5 Test that rejected connections increment `websocket.handshake.rejected` metric with correct reason tag
- [ ] 6.6 Test WebSocket connection through API gateway to verify query parameter propagation

## 7. Documentation & Troubleshooting

- [x] 7.1 Document WebSocket JWT authentication flow in NotificationService README
- [x] 7.2 Add troubleshooting guide listing common rejection reasons (missing token, decode failed, etc.) and resolution steps
- [x] 7.3 Document metrics available for monitoring WebSocket health and how to alert on failed handshakes
- [x] 7.4 Add example curl/wscat commands for testing WebSocket connections with valid/invalid tokens
