## Why

The current add-friend flow relies on broader realtime behavior that is noisy and does not match the desired UX. The product now needs a simpler interaction: notification badge updates for friend request events and clearer recommendation card status changes.

## What Changes

- Limit add-friend realtime behavior to friend request notification signals only.
- Show notification badge updates when a user receives a friend request.
- Show notification feedback when a user sends a friend request.
- Update recommendation status in the add-friend page so each recommended user reflects current request/friend state consistently.
- Remove unnecessary realtime recommendation list mutation behavior that is not required for this scope.

## Capabilities

### New Capabilities
- `friend-request-notification-badge`: Define notification badge behavior for incoming and outgoing friend request events.
- `add-friend-recommendation-status`: Define recommendation item status transitions after send request and after friendship state changes.

### Modified Capabilities
- None.

## Impact

- Affected backend modules: friendship-service event publishing and notification payload scope.
- Affected frontend modules: add-friend page state handling, notification badge state, recommendation card status rendering.
- API/WebSocket contracts may be narrowed to friend-request-focused events for this feature path.
