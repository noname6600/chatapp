## ADDED Requirements

### Requirement: Room unread state updates in real time
The system SHALL update room-level unread indicators immediately when new message events are received, without requiring a page refresh.

#### Scenario: Recipient unread badge increments on incoming message
- **WHEN** a new message event is received for a room where current user is not the sender
- **THEN** that room unread count increments in the room list and message-view unread indicator updates in the same render cycle

#### Scenario: Sender unread badge does not increment for own message
- **WHEN** a new message event is received where event sender matches current user
- **THEN** unread count for that user SHALL NOT increase for that event

#### Scenario: Edited/deleted events do not corrupt unread count
- **WHEN** message edited or deleted events are received
- **THEN** room unread count remains logically consistent and does not increment unexpectedly

### Requirement: Refresh rehydrates unread state from backend truth
The system SHALL reconcile unread UI state from backend room snapshot after refresh/reconnect.

#### Scenario: Unread restored after browser refresh
- **WHEN** user refreshes page and room list is loaded
- **THEN** unread counts are populated from backend response and replace stale in-memory values

#### Scenario: Snapshot reconciliation resolves websocket drift
- **WHEN** in-memory unread differs from backend snapshot during reload
- **THEN** frontend state converges to backend snapshot values deterministically
