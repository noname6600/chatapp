## 1. Reply Action Recovery

- [x] 1.1 Reconnect `MessageItem` reply action to shared reply state so selecting Reply sets active reply target.
- [x] 1.2 Ensure message composer renders reply preview from shared reply state and supports clear/cancel paths.
- [x] 1.3 Ensure send flow includes reply linkage metadata when active reply target exists and clears reply state on successful send.

## 2. Delete Action Recovery

- [x] 2.1 Reconnect `MessageItem` delete action to delete-intent state and confirmation dialog flow.
- [x] 2.2 Ensure confirm-delete path removes the selected message from state/rendered list.
- [x] 2.3 Ensure cancel-delete path clears pending delete target without mutating message state.

## 3. Boundary-Safe Integration and Validation

- [x] 3.1 Preserve item-scoped prop contract for `MessageItem` while keeping list-wide derivations in `MessageList`.
- [x] 3.2 Add/update tests for reply/delete action behavior across item click, state propagation, and UI confirmation/preview rendering.
- [x] 3.3 Run frontend tests/build for reply/delete regression coverage and resolve any failures.
