## Why

Message reactions are not fully synchronized in realtime and users cannot clearly see whether they have reacted, which causes inconsistent chat UX across clients. Completing the reaction flow now will make reactions feel instant, reliable, and intuitive with clear toggle behavior.

## What Changes

- Deliver end-to-end realtime reaction updates for add and remove actions across all participants in a room
- Ensure reaction toggle semantics are consistent: reacting with the same emoji again removes the user reaction
- Highlight reactions that include the current user so users can quickly identify their own interaction state
- Normalize reaction update handling so UI state remains correct during rapid repeated toggles and multi-user updates

## Capabilities

### New Capabilities
- `realtime-reaction-toggle-highlight`: Provide realtime, toggleable emoji reactions with self-highlight state and consistent synchronization across message lists and clients.

### Modified Capabilities
<!-- No existing capability requirements are changing -->

## Impact

- Affects frontend reaction rendering and message state updates in chat message components and stores
- Affects websocket/realtime event handling for reaction updates
- Uses existing backend reaction events and message identifiers; no breaking API contract changes expected
- May require additional frontend tests for rapid toggle race conditions and cross-client consistency
