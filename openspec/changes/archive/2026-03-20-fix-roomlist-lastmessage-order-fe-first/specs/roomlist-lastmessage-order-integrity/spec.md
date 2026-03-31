## ADDED Requirements

### Requirement: Room list keeps split layout
The frontend MUST preserve the existing split room-list layout: group rooms are displayed in the left avatar rail and private conversations are displayed in the right panel.

#### Scenario: Render split layout
- **WHEN** the room list is rendered
- **THEN** group rooms are shown only in the left section and private rooms are shown only in the right section

### Requirement: LastMessagePreview API response includes senderName
The backend MUST include the `senderName` field in the `LastMessagePreview` DTO so that the `/rooms/my` response carries sender display name for the latest message.

#### Scenario: API response includes senderName when a last message exists
- **WHEN** the rooms endpoint returns a room with a last message
- **THEN** the `lastMessage.senderName` field in the response is populated from the stored sender display name

#### Scenario: senderName is empty string when no sender name was stored
- **WHEN** the rooms endpoint returns a room whose last message sender name was not stored
- **THEN** the `lastMessage.senderName` field is an empty string, not absent

### Requirement: Private room preview includes latest sender context
The frontend MUST display sender context, latest message content, avatar, and name for a private room's last-message preview whenever metadata is present in the room list API payload or realtime update payload.

#### Scenario: Sender metadata available in initial room list
- **WHEN** the room list loads and a private room contains a last message with sender display metadata
- **THEN** the room preview shows the sender context for that latest message

#### Scenario: Sender metadata available in realtime update
- **WHEN** a realtime message update arrives for a private room with sender display metadata
- **THEN** the room preview updates to show that sender context as the latest message sender

#### Scenario: Sender metadata missing for private-room latest message in realtime event
- **WHEN** a realtime WS event arrives for a private room and the event does not carry senderName
- **THEN** the frontend applies a deterministic fallback sender label and records a contract-gap warning for diagnostics

### Requirement: Room list ordering is determined by latest activity
The frontend MUST order private-room list items in the right panel by descending latest activity timestamp using deterministic tie-breaking when timestamps are equal or missing.

#### Scenario: Private panel is ordered by recency
- **WHEN** the frontend derives private-room previews with different activity timestamps
- **THEN** private rooms are rendered in descending activity order in the right panel

#### Scenario: Realtime update changes room recency
- **WHEN** a realtime update introduces a newer activity timestamp for a room
- **THEN** that room is repositioned according to descending activity order on the next render

#### Scenario: Equal or missing activity timestamps
- **WHEN** two or more rooms have equal or missing primary activity timestamps
- **THEN** the frontend uses deterministic tie-break fields so ordering is stable across renders