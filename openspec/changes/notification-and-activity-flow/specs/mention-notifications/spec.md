## ADDED Requirements

### Requirement: @mention tokens in messages SHALL generate MENTION notifications for each mentioned user
The system SHALL detect `@username` tokens in a message body during the send flow, validate that each mentioned user is a member of the room, and generate a MENTION-type notification for each valid mention.

#### Scenario: Client parses @mention tokens before send
- **WHEN** user types a message containing `@username` and presses send
- **THEN** the client resolves each `@username` to a userId and includes `mentionedUserIds[]` in the message create request

#### Scenario: Chat-service validates mentioned users are room members
- **WHEN** chat-service receives a message create request with `mentionedUserIds`
- **THEN** only userIds that are confirmed room members are included in the enriched `MessageCreatedEvent`; invalid IDs are silently dropped

#### Scenario: Notification-service creates MENTION notification per mentioned user
- **WHEN** notification-service consumes a `MessageCreatedEvent` with non-empty `mentionedUserIds`
- **THEN** one `Notification` record with type `MENTION` and `referenceId = messageId` is persisted for each mentioned user

#### Scenario: MENTION notification replaces MESSAGE notification for mentioned user
- **WHEN** a user is both a room member and mentioned in the same message
- **THEN** only a MENTION notification is created for that user, not an additional MESSAGE notification

#### Scenario: Mention notification is received in real-time over WebSocket
- **WHEN** a MENTION notification is persisted
- **THEN** a `NOTIFICATION_NEW` WebSocket event with `type = MENTION` and a deep-link `roomId` + `messageId` is pushed to the mentioned user's queue

#### Scenario: Autocomplete shows matching room members when @ is typed
- **WHEN** user types `@` followed by at least one character in the message input
- **THEN** a dropdown appears listing room members whose display name starts with the typed characters (max 5 results)

#### Scenario: Selecting an autocomplete suggestion inserts the mention token
- **WHEN** user selects a member from the @mention autocomplete dropdown
- **THEN** the typed `@partial` is replaced with `@username` and the userId is tracked internally for the send payload
