## ADDED Requirements

### Requirement: History API responses SHALL exclude soft-deleted messages
The system SHALL ensure that all backend history queries used to build room message windows exclude messages with `deleted = true`, so that soft-deleted messages are never returned to any client in any hydration path.

#### Scenario: Deleted message does not appear after page refresh
- **WHEN** a user deletes a message and then refreshes the page or re-enters the room
- **THEN** the deleted message is not visible in the message list

#### Scenario: Latest-window query excludes deleted messages
- **WHEN** the `findLatestByRoom` query runs for a room
- **THEN** rows with `deleted = true` are excluded from the result set

#### Scenario: Older-page query excludes deleted messages
- **WHEN** the `findBeforeSeq` query runs to load older messages
- **THEN** rows with `deleted = true` are excluded from the result set

#### Scenario: Range query excludes deleted messages
- **WHEN** the `findRange` query runs to build a message window around a target sequence
- **THEN** rows with `deleted = true` are excluded from the result set

#### Scenario: Last-message-per-room query excludes deleted messages
- **WHEN** `findLastMessages` runs to compute the latest visible message for a set of rooms
- **THEN** the query skips deleted messages and returns the latest non-deleted message per room
