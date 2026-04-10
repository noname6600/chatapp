## 1. Realtime Room Store Updates

- [x] 1.1 Update room websocket message handler to atomically apply unread increment, latest timestamp, and last-message preview fields for the affected room.
- [x] 1.2 Ensure preview derivation uses normalized message/block formatting helper so TEXT/ASSET/ROOM_INVITE summaries are deterministic.
- [x] 1.3 Ensure background room updates trigger room-list re-render without requiring route change or manual refresh.

## 2. Reconciliation and Freshness Guards

- [x] 2.1 Update room snapshot reconciliation logic to avoid overwriting fresher websocket-applied preview/timestamp with stale snapshot data.
- [x] 2.2 Keep sender exclusion and muted-room unread rules unchanged while applying preview synchronization updates.
- [x] 2.3 Verify room ordering remains deterministic when timestamps are equal after realtime updates.

## 3. Validation and Regression Coverage

- [x] 3.1 Add/adjust unit tests for room store realtime updates on background incoming messages (unread + preview + latest timestamp together).
- [x] 3.2 Add/adjust tests for reconciliation behavior to confirm stale snapshot responses do not roll back newer preview state.
- [ ] 3.3 Perform manual two-user validation: incoming background-room message appears in room list preview and unread badge immediately without page refresh.
