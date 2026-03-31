## Why

Notifications are currently not delivered in real time for active users, so new notifications are only visible after a manual page refresh. This breaks expected chat UX and causes users to miss timely updates.

## What Changes

- Fix frontend realtime notification subscription/handling so incoming notification events are processed immediately without refresh.
- Ensure notification unread badge and panel list update as soon as a new server event arrives.
- Ensure reconnection behavior restores notification stream handling after temporary disconnects.
- Add targeted automated tests for realtime notification event handling and reconnect continuity.

## Capabilities

### New Capabilities
- `notification-realtime-sync`: Keep notification state synchronized live from websocket events, including reconnect recovery without page refresh.

### Modified Capabilities
- `room-unread-realtime-sync`: Align room/bell unread behavior so realtime notification events update counts consistently without requiring manual reload.

## Impact

- Frontend notification state management and websocket event wiring.
- Notification UI surfaces (bell badge and list panel).
- Realtime event integration paths and tests in chatappFE.
- No new external dependencies expected.
