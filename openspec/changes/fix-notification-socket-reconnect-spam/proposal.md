## Why

The notification websocket currently enters a repeated reconnect loop (`Scheduling reconnect` every 3 seconds) when the socket opens then closes shortly after. This causes noisy network spam, obscures root-cause debugging, and can degrade client performance while still leaving notification realtime unreliable.

## What Changes

- Add resilient reconnect policy for notification websocket:
  - detect rapid post-open close loops
  - apply capped exponential backoff instead of fixed-interval retry
  - suppress retries for the same auth/session signature after repeated non-recoverable failures
- Add structured client diagnostics for connect/open/error/close/retry transitions, including close code/reason and connection lifetime.
- Add explicit recovery reset conditions (successful stable open, token/session change, manual disconnect).
- Add targeted tests that validate backoff progression, suppression behavior, and reset behavior.

## Capabilities

### New Capabilities
- `notification-websocket-reconnect-resilience`: resilient reconnect and suppression rules for unstable notification websocket sessions.

### Modified Capabilities
- `notification-realtime-sync`: strengthen reconnect/recovery requirements so realtime behavior remains deterministic under websocket instability and avoids infinite retry spam.

## Impact

- Frontend websocket client: `chatappFE/src/websocket/notification.socket.ts`
- Frontend notification store integration: `chatappFE/src/store/notification.store.tsx` (only if lifecycle hooks require alignment)
- Frontend tests: `chatappFE/src/websocket/notification.socket.test.ts`
- Observability/debugging via browser console diagnostics for notification socket lifecycle
