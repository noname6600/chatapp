## MODIFIED Requirements

### Requirement: Room unread state updates in real time
The system SHALL update room-level unread indicators immediately when new message events are received, excluding sender-side increments and preserving unseen-incoming counts when user is away from latest.

#### Scenario: Recipient unread badge increments on incoming message
- **WHEN** a new message event is received for a room where current user is not the sender
- **THEN** room unread count increments and message-view unread indicators update in the same render cycle

#### Scenario: Sender unread badge does not increment for own message
- **WHEN** a new message event is received where event sender matches current user
- **THEN** unread count for that user SHALL NOT increase for that event

#### Scenario: Incoming while away from latest increments top new-message indicator
- **WHEN** user is viewing older context and new non-sender messages arrive
- **THEN** top incremental indicator count increases to reflect unseen incoming messages

### Requirement: Refresh rehydrates unread state from backend truth
The system SHALL reconcile unread UI state from backend room snapshot after refresh/reconnect and after mark-read acknowledgements.

#### Scenario: Unread restored after browser refresh
- **WHEN** user refreshes page and room list is loaded
- **THEN** unread counts are populated from backend response and replace stale in-memory values

#### Scenario: Snapshot reconciliation resolves websocket drift
- **WHEN** in-memory unread differs from backend snapshot during reload
- **THEN** frontend state converges to backend snapshot values deterministically

#### Scenario: Post-mark-read snapshot clears stale indicators
- **WHEN** mark-read succeeds and snapshot is reloaded
- **THEN** unread badge and top incremental indicators are reset according to backend state
