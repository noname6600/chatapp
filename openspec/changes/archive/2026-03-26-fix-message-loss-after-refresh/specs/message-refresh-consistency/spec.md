## ADDED Requirements

### Requirement: Persisted latest messages SHALL remain visible after refresh
The system SHALL ensure that all persisted messages in the room's latest window remain visible after room activation and browser refresh for every participant.

#### Scenario: Sender and receiver converge after refresh
- **WHEN** user A and user B participate in the same room and messages are persisted successfully
- **THEN** both users see the same latest persisted message set after refresh

#### Scenario: Latest persisted message is not dropped by hydration
- **WHEN** latest-page hydration and boundary/range hydration both run for an active room
- **THEN** the merged window preserves the latest persisted message and sequence tail

### Requirement: Sequence discontinuity SHALL trigger deterministic recovery
The system SHALL detect sequence discontinuity in active-room live events and reconcile from authoritative latest history.

#### Scenario: Active-room gap recovery
- **WHEN** a MESSAGE_SENT event arrives with sequence greater than known latest sequence plus one
- **THEN** the client triggers room reconciliation and restores any missing persisted messages

#### Scenario: Reconnect recovery
- **WHEN** websocket connection reopens while a room is active
- **THEN** the client reconciles that room's latest persisted messages without introducing duplicates
