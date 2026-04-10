## Why

Inviting members from a room currently lacks a guided flow that combines join-code sharing, member context, and a one-click in-chat invite card. A dedicated popup improves invite UX and reduces friction for inviting users with clear copy/send actions.

## What Changes

- Add a room invite popup opened from the room "more" menu.
- Show room join code in the popup with a copy action.
- Show a searchable list of users with profile context (including about text) and @mention-ready identity cues.
- Add an invite action per listed user that sends a room invite card message into the current room.
- Ensure invite-card send path carries room context required for recipients to join from the card.

## Capabilities

### New Capabilities
- `room-invite-popup-and-card-send`: Popup-based room invite flow that supports join-code copy, mention-friendly member discovery, and sending invite cards into the room.

### Modified Capabilities
- `message-sending`: Extend message send behavior to include room invite card payload and delivery semantics.

## Impact

- Frontend chat room UI: room header/menu action, popup modal layout, user list/selection, copy UX, invite button actions.
- Frontend state + API/websocket: loading candidate users, issuing invite-card send requests, optimistic/pending message rendering.
- Backend chat-service message flow: validation and persistence of invite-card message type/content metadata.
- Existing chat rendering paths: invite card display in message list and click-to-join compatibility.
