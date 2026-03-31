## MODIFIED Requirements

### Requirement: Room unread state updates in real time
The system SHALL update room-level unread indicators immediately when new message events are received, without requiring a page refresh, except for rooms that the current user has explicitly muted.

#### Scenario: Recipient unread badge increments on incoming message
- **WHEN** a new message event is received for a room where current user is not the sender AND the room is not muted by the current user
- **THEN** that room unread count increments in the room list and message-view unread indicator updates in the same render cycle

#### Scenario: Muted room does not increment unread badge or notification bell
- **WHEN** a new message event is received for a room that the current user has muted
- **THEN** the room unread count does NOT increment and the notification bell badge does NOT increment for that event

#### Scenario: Sender unread badge does not increment for own message
- **WHEN** a new message event is received where event sender matches current user
- **THEN** unread count for that user SHALL NOT increase for that event

#### Scenario: Edited/deleted events do not corrupt unread count
- **WHEN** message edited or deleted events are received
- **THEN** room unread count remains logically consistent and does not increment unexpectedly
