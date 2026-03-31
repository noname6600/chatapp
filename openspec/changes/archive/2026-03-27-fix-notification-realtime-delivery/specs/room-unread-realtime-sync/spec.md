## MODIFIED Requirements

### Requirement: Room unread state updates in real time
The system SHALL update room-level unread indicators immediately when new realtime events are received, without requiring a page refresh, and SHALL keep room unread and notification bell updates behaviorally consistent for the same event.

#### Scenario: Recipient unread badge increments on incoming message
- **WHEN** a new message event is received for a room where current user is not the sender
- **THEN** that room unread count increments in the room list and message-view unread indicator updates in the same render cycle

#### Scenario: Sender unread badge does not increment for own message
- **WHEN** a new message event is received where event sender matches current user
- **THEN** unread count for that user SHALL NOT increase for that event

#### Scenario: Edited/deleted events do not corrupt unread count
- **WHEN** message edited or deleted events are received
- **THEN** room unread count remains logically consistent and does not increment unexpectedly

#### Scenario: Bell and room unread remain aligned for realtime events
- **WHEN** a realtime event changes unread state for a non-muted room
- **THEN** room unread and notification bell state transition is applied without requiring manual refresh and without contradictory counts caused by stale client state
