# self-message-unread-exclusion Specification

## Purpose
Define how the unread count calculation handles messages sent by the current user, ensuring unread badges and behind-latest indicators only reflect messages from other participants that the current user has not yet read.

## ADDED Requirements

### Requirement: Exclude current user's messages from unread count
The system SHALL exclude messages sent by the current user (`message.senderId === currentUserId`) from all unread count calculations, ensuring unread indicators only show messages from other participants.

#### Scenario: User sends message, own message does not count as unread
- **WHEN** the current user sends a message (optimistic or confirmed)
- **THEN** the unread count remains unchanged; the user's own message is not included in unread total

#### Scenario: User receives message from another, unread count increments
- **WHEN** another user sends a message and it arrives via WebSocket
- **THEN** unread count increments by 1 if `unreadCount > 0`

#### Scenario: Mixed message arrival (user + other), only other counts
- **WHEN** message list receives both a self-sent message (confirmed) and a message from another user
- **THEN** unread count increments by 1 (only the other user's message contributes)

#### Scenario: Unread count recalculated on room entry, self messages excluded
- **WHEN** user opens a room and unread count is fetched from backend
- **THEN** backend value (`lastReadSeq`, `latestSeq`) does not include self-messages in unread calculation

### Requirement: Behind-latest indicator excludes self messages
The system SHALL exclude the current user's messages when calculating the "behind-latest" count (messages newer than last-read position), preventing false "X new messages" indicators for the user's own outgoing messages.

#### Scenario: User sends message while at bottom, no behind-latest spike
- **WHEN** user is at latest message position and sends a message
- **THEN** the behind-latest count remains 0; the user's sent message does not create an unread indicator

#### Scenario: User reads up, other sends message, behind-latest increments correctly
- **WHEN** user scrolls up, then another user sends a message
- **THEN** behind-latest count increments to 1 (reflecting the new message from other user)

#### Scenario: User reads up, user sends message, behind-latest unchanged
- **WHEN** user scrolls up to read old messages, then sends a reply
- **THEN** behind-latest count does not increment for the user's own message

### Requirement: Unread state reflected in UI only for others' messages
UI components showing unread messages or "behind-latest" indicators SHALL display counts that exclude the current user's messages.

#### Scenario: Unread banner shows count excluding self messages
- **WHEN** user has 5 unread messages total, but 2 are from themselves (impossible but for clarity: if it happened, the banner should show only truly unread messages)
- **THEN** the unread banner and count reflect only messages from other users

#### Scenario: Notification list filters out self-message notifications
- **WHEN** a notification would be created for a message sent by the current user
- **THEN** no notification is created; the user does not see a bell alert for their own messages

#### Scenario: Room preview does not show unread for latest self-message
- **WHEN** the latest message in a room is from the current user
- **THEN** the room list item shows `unreadCount: 0` even if other unread messages exist below the latest

### Requirement: Deterministic unread count after realtime updates
After a batch of realtime MESSAGE_SENT events (including self-messages), the frontend unread count SHALL match the server's calculation, excluding self-messages.

#### Scenario: Realtime MESSAGE_SENT from self, count remains stable
- **WHEN** MESSAGE_SENT event arrives for current user's message
- **THEN** realtime handler does not increment unread; count remains unchanged

#### Scenario: Realtime MESSAGE_SENT from other, count increments
- **WHEN** MESSAGE_SENT event arrives for another user's message and `unreadCount > 0`
- **THEN** realtime handler increments unread by 1
