## MODIFIED Requirements

### Requirement: Room unread state updates in real time
The system SHALL update room-level unread indicators immediately when new message events are received, while keeping displayed unread and "messages behind latest" values consistent with backend last-read state, except for rooms that the current user has explicitly muted, and SHALL keep room list preview state synchronized in the same realtime flow.

#### Scenario: Recipient unread badge increments on incoming message
- **WHEN** a new message event is received for a room where current user is not the sender
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

#### Scenario: Bell and room unread remain aligned for realtime events
- **WHEN** a realtime event changes unread state for a non-muted room
- **THEN** room unread and notification bell state transition is applied without requiring manual refresh and without contradictory counts caused by stale client state

#### Scenario: In-room read state suppresses stale behind-latest spikes
- **WHEN** user is currently in the room and last-read is at or near latest sequence
- **THEN** behind-latest display is derived from bounded sequence math and MUST NOT show invalid extreme values caused by stale counters

#### Scenario: Incoming while away from latest increments top new-message indicator
- **WHEN** user is viewing older context and new non-sender messages arrive
- **THEN** top incremental indicator count increases to reflect unseen incoming messages

#### Scenario: Unread and preview stay synchronized for background rooms
- **WHEN** a non-muted incoming message is received for a background room
- **THEN** unread increment and room preview update are applied together in the same realtime update path
- **AND** the room list does not require page refresh to reflect either unread or preview changes
