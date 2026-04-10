## Why

Realtime friend-request badge updates are failing because the friendship WebSocket connection repeatedly closes during handshake, leaving users without live unread updates. This needs a full diagnostic and reliability hardening so realtime works consistently in local/dev environments and surfaces actionable failure reasons when it does not.

## What Changes

- Add explicit, structured observability for friendship WebSocket lifecycle across frontend and backend handshake/auth stages.
- Add deterministic connection validation behavior so handshake failures are diagnosable (token, JWKS reachability, endpoint registration, and close reasons).
- Add resilient unread-count synchronization fallback behavior so badge state remains correct when WebSocket is temporarily unavailable.
- Tighten frontend reconnection behavior to avoid noisy infinite retries without diagnostic signal.
- Remove noisy SQL debug logging that obscures WebSocket diagnostics in friendship-service logs.

## Capabilities

### New Capabilities
- `friendship-websocket-diagnostic-observability`: End-to-end diagnostics for friendship WebSocket connection, handshake, token validation, and disconnect causes.

### Modified Capabilities
- `friend-request-notification-badge`: Ensure unread badge correctness with explicit fallback synchronization when realtime transport is unavailable.

## Impact

- Frontend: friendship socket connection/retry logic, friend initialization provider, badge state synchronization/logging.
- Backend: friendship-service WebSocket handshake/auth logging and runtime diagnostics.
- Operations: cleaner logs by disabling excessive Hibernate SQL output in friendship-service dev runtime.
- Testing: add checks for connected, rejected, and fallback states to validate realtime and degraded behavior.