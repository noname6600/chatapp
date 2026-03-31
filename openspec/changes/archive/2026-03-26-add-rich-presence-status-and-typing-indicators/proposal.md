## Why

The current presence flow only models binary online or offline state and basic room typing events. That is not enough to support friend and room presence surfaces, user-selectable statuses such as away, or consistent automatic presence transitions based on real activity.

Users need a richer presence model that shows whether someone is online, away, or offline across the friend list, room member lists, and global presence snapshots. They also need a clearer typing indicator in each room that excludes the current user and renders a readable summary above the message input.

## What Changes

- Add rich presence statuses for users, including online, away, and offline, with both automatic state derivation and manual override support.
- Extend the presence service API and websocket payloads so the frontend can fetch and react to global presence, per-room presence, and user status updates using one consistent contract.
- Add frontend presence APIs and store support so a user can explicitly set their own status when allowed and otherwise fall back to automatically derived status.
- Surface user presence in friend views and room member views using the richer status model instead of binary online-only checks.
- Add room typing indicator rules that display other users typing above the chat input, show one name when one person is typing, summarize when several people are typing, and never show the current user as typing.

## Capabilities

### New Capabilities
- `rich-presence-status`: Rich global and room-scoped presence states, manual status selection, automatic away detection, and frontend/backend synchronization for friend and room presence surfaces.
- `room-typing-indicators`: Room typing indicator rules for websocket events, self-filtering, summary text, and bottom-of-chat placement above the composer.

### Modified Capabilities
- None.

## Impact

- Backend: presence-service status model, REST endpoints, websocket commands and events, Redis presence data, and room/global snapshot broadcasting.
- Frontend: presence API client, websocket handling, presence store shape, friend list rows, room member list status rendering, and chat-room typing indicator placement.
- UX: users can see richer availability states, understand who is active in a room, and read concise typing feedback without the indicator overlapping the input.
- Testing: backend coverage for status transitions and snapshot payloads, plus frontend coverage for status rendering, self-filtered typing summaries, and display thresholds.