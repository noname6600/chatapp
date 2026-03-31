# message-refresh-consistency Specification

## Purpose
Defines guarantees that all persisted messages remain visible after room activation and browser refresh for every participant, and that sequence discontinuities trigger deterministic recovery.

## Requirements

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
