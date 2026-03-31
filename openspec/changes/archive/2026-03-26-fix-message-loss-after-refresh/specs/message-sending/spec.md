## MODIFIED Requirements

### Requirement: Sent messages update unread count for other users in real-time
When a message is successfully sent and broadcast via WebSocket, other users in the same room SHALL see unread updates in real-time, and sender/receiver clients SHALL converge on the same persisted latest window after refresh.

#### Scenario: Unread count increments for recipient when message is sent
- **WHEN** User A sends a message to a room
- **THEN** the NewMessageEvent is broadcast via WebSocket, User B's room store increments unreadCount (if User B has not marked the room read)

#### Scenario: Unread count update reaches all connected clients
- **WHEN** a message is sent and broadcast
- **THEN** all WebSocket-connected clients in that room receive the NewMessageEvent and update their local unreadCount

#### Scenario: UI reflects updated unread count immediately
- **WHEN** unreadCount changes due to NewMessageEvent
- **THEN** the unread message banner (if displayed) updates to show the new count without page reload

#### Scenario: Sender and receiver converge on refresh after mixed send flow
- **WHEN** one user sends early messages, another user joins and sends additional messages, and both users refresh
- **THEN** both users render the same persisted latest message set with no missing tail message
