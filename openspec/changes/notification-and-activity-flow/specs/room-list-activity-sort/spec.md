## ADDED Requirements

### Requirement: Room list SHALL be ordered by latest message activity descending
The client SHALL display rooms sorted by the timestamp of the most recently sent message (or room creation time if no messages yet), with the most recently active room at the top.

#### Scenario: Initial room list is sorted by latest message timestamp
- **WHEN** the room list loads from `GET /api/v1/rooms`
- **THEN** rooms are displayed in descending order of `latestMessageAt`, with rooms having no messages sorted by `createdAt`

#### Scenario: Room moves to top when a new message arrives
- **WHEN** a `ws_message_sent_event` arrives for any room (active or background)
- **THEN** the affected room is promoted to the top of the room list within 300 ms (debounce threshold)

#### Scenario: Sender's own room also moves to top after send
- **WHEN** the current user sends a message to any room
- **THEN** that room moves to the top of the sorted list immediately

#### Scenario: Sort is stable for rooms with identical timestamps
- **WHEN** two or more rooms share the same `latestMessageAt` value
- **THEN** their relative order is deterministic (e.g., alphabetical by name or by roomId as tiebreaker)
