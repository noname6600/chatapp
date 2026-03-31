## ADDED Requirements

### Requirement: Messages are grouped by sender and time proximity
The system SHALL group consecutive messages from the same sender within a 2-minute window, displaying them visually as a single unit.

#### Scenario: Messages from same sender within 2 minutes are grouped
- **WHEN** two messages from user A are sent within 2 minutes of each other in chronological order
- **THEN** they appear in the same group, with sender information shown only at the group start

#### Scenario: Different senders create separate groups
- **WHEN** a message from user B is sent after a message from user A
- **THEN** user B's message starts a new group, even if sent within 2 minutes of user A's message

#### Scenario: Time gap creates new group
- **WHEN** user A sends a message, then more than 2 minutes pass, then user A sends another message
- **THEN** the second message starts a new group despite being from the same sender

#### Scenario: Attachments are not grouped
- **WHEN** an attachment message is sent by user A
- **THEN** it is treated as its own group regardless of time or sender, never grouped with text messages

#### Scenario: Grouping works correctly with chronological ordering
- **WHEN** messages are displayed in ascending chronological order and grouped by sender/time
- **THEN** groups appear in chronological order from top to bottom; neither grouping nor ordering interferes with the other
