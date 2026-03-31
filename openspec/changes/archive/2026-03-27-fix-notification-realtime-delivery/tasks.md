## 1. Notification Realtime Wiring

- [x] 1.1 Audit notification websocket subscription lifecycle in notification store/provider and identify why events are not applied until refresh
- [x] 1.2 Refactor notification event handling to a single state transition path for NOTIFICATION_NEW updates (bell count + list prepend)
- [x] 1.3 Ensure listener registration is idempotent and cleaned up correctly to avoid missed events or duplicate increments

## 2. Reconnect Recovery

- [x] 2.1 Add reconnect-aware notification handler registration so events continue after socket reconnect
- [x] 2.2 Trigger notification state reconciliation fetch after reconnect success to recover from missed events
- [x] 2.3 Ensure merge logic prevents stale snapshot from overwriting newer realtime updates

## 3. Room/Bell Unread Consistency

- [x] 3.1 Align room unread and bell unread realtime update behavior for non-muted rooms in the same interaction cycle
- [x] 3.2 Preserve muted-room suppression behavior while keeping persisted notification visibility intact
- [x] 3.3 Verify sender/recipient unread rules remain unchanged for message events

## 4. Validation

- [x] 4.1 Add or update unit tests for realtime NOTIFICATION_NEW event handling without refresh
- [x] 4.2 Add or update tests covering websocket reconnect and post-reconnect event processing
- [x] 4.3 Run frontend test suite and confirm all tests pass
- [ ] 4.4 Manual verification: with two users active, new notifications appear in receiver UI immediately without page refresh