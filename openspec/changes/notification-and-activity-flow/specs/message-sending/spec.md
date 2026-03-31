## MODIFIED Requirements

### Requirement: Sent messages update unread count for other users in real-time
When a message is successfully sent and broadcast via WebSocket, other users in the same room SHALL see the unread message count increment immediately (if they have the message list open and have not muted the room).

#### Scenario: Unread count increments for recipient when message is sent
- **WHEN** User A sends a message to a room
- **THEN** the NewMessageEvent is broadcast via WebSocket, User B's room store increments unreadCount (if User B has not marked the room read AND has not muted the room)

#### Scenario: Unread count update reaches all connected clients
- **WHEN** a message is sent and broadcast
- **THEN** all WebSocket-connected clients in that room receive the NewMessageEvent and update their local unreadCount (subject to mute rules)

#### Scenario: UI reflects updated unread count immediately
- **WHEN** unreadCount changes due to NewMessageEvent
- **THEN** the unread message banner (if displayed) updates to show the new count without page reload

#### Scenario: Sender and receiver converge on refresh after mixed send flow
- **WHEN** one user sends early messages, another user joins and sends additional messages, and both users refresh
- **THEN** both users render the same persisted latest message set with no missing tail message

## ADDED Requirements

### Requirement: Message send payload SHALL include mention metadata
The client SHALL include `mentionedUserIds[]` in the message create request when `@username` tokens are detected in the message body.

#### Scenario: Message with mentions includes mentionedUserIds
- **WHEN** user types a message with one or more valid `@username` tokens and sends
- **THEN** the outbound `POST /api/v1/messages` request body includes `mentionedUserIds` array containing the resolved userIds

#### Scenario: Message without mentions sends empty or absent mentionedUserIds
- **WHEN** user sends a plain text message with no `@` tokens
- **THEN** the request body either omits `mentionedUserIds` or sends an empty array; message is sent normally
