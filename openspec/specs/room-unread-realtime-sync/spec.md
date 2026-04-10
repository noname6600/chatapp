# room-unread-realtime-sync Specification

## Purpose
Define deterministic realtime unread-count behavior in room state, including sender exclusion, non-sender increments, and refresh/reconnect reconciliation against backend room snapshots.
## Requirements
### Requirement: Room unread state updates in real time
The system SHALL update room-level unread indicators immediately when new message events are received, while keeping displayed unread and "messages behind latest" values consistent with backend last-read state, except for rooms that the current user has explicitly muted.

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

### Requirement: Refresh rehydrates unread state from backend truth
The system SHALL reconcile unread UI state from backend room snapshot after refresh/reconnect.

#### Scenario: Unread restored after browser refresh
- **WHEN** user refreshes page and room list is loaded
- **THEN** unread counts are populated from backend response and replace stale in-memory values

#### Scenario: Snapshot reconciliation resolves websocket drift
- **WHEN** in-memory unread differs from backend snapshot during reload
- **THEN** frontend state converges to backend snapshot values deterministically

#### Scenario: Post-mark-read snapshot clears stale indicators
- **WHEN** mark-read succeeds and snapshot is reloaded
- **THEN** unread badge and top incremental indicators are reset according to backend state

