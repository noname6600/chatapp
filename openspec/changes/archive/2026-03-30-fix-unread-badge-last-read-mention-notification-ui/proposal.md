## Why

Unread and latest-jump indicators are currently inconsistent with last-read state, causing incorrect badge counts and extreme "messages behind latest" values. Mention notifications and the notification surface are also noisy and visually unstable, including overlap issues with the room list layout.

## What Changes

- Align unread badge, last-read anchor, and newest-jump calculations so counts and markers are consistent when entering and reading rooms.
- Restrict mention notifications to actual mentioned targets and prevent non-mentioned users from receiving mention-specific notification events.
- Tighten notification rendering and layout behavior so notification UI does not overlap or break behind the room list container.
- Normalize room list and in-room unread indicator updates during realtime events to avoid drift between server state and client badges.

## Capabilities

### New Capabilities
- `mention-targeted-notification-delivery`: Defines mention notification fanout rules so only explicitly mentioned users receive mention notification events.

### Modified Capabilities
- `room-unread-realtime-sync`: Clarify and enforce unread count reconciliation with last-read state while users are in-room and during incoming realtime events.
- `message-unread-indicator`: Update requirements for newest-jump badge/count correctness and display stability.
- `unread-boundary-recovery`: Tighten rules for last-read boundary placement and recovery when message windows shift.
- `notification-realtime-sync`: Update notification UI behavior and event handling so notification panes and badges remain synchronized and do not visually conflict with room list layout.

## Impact

- Frontend: unread badge and newest-jump logic in chat message list and room list; notification panel layout and z-index/positioning; mention notification rendering.
- Backend: mention notification targeting and event fanout validation in notification/message pipelines.
- Realtime: websocket event handling for unread and notification updates.
- Testing: unit/integration/manual coverage for badge correctness, mention targeting, and layout stability.
