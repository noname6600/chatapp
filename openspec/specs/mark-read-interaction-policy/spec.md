## ADDED Requirements

### Requirement: Mark-read is interaction-driven
The client SHALL mark messages as read only after meaningful interaction with unread content, not immediately on room open.

#### Scenario: Crossing unread boundary triggers mark-read
- **WHEN** viewport crosses from read region into unread region during user scroll
- **THEN** client sends mark-read request for the room

#### Scenario: Jump-to-latest triggers mark-read
- **WHEN** user activates jump-to-latest and lands at live tail
- **THEN** client sends mark-read request for the room

### Requirement: Mark-read requests are idempotent per room-view cycle
The client SHALL suppress duplicate mark-read requests while a room mark-read request is in-flight or already acknowledged for the same view cycle.

#### Scenario: Repeated triggers do not duplicate calls
- **WHEN** user triggers multiple mark-read conditions rapidly in one room-view cycle
- **THEN** only one effective mark-read request is emitted until state changes

#### Scenario: In-flight mark-read blocks additional requests
- **WHEN** mark-read request is currently in-flight
- **THEN** subsequent trigger events are ignored until request resolves

### Requirement: Read-state reconciliation uses backend truth
After mark-read and reconnect/refresh events, the client SHALL replace local unread indicators using backend snapshot values.

#### Scenario: Successful mark-read converges unread indicators
- **WHEN** mark-read call succeeds and room snapshot is refreshed
- **THEN** unread badge, unread divider, and top indicators converge to backend read state