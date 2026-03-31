## 1. Pagination Trigger Resilience

- [x] 1.1 Add no-overflow auto-prefetch flow in message list so older history can load without manual upward scroll.
- [x] 1.2 Enforce stop conditions for auto-prefetch (max attempts and no-progress detection) to avoid infinite fetch loops.
- [x] 1.3 Preserve scroll-position restoration behavior after prepending older messages during fallback and manual triggers.

## 2. Room Transition Correctness

- [x] 2.1 Ensure room-specific pagination guard state is reset or isolated correctly when switching rooms.
- [x] 2.2 Verify returning to a previously visited room still allows older-message fetching when history remains.

## 3. Diagnostics and Validation

- [x] 3.1 Add explicit pagination diagnostics for trigger, blocked, no-more-history, and empty-page paths.
- [x] 3.2 Extend or add unit tests for no-overflow auto-prefetch and room-switch return regression scenarios.
- [x] 3.3 Run frontend tests/build for changed chat pagination paths and address any failures.
