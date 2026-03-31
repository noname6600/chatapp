## Why

Message grouping behavior differs between live sending and post-refresh views. Users see inconsistent grouping state, which causes confusion and reduces trust in the chat timeline.

## What Changes

- Unify live and refresh grouping outcomes so the same message sequence renders identically in both cases.
- Define deterministic handling for optimistic messages and server-confirmed replacements.
- Prevent duplicate messages during optimistic-to-real reconciliation while preserving grouping correctness.
- Ensure grouping decisions are based on stable message attributes and ordering rules.

## Capabilities

### New Capabilities
- `live-message-grouping-parity`: Ensure message grouping behavior is consistent between immediate send flow and refreshed/history-loaded flow.

### Modified Capabilities
- None.

## Impact

- Affected frontend components: chat input send path, message list grouping logic.
- Affected state management: chat message reconciliation in Zustand store.
- Affected real-time behavior: WebSocket message receive path and optimistic replacement flow.
- No backend API contract changes required.
