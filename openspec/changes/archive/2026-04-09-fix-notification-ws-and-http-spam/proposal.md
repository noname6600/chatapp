## Why

Notification realtime is currently causing two forms of client-side spam: repeated websocket reconnect attempts and repeated HTTP calls to `/api/v1/notifications`. This creates noisy network traffic, unstable UX, and unnecessary backend load when the client enters reconnect/reconcile loops.

## What Changes

- Harden notification websocket reconnect behavior to prevent infinite fixed-interval retry loops.
- Add notification fetch rate controls so repeated reconnect/open events cannot spam `/api/v1/notifications`.
- Add in-flight deduplication and cooldown/debounce for convergence fetches triggered by socket lifecycle events.
- Add diagnostics for why a notification fetch was executed, skipped, coalesced, or rate-limited.
- Add targeted tests for websocket instability + fetch throttling interactions.

## Capabilities

### New Capabilities
- `notification-sync-fetch-throttling`: bounded, deduplicated, and observable notification sync fetch policy under reconnect churn.

### Modified Capabilities
- `notification-realtime-sync`: reconnect/recovery behavior is updated to ensure bounded websocket retries and bounded convergence-fetch frequency.

## Impact

- Frontend websocket client: `chatappFE/src/websocket/notification.socket.ts`
- Frontend notification store sync flow: `chatappFE/src/store/notification.store.tsx`
- Frontend notification API integration behavior for `GET /api/v1/notifications`
- Frontend tests: `chatappFE/src/websocket/notification.socket.test.ts` and notification store test coverage
