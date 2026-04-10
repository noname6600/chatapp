## 1. WebSocket Reconnect Spam Control

- [x] 1.1 Implement bounded exponential backoff (with max cap) for notification websocket reconnect attempts in `chatappFE/src/websocket/notification.socket.ts`
- [x] 1.2 Classify both pre-open closes and rapid post-open closes as instability failures for suppression accounting
- [x] 1.3 Add per-session-signature suppression after repeated non-recoverable failures and reset on stable open, token change, or manual disconnect
- [x] 1.4 Add structured reconnect lifecycle diagnostics (attempt, delay, code/reason, lifetime, suppression/reset trigger)

## 2. Notifications API Spam Control

- [x] 2.1 Add single-flight guard for notification sync so concurrent reconnect triggers do not launch parallel `GET /api/v1/notifications` calls in `chatappFE/src/store/notification.store.tsx`
- [x] 2.2 Add reconnect-trigger cooldown/min-interval policy to bound frequent sync fetches during websocket churn
- [x] 2.3 Introduce trigger-reason handling (`initial_load`, `socket_reconnect`, `manual_action`, `post-mark-read`) and ensure manual/user-intent paths bypass reconnect cooldown
- [x] 2.4 Add diagnostics for fetch decisions (executed, coalesced, skipped by cooldown, skipped due to in-flight)

## 3. Tests and Validation

- [x] 3.1 Extend `chatappFE/src/websocket/notification.socket.test.ts` to cover backoff progression, suppression thresholds, and reset behavior
- [x] 3.2 Add/extend notification store tests to verify in-flight dedupe and cooldown behavior for `/api/v1/notifications` sync calls
- [x] 3.3 Run targeted frontend tests and verify no regressions in notification read/mark-all flows
- [x] 3.4 Perform manual runtime verification that websocket reconnect churn no longer causes infinite 3-second reconnect spam and no longer floods `/api/v1/notifications`
