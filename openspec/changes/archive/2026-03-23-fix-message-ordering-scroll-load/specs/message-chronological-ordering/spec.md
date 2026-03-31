## ADDED Requirements

### Requirement: Messages are sorted in chronological order
The system SHALL maintain and display messages in ascending chronological order, with the oldest messages at the top of the message list and newest messages at the bottom.

#### Scenario: Initial load shows oldest to newest
- **WHEN** user opens a room with 5 existing messages (seq 1, 2, 3, 4, 5)
- **THEN** the message list displays them in order from top to bottom: 1, 2, 3, 4, 5

#### Scenario: New message appears below existing messages
- **WHEN** a new message (seq 6) is received via websocket while viewing the room
- **THEN** the new message is appended and appears below message 5, maintaining chronological order

#### Scenario: Messages maintain order after pagination prepend
- **WHEN** older messages are fetched and prepended to the list
- **THEN** all messages remain in ascending order, with newly prepended messages appearing before the previous oldest message

#### Scenario: Sorting handles equal sequences correctly
- **WHEN** multiple messages have the same sequence number (edge case)
- **THEN** system sorts by secondary key (e.g., createdAt or messageId) to ensure deterministic order
