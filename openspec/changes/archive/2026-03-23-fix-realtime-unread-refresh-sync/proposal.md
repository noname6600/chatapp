## Why

Unread behavior regressed: room/message unread indicators are no longer updating in real time and become stale after page refresh. This causes users to miss new messages or see incorrect unread state, especially for senders and recipients in active rooms.

## What Changes

- Restore real-time unread state updates on incoming room events so UI updates immediately without manual refresh.
- Ensure unread state is rehydrated correctly on refresh and reconciles with backend truth (`lastReadSeq`, `lastSeq`, `unreadCount`).
- Prevent sender-side false unread increments for messages sent by the current user.
- Re-establish unread boundary behavior in message list (new-message divider, banner visibility, jump behavior) using backend-driven state.
- Add reconciliation logic for race conditions between websocket events and room reloads.

## Capabilities

### New Capabilities
- `room-unread-realtime-sync`: Keep room list unread badges and message-view unread indicators synchronized in real time and after refresh with backend state.
- `unread-boundary-recovery`: Restore backend-based unread boundary display and entry positioning behavior after data reloads.

### Modified Capabilities
- `message-sending`: Update unread semantics so sender does not get unread increments for their own outbound messages while recipients do.

## Impact

Frontend:
- Update room store websocket update logic and refresh reconciliation.
- Update message list unread-boundary rendering and initial positioning behavior after load.
- Add defensive handling for stale room state during reconnect/reload.

Backend:
- No contract-breaking API changes expected; existing `/rooms/my` and `/rooms/{roomId}/read` remain source of truth.

Quality:
- Add regression tests for real-time unread updates, sender exclusion, and refresh consistency.
- Add smoke checks for multi-room and reload scenarios.
