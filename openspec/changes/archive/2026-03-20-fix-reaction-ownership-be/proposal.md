## Why

Reaction state is currently ambiguous when message history is loaded from the backend because reactions are returned as emoji aggregates without per-user ownership. This causes incorrect initial UI state (user reactions not marked as mine), wrong toggle direction on first click, and downstream inconsistency when realtime updates arrive.

## What Changes

- Extend backend message-history reaction payloads to include whether the current authenticated user has reacted to each emoji.
- Keep aggregate counts while adding ownership metadata in a backward-compatible response shape.
- Ensure toggle reaction processing and published reaction updates remain idempotent and consistent with ownership semantics.
- Add backend tests to cover initial load ownership, toggle transitions, and aggregate consistency under repeated toggles.

## Capabilities

### New Capabilities
- `reaction-ownership-in-history`: Message history responses include per-emoji ownership for the current user so clients can initialize `reactedByMe` correctly.
- `reaction-toggle-idempotent-ownership`: Reaction toggles preserve correct counts and ownership state across repeated clicks and event propagation.

### Modified Capabilities
- `realtime-reaction-toggle-highlight`: Requirements updated so realtime/UI highlight correctness depends on backend-provided ownership in initial history payloads.

## Impact

- Affected backend module: chatappBE chat-service message query/mapping/reaction aggregation.
- Affected APIs: message history endpoints that return message reactions.
- Affected response DTOs/mappers: message reaction response model and mapping pipeline.
- Affected tests: query service, mapper tests, and reaction command/integration tests for ownership and idempotency.
- Downstream impact: frontend reaction highlight/toggle logic becomes deterministic from first render.
