## 1. Backend Event Scope

- [x] 1.1 Audit friendship-service friend request event publishing and identify nonessential add-friend realtime paths.
- [x] 1.2 Restrict add-friend-related event publishing/consumption to friend request notification signals only.
- [x] 1.3 Ensure unread count reconciliation endpoint remains authoritative for badge state.

## 2. Frontend Notification Badge

- [x] 2.1 Update add-friend notification event handler to process incoming friend request badge increments.
- [x] 2.2 Add outgoing send-request feedback notification flow without increasing incoming unread badge count.
- [x] 2.3 Reconcile badge count on initialization/reconnect using unread-count API.

## 3. Recommendation Status Behavior

- [x] 3.1 Define recommendation item status model for available, pending, and friend states.
- [x] 3.2 Update send-request action to transition item status to pending and disable duplicate action.
- [x] 3.3 Apply accepted/rejected/canceled updates to recommendation item status transitions.
- [x] 3.4 Remove unrelated realtime recommendation list mutation logic from add-friend page.

## 4. Validation

- [x] 4.1 Verify incoming request increments notification badge for recipient user.
- [x] 4.2 Verify outgoing request shows feedback but does not increment unread incoming badge.
- [x] 4.3 Verify recommendation status transitions remain consistent across refresh and reconnect.
- [x] 4.4 Add or update tests for badge reconciliation and recommendation state transitions.
