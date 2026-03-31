## ADDED Requirements

### Requirement: Mention notifications SHALL target only explicitly mentioned users
The system SHALL emit and deliver mention-notification events only to users explicitly listed in mentionedUserIds for a message.

#### Scenario: Mention event emitted for mentioned user
- **WHEN** a message contains one or more valid mention targets
- **THEN** the system emits mention-notification events for each explicitly mentioned user

#### Scenario: Non-mentioned user receives no mention event
- **WHEN** a message is sent without including a given user in mentionedUserIds
- **THEN** that user MUST NOT receive mention-notification events for that message

### Requirement: Mention notification persistence SHALL reflect targeted fanout
Persisted mention-notification records SHALL exist only for explicitly targeted mentioned users.

#### Scenario: Mention records created only for targeted users
- **WHEN** notification records are written for a message containing mentions
- **THEN** persisted mention-notification entries exist only for users in mentionedUserIds

#### Scenario: Message notification remains separate from mention notification
- **WHEN** a room member is not mentioned in message content
- **THEN** they may receive standard message notifications but MUST NOT receive mention-notification records
