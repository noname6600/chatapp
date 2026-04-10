## MODIFIED Requirements

### Requirement: Users SHALL be able to mute or unmute group chat rooms
Each user SHALL have a per-room mute preference for group rooms, stored server-side, that suppresses notification toasts and badge increments without deleting or hiding notification records.

#### Scenario: User loads mute state for a group room
- **WHEN** user opens or switches to a group room and the client loads room notification settings
- **THEN** client sends `GET /api/v1/rooms/{roomId}/settings`
- **AND** the authenticated user's mute state is returned successfully for that room

#### Scenario: User mutes a group room
- **WHEN** user toggles mute ON for a group room via the room settings panel
- **THEN** client sends `POST /api/v1/rooms/{roomId}/mute`
- **AND** the mute state is persisted server-side
- **AND** client store updates immediately (optimistic)

#### Scenario: User unmutes a group room
- **WHEN** user toggles mute OFF for a group room
- **THEN** client sends `DELETE /api/v1/rooms/{roomId}/mute`
- **AND** mute record is removed
- **AND** client store updates immediately (optimistic)

#### Scenario: Muted room suppresses notification toast
- **WHEN** a new message notification arrives for a muted room
- **THEN** no notification toast is shown
- **AND** the notification bell badge count is NOT incremented for that event

#### Scenario: Muted room notification is still persisted
- **WHEN** a new message notification arrives for a muted room
- **THEN** the notification record IS still persisted in the backend and visible in the notification inbox

#### Scenario: Muted room unread count is not incremented in room list
- **WHEN** a new message arrives for a muted room
- **THEN** the room's unread badge in the room list is NOT incremented for the current user

#### Scenario: Mute state is loaded on room activation
- **WHEN** user opens or switches to a room
- **THEN** the mute state for that room is read from the canonical room settings endpoint and applied to the client store

#### Scenario: Mute setting is scoped to the authenticated user
- **WHEN** user A mutes room X
- **THEN** user B in the same room is unaffected and still receives notifications and badge increments for room X