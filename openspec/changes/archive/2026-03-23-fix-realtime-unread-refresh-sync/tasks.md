## 1. Room Store Realtime Unread Sync

- [x] 1.1 Update MESSAGE_SENT room store handler to skip unread increment when event sender matches current user
- [x] 1.2 Keep recipient-side unread increment behavior unchanged for incoming messages
- [x] 1.3 Ensure MESSAGE_EDITED and MESSAGE_DELETED handlers do not mutate unread count incorrectly
- [x] 1.4 Add deterministic merge rules to avoid duplicate unread updates from repeated websocket events

## 2. Refresh Reconciliation

- [x] 2.1 On room load/refresh, rehydrate unreadCount from backend /rooms/my response as authoritative state
- [x] 2.2 Reconcile in-memory room state with backend snapshot after websocket reconnect
- [x] 2.3 Ensure stale in-memory unread values are replaced (not additive) on refresh
- [ ] 2.4 Verify unread consistency after hard refresh and soft navigation between rooms

## 3. Unread Boundary Recovery in Message View

- [ ] 3.1 Compute first unread boundary index from backend-driven unreadCount and current message window
- [ ] 3.2 Render visible unread boundary divider between read and unread messages when boundary is in loaded page
- [ ] 3.3 Avoid rendering fake divider when unread boundary lies outside loaded message window
- [ ] 3.4 Preserve jump-to-latest affordance when boundary is outside loaded window

## 4. Entry Positioning Behavior

- [ ] 4.1 On room open, detect unread span height relative to viewport
- [ ] 4.2 For multi-page unread span, position boundary near top and show unread banner
- [ ] 4.3 For single-page unread span, center first unread near middle of viewport
- [ ] 4.4 Ensure positioning logic reruns correctly after async message load completes

## 5. Mark-Read Interaction Rules

- [ ] 5.1 Trigger mark-read when user scroll crosses unread boundary into viewed region
- [ ] 5.2 Trigger mark-read when user uses jump-to-latest action
- [ ] 5.3 Guard mark-read to fire once per room-view cycle to avoid duplicate calls
- [ ] 5.4 Confirm backend read state and frontend unread indicators converge after mark-read

## 6. Regression Tests

- [ ] 6.1 Unit test sender exclusion: sender event does not increase own unread count
- [ ] 6.2 Unit test recipient increment: non-sender receives unread increment
- [ ] 6.3 Unit test refresh reconciliation overwrites stale unread state from backend snapshot
- [ ] 6.4 Integration test unread boundary renders at first unread when boundary is loaded
- [ ] 6.5 Integration test unread banner/jump behavior when boundary is outside current page
- [ ] 6.6 Integration test mark-read on boundary scroll crossing
- [ ] 6.7 Integration test mark-read on jump-to-latest

## 7. Manual Validation

- [ ] 7.1 Smoke test real-time unread updates across two users in same room
- [ ] 7.2 Smoke test sender sees no unread badge increment for own message
- [ ] 7.3 Smoke test refresh keeps unread state consistent with backend
- [ ] 7.4 Smoke test multi-page unread entry behavior and banner visibility
- [ ] 7.5 Smoke test single-page unread entry behavior and middle positioning
