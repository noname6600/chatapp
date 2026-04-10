## 1. Last-Read Entry Anchor

- [x] 1.1 Derive first-unread anchor from backend read/unread metadata when opening a room.
- [x] 1.2 Implement room-open viewport positioning at unread boundary (or latest when unread is zero).
- [x] 1.3 Preserve anchor stability after async message window hydration.

## 2. Boundary Rendering and Bidirectional Window Loading

- [x] 2.1 Render unread divider as clear red boundary when first unread exists in loaded window.
- [x] 2.2 Prevent fake boundary rendering when unread boundary lies outside loaded range.
- [x] 2.3 Add upward pagination from anchor context for older history.
- [x] 2.4 Add downward pagination from anchor context for missing newer ranges before live tail.

## 3. Top Indicators and Latest Navigation

- [x] 3.1 Implement distance-to-latest top affordance when user is far above newest messages.
- [x] 3.2 Implement incremental "N new message(s)" top indicator while user reads older context.
- [x] 3.3 Wire top indicators to jump-to-latest action targeting current latest message at click time.
- [x] 3.4 Ensure top indicators clear or reconcile correctly after returning to live tail.

## 4. Realtime Unread Synchronization

- [x] 4.1 Keep sender-exclusion behavior for unread increments on message-sent events.
- [x] 4.2 Increment unread and incremental-top-indicator counts for recipient-side incoming messages.
- [x] 4.3 Reconcile unread/banner/top indicators by replacement from backend snapshot after reconnect/refresh.

## 5. Mark-Read Policy (How Read Is Marked)

- [x] 5.1 Audit existing mark-read API/command path and document required request timing at room-view layer.
- [x] 5.2 Trigger mark-read when viewport crosses unread boundary into unread region.
- [x] 5.3 Trigger mark-read on jump-to-latest completion and live-tail arrival conditions.
- [x] 5.4 Add per-room in-flight/idempotency guards so repeated triggers emit one effective mark-read request.
- [x] 5.5 Reconcile UI state (unread divider, unread count, top indicators) after successful mark-read.

## 6. Validation and Regression Coverage

- [x] 6.1 Unit test room-open anchor computation and fallback to latest when unread is zero.
- [x] 6.2 Unit/integration test unread divider rendering for in-window and out-of-window boundary cases.
- [x] 6.3 Integration test bidirectional loading (scroll up older, scroll down newer).
- [x] 6.4 Integration test top indicators for far-from-latest and incremental realtime arrivals.
- [x] 6.5 Integration test mark-read triggers, dedupe guards, and backend-state convergence.
- [ ] 6.6 Run frontend test suite and targeted manual two-user realtime verification.
