## Why

The reaction toggle flow currently carries roomId in backend context and related processing paths even though messageId already uniquely identifies the message and its room relationship in persistence. Keeping redundant roomId increases coupling and creates avoidable inconsistency risk when values drift.

## What Changes

- Remove roomId from reaction toggle backend context and all dependent reaction-toggle processing paths
- Refactor pipeline steps, payload construction, and validation logic to derive required room linkage from messageId and persisted message records
- Update related tests and contracts to ensure reaction toggle behavior remains correct for add/remove flows
- Preserve realtime reaction publishing behavior and channel routing based on authoritative message data

## Capabilities

### New Capabilities
- `reaction-toggle-without-room-id`: Reaction toggle backend flow operates without roomId in request context, deriving room association from message identity and persisted state.

### Modified Capabilities
<!-- No existing capability requirements are changing -->

## Impact

- Affects backend reaction pipeline context object and associated pipeline steps in chat-service
- Affects event publishing code paths that currently rely on roomId from context
- Requires updates to unit/integration tests around reaction toggle and event publishing
- No frontend API contract change required if external endpoint shape remains stable
