## 1. Join Input Case Preservation (Frontend)

- [x] 1.1 Audit join-code input and submit handlers in chatappFE for any toUpperCase/toLowerCase transforms
- [x] 1.2 Remove implicit case normalization while keeping existing validation UX intact
- [x] 1.3 Ensure join request payload uses exact user-entered string
- [x] 1.4 Add inline comments/tests documenting case-preservation rule

## 2. Room-Scoped Code State Isolation (Frontend)

- [x] 2.1 Refactor room-code state to be keyed by roomId (or equivalent room-scoped structure)
- [x] 2.2 Remove/shared global activeCode state paths that can leak across rooms
- [x] 2.3 Update selectors/components to read code by current roomId only
- [x] 2.4 Validate room A/B switching cannot display another room's code

## 3. Stale Response Guarding (Frontend)

- [x] 3.1 Add request-ownership guard (token/snapshot) for room-code fetch operations
- [x] 3.2 Ignore out-of-order responses that do not match the active/requested room context
- [x] 3.3 Preserve current UI state when stale responses are dropped
- [x] 3.4 Add trace logging/debug hook for dropped stale responses (non-blocking)

## 4. Automated Frontend Tests

- [x] 4.1 Add test: mixed-case join input is submitted unchanged
- [x] 4.2 Add test: lowercase input is not auto-uppercased
- [x] 4.3 Add test: room A code never renders in room B after rapid switching
- [x] 4.4 Add test: stale room-code response is ignored

## 5. Verification (Frontend-Only)

- [x] 5.1 Run frontend unit/integration tests for join-code and room-code flows
- [x] 5.2 Run frontend build and typecheck
- [ ] 5.3 Manual smoke test: fetch room code in multiple rooms and switch quickly
- [ ] 5.4 Manual smoke test: join by code preserves exactly typed input
