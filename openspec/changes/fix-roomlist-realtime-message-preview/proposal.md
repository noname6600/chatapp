## Why

Room list entries are not reflecting newly received messages in real time for background rooms. Users currently need to refresh the page before seeing updated last-message preview text and ordering, which breaks expected chat responsiveness.

## What Changes

- Ensure room list item preview data (last message sender, content summary, timestamp) updates immediately when new messages arrive via websocket.
- Ensure background room entries re-render in place without requiring manual refresh.
- Keep unread badge behavior and room sort behavior consistent with existing real-time rules.
- Add deterministic reconciliation so websocket updates and room snapshot reload do not regress preview state.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `room-list-activity-sort`: extend requirements so websocket-driven room promotion also updates room preview fields in real time.
- `room-unread-realtime-sync`: clarify that room list unread and preview state stay synchronized for incoming realtime events without page refresh.

## Impact

- Frontend room store state update logic for websocket `MESSAGE_SENT` handling.
- Frontend room list item rendering for last message preview data.
- Potentially related tests for room list realtime ordering and preview refresh.
- No new backend APIs expected; leverages existing websocket events and room snapshot endpoints.
