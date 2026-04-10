## 1. Reproduce and Isolate Failure

- [ ] 1.1 Verify friendship-service WebSocket endpoint registration and startup logs (`/ws/friendship` on port 8085)
- [x] 1.2 Confirm auth-service JWKS endpoint reachability from friendship-service runtime
- [x] 1.3 Reproduce handshake failure with browser and capture frontend connect/error/close sequence
- [ ] 1.4 Capture backend handshake interceptor logs for the same attempts and map failure stage

## 2. Backend Handshake Observability and Validation

- [x] 2.1 Add structured logs in handshake interceptor for request entry, token presence, decode attempt, and rejection reason
- [x] 2.2 Add structured logs in JWT decode path with safe token redaction and exception class
- [x] 2.3 Add structured logs in friendship WebSocket handler for accepted connect/disconnect with session/user context
- [ ] 2.4 Validate that failed and successful handshakes now emit deterministic reasoned logs

## 3. Frontend Realtime Reliability

- [x] 3.1 Add structured frontend logs for connect attempt, reconnect scheduling, and manual-close suppression
- [x] 3.2 Ensure unread-count reconciliation executes on authenticated initialization independent of socket open
- [x] 3.3 Preserve realtime event-driven increments/decrements when socket is connected
- [ ] 3.4 Validate badge correctness across connected, disconnected, and reconnecting states

## 4. Logging Noise Reduction

- [x] 4.1 Disable verbose Hibernate SQL output in friendship-service default dev run profile
- [ ] 4.2 Confirm WebSocket diagnostics are visible and not drowned by SQL traces

## 5. Verification

- [ ] 5.1 Verify successful websocket connect path logs across frontend and backend
- [ ] 5.2 Verify expected rejection logs for invalid/expired token
- [ ] 5.3 Verify unread badge remains correct during websocket outage via HTTP fallback
- [ ] 5.4 Verify realtime badge increments after websocket recovery