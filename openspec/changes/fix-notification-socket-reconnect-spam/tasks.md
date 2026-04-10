## 1. Reconnect State Machine Hardening

- [ ] 1.1 Add bounded exponential backoff configuration and delay calculator in `chatappFE/src/websocket/notification.socket.ts`
- [ ] 1.2 Track unstable reconnect failures for both pre-open closes and rapid post-open closes within a stability window
- [ ] 1.3 Enforce per-session-signature suppression after threshold failures to stop infinite retry loops
- [ ] 1.4 Reset retry counters/suppression when connection becomes stable, token signature changes, or manual disconnect occurs

## 2. Diagnostics and Observability

- [ ] 2.1 Add structured lifecycle logs for connect/open/error/close/retry decisions including close code/reason and connection lifetime
- [ ] 2.2 Add explicit suppression and reset diagnostics with trigger reason and current retry state

## 3. Store/Lifecycle Alignment

- [ ] 3.1 Verify `NotificationProvider` websocket lifecycle integration remains correct with updated reconnect logic in `chatappFE/src/store/notification.store.tsx`
- [ ] 3.2 Ensure reconnect-triggered convergence sync (`syncNotifications("reconcile")`) still runs after successful reopen

## 4. Test Coverage

- [ ] 4.1 Extend `chatappFE/src/websocket/notification.socket.test.ts` to verify exponential backoff progression and cap behavior
- [ ] 4.2 Add tests that verify suppression after repeated unstable post-open closes and pre-open closes
- [ ] 4.3 Add tests that verify suppression reset on token/signature change and stable open

## 5. Validation

- [ ] 5.1 Run targeted frontend tests for notification socket and notification store behavior
- [ ] 5.2 Perform manual runtime verification that repeated close loops no longer produce infinite 3-second reconnect spam
- [ ] 5.3 Capture final verification notes in change discussion before `/opsx:apply`
