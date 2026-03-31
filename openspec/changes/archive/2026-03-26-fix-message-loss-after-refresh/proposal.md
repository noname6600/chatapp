## Why

In a multi-user send/join flow, users can see an incomplete room window and, after refresh, both clients can lose the latest persisted message (seeing 1-9 instead of 1-10). This breaks trust in message durability and creates data consistency confusion in the core chat experience.

## What Changes

- Define deterministic requirements for active-room reconciliation when websocket delivery and REST hydration diverge.
- Require refresh-safe latest-window hydration so persisted messages are never dropped from the client window.
- Require sequence-contiguous recovery behavior when clients detect gaps or reconnect.
- Clarify sender/receiver consistency guarantees for message visibility after send, join, and refresh cycles.

## Capabilities

### New Capabilities
- `message-refresh-consistency`: Guarantees that persisted latest messages remain visible across join, live updates, and browser refresh.

### Modified Capabilities
- `message-sending`: Strengthen delivery/visibility requirements from optimistic send through server-confirmed persistence and post-refresh rendering.
- `pagination-state-integrity`: Ensure latest-window and boundary-window merges never discard newer persisted messages.
- `room-switch-resilient-history-pagination`: Require active-room initialization/reconciliation behavior to be idempotent and stable under rapid updates.

## Impact

- Frontend chat state orchestration and websocket event handling in chat window hydration/reconciliation paths.
- Backend sequence and latest-message retrieval correctness validation for concurrent send/join scenarios.
- Additional regression tests for cross-user visibility and refresh consistency flows.
