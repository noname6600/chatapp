## Why

Users sometimes see an optimistic message in the chat UI that appears sent, but after refresh the message is missing from server history. This creates data trust issues and confusion because the UI currently treats local optimistic state as success too early.

## What Changes

- Tighten send lifecycle so optimistic messages are only marked delivered after explicit server confirmation tied to `clientMessageId`.
- Add deterministic failure handling for optimistic messages when send fails or confirmation does not arrive within a bounded window.
- Add safe retry behavior that reuses the same idempotency key for failed optimistic messages.
- Add reconciliation rules on history refresh (`latest`) to remove or mark stale optimistic entries that were never persisted.
- Improve observability around send failure/reconciliation paths to support debugging intermittent non-persisted sends.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `message-sending`: Require explicit delivery-state transitions (`pending`, `sent`, `failed`) and user-visible failure/retry behavior for non-persisted sends.
- `client-message-id-flow`: Strengthen optimistic reconciliation rules so local placeholders cannot remain in a false-success state when no persisted message exists.

## Impact

- Frontend chat send flow: composer submit handler, optimistic message insertion/update logic, and send error handling.
- Frontend message store: delivery state model, retry action, and latest-history reconciliation logic.
- Existing REST/WS send flow usage and response handling with `clientMessageId` correlation.
- Tests: send-flow unit/integration tests for timeout, failure, retry, and refresh reconciliation cases.
