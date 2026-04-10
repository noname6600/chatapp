## ADDED Requirements

### Requirement: Users SHALL be able to mute or unmute group chat rooms
Each user SHALL have a per-room mute preference for group rooms, stored server-side, that suppresses notification toasts and badge increments without deleting or hiding notification records.

#### Scenario: User mutes a group room
- **WHEN** user toggles mute ON for a group room via the room settings panel
- **THEN** client sends `POST /api/v1/rooms/{roomId}/mute` and the mute state is persisted server-side; client store updates immediately (optimistic)

#### Scenario: User unmutes a group room
- **WHEN** user toggles mute OFF for a group room
- **THEN** client sends `DELETE /api/v1/rooms/{roomId}/mute` and mute record is removed; client store updates immediately (optimistic)

#### Scenario: Muted room suppresses notification toast
- **WHEN** a new message notification arrives for a muted room
- **THEN** no notification toast is shown and the notification bell badge count is NOT incremented for that event

#### Scenario: Muted room notification is still persisted
- **WHEN** a new message notification arrives for a muted room
- **THEN** the notification record IS still persisted in the backend and visible in the notification inbox

#### Scenario: Muted room unread count is not incremented in room list
- **WHEN** a new message arrives for a muted room
- **THEN** the room's unread badge in the room list is NOT incremented for the current user

#### Scenario: Mute state is loaded on room activation
- **WHEN** user opens or switches to a room
- **THEN** the mute state for that room is read from the server or local cache and applied to the client store

#### Scenario: Mute setting is scoped to the authenticated user
- **WHEN** user A mutes room X
- **THEN** user B in the same room is unaffected and still receives notifications and badge increments for room X

### Requirement: Mute toggle is only available for group rooms
The mute setting SHALL be exposed only in group room settings; private (1-1) chat rooms SHALL NOT display the mute toggle.

#### Scenario: Mute toggle not shown for private chat
- **WHEN** user opens the settings panel for a private (1-1) chat room
- **THEN** no mute toggle control is visible
