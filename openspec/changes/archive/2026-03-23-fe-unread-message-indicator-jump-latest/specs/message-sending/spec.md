## MODIFIED Requirements

### Requirement: Message is sent when user presses Enter key
The system SHALL send a message when the user presses the Enter key in the message input field, as an alternative to clicking a send button.

#### Scenario: Enter key sends message through HTTP endpoint
- **WHEN** user types text and presses Enter key
- **THEN** message is sent to `POST /api/v1/messages` with the message content

#### Scenario: Enter key sends message through WebSocket
- **WHEN** user types text and presses Enter in a WebSocket-connected chat
- **THEN** SEND command is dispatched to the WebSocket connection with message content

#### Scenario: Enter key does not send empty message
- **WHEN** user presses Enter with empty or whitespace-only input
- **THEN** message is not sent, input remains focused

### Requirement: Dragged files are automatically attached to message send
The system SHALL allow users to drag files/images into the message input area, and these files SHALL be uploaded as attachments when the message is sent.

#### Scenario: File dropped into input is queued for upload
- **WHEN** user drops a file onto the message input area
- **THEN** file is visually indicated as pending upload (e.g., shown in attachments preview)

#### Scenario: Dropped files and typed text are sent together
- **WHEN** user drops files, types text, and presses Enter
- **THEN** single message request is sent with both text content and file attachments

#### Scenario: Multiple dropped files create multiple attachments
- **WHEN** user drops three image files onto input
- **THEN** all three files are queued and attached to the message when sent

### Requirement: User interactions are keyboard-accessible
The system SHALL ensure that all message send operations are accessible via keyboard without reliance on mouse/click interactions.

#### Scenario: Message can be sent using only keyboard
- **WHEN** user types message and presses Enter (no mouse interaction required)
- **THEN** message is successfully sent

#### Scenario: Inline newlines do not interfere with send
- **WHEN** user creates multiline message using Alt+Enter and sends with Enter
- **THEN** message with all newlines is sent correctly, without premature send on the first Enter

### Requirement: Sent messages update unread count for other users in real-time
When a message is successfully sent and broadcast via WebSocket, other users in the same room SHALL see the unread message count increment immediately (if they have the message list open).

#### Scenario: Unread count increments for recipient when message is sent
- **WHEN** User A sends a message to a room
- **THEN** the NewMessageEvent is broadcast via WebSocket, User B's room store increments unreadCount (if User B has not marked the room read)

#### Scenario: Unread count update reaches all connected clients
- **WHEN** a message is sent and broadcast
- **THEN** all WebSocket-connected clients in that room receive the NewMessageEvent and update their local unreadCount

#### Scenario: UI reflects updated unread count immediately
- **WHEN** unreadCount changes due to NewMessageEvent
- **THEN** the unread message banner (if displayed) updates to show the new count without page reload
