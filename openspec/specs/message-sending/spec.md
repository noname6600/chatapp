# message-sending Specification

## Purpose
TBD - created by archiving change simplify-message-input. Update Purpose after archive.
## Requirements
### Requirement: Message is sent when user presses Enter key
The system SHALL send a message when the user presses Enter key in the message input field as an alternative to clicking a send button, SHALL avoid duplicate send triggers while one send is in-flight for the same draft, and SHALL not treat an optimistic placeholder as delivered until server confirmation is correlated.

#### Scenario: Enter key sends message through HTTP endpoint
- **WHEN** user types text and presses Enter key
- **THEN** message is sent to `POST /api/v1/messages` with message content and `clientMessageId`

#### Scenario: Enter key sends message through WebSocket
- **WHEN** user types text and presses Enter in a WebSocket-connected chat
- **THEN** SEND command is dispatched to the WebSocket connection with message content and `clientMessageId`

#### Scenario: Enter key does not send empty message
- **WHEN** user presses Enter with empty or whitespace-only input
- **THEN** message is not sent and input remains focused

#### Scenario: Reply context propagates in send flow
- **WHEN** user has selected a reply target and sends message
- **THEN** outgoing payload includes `replyToMessageId` linked to the selected original message

#### Scenario: In-flight send suppresses duplicate trigger
- **WHEN** user triggers send repeatedly while current send request for the same draft is still in-flight
- **THEN** only one outbound send is processed for that draft

#### Scenario: Optimistic message remains pending until correlated confirmation
- **WHEN** optimistic message is inserted locally after send trigger
- **THEN** it remains in `pending` state until a server-confirmed message with matching `clientMessageId` is observed

### Requirement: Unconfirmed optimistic sends SHALL transition to failed with retry option
The system SHALL transition pending optimistic messages to `failed` when confirmation does not arrive before the configured timeout, SHALL display failure state in the message list, and SHALL provide a retry action.

#### Scenario: Pending optimistic send times out and becomes failed
- **WHEN** pending optimistic message exceeds the configured confirmation timeout without correlated server message
- **THEN** the message transitions to `failed`

#### Scenario: Failed optimistic message exposes retry
- **WHEN** message is in `failed` state
- **THEN** UI shows a retry action for that message

#### Scenario: Retry keeps idempotency safety
- **WHEN** user retries a failed optimistic message
- **THEN** resend uses the same original `clientMessageId`

### Requirement: Dragged files are automatically attached to message send
The system SHALL allow users to drag files/images into the message input area, and these files SHALL be uploaded using upload-service prepared Cloudinary parameters before message send payload includes normalized attachment metadata.

#### Scenario: File dropped into input is queued for upload
- **WHEN** user drops a file onto the message input area
- **THEN** file is visually indicated as pending upload (e.g., shown in attachments preview)

#### Scenario: Dropped files and typed text are sent together
- **WHEN** user drops files, types text, and presses Enter
- **THEN** single message request is sent with both text content and normalized attachment metadata

#### Scenario: Multiple dropped files create multiple attachments
- **WHEN** user drops three image files onto input
- **THEN** all three files are queued and attached to the message when sent

#### Scenario: Attachment upload uses upload-service preparation
- **WHEN** attachment upload flow starts from chat input
- **THEN** client requests signed upload preparation from upload-service for purpose `chat-attachment`
- **AND** client uploads file to Cloudinary using returned signed parameters

#### Scenario: Failed attachment upload blocks attachment submission
- **WHEN** one or more queued attachment uploads fail before send
- **THEN** failed attachments are not included in message payload
- **AND** UI surfaces upload error state to user before retry/send

### Requirement: User interactions are keyboard-accessible
The system SHALL ensure that all message send operations are accessible via keyboard without reliance on mouse/click interactions.

#### Scenario: Message can be sent using only keyboard
- **WHEN** user types message and presses Enter (no mouse interaction required)
- **THEN** message is successfully sent

#### Scenario: Inline newlines do not interfere with send
- **WHEN** user creates multiline message using Alt+Enter and sends with Enter
- **THEN** message with all newlines is sent correctly, without premature send on the first Enter

### Requirement: Sender exclusion is enforced for unread increments
Unread count updates triggered by message-sent events SHALL exclude the sender account for that same message.

#### Scenario: Sender event does not increase own unread
- **WHEN** current user sends a message and receives corresponding real-time sent event
- **THEN** current user's room unread count remains unchanged

#### Scenario: Other members still receive unread increment
- **WHEN** one user sends a message to shared room
- **THEN** other room members receive unread increment according to existing unread rules

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

#### Scenario: Sender and receiver converge on refresh after mixed send flow
- **WHEN** one user sends early messages, another user joins and sends additional messages, and both users refresh
- **THEN** both users render the same persisted latest message set with no missing tail message

### Requirement: Message send supports ordered mixed-content payloads
The system SHALL accept an ordered message-body payload so one sent message can preserve interleaved text, media, and embed content.

#### Scenario: Mixed-content payload is submitted in authored order
- **WHEN** user sends a draft containing text blocks and uploaded image blocks
- **THEN** send payload preserves the authored sequence of those blocks instead of flattening media into an unordered attachment list

#### Scenario: Existing text-only sends remain valid
- **WHEN** user sends a plain text message without media blocks
- **THEN** the existing message send behavior remains valid without requiring embed or asset blocks

### Requirement: Message service derives compatibility fields from ordered content
The system SHALL derive compatibility-oriented summaries from ordered message blocks so legacy views can continue working during migration.

#### Scenario: Mixed-content message derives preview summary
- **WHEN** a mixed-content message is persisted
- **THEN** service derives a concise preview string that summarizes ordered blocks for room list and notification usage

#### Scenario: Asset references remain available to legacy consumers
- **WHEN** ordered content includes uploaded asset blocks
- **THEN** normalized asset metadata remains available in compatibility fields needed by existing consumers during migration

### Requirement: Plain links remain safe and clickable in mixed-content messages
The system SHALL preserve URLs inside text blocks as plain clickable links without requiring provider-specific embed rendering.

#### Scenario: URL in text block stays clickable
- **WHEN** a text block contains a valid URL
- **THEN** message rendering preserves the authored text and exposes the URL as a clickable link

### Requirement: Mention highlighting follows target-aware policy
The system SHALL apply row-level highlight only for messages that mention the current user, and token-level highlight for mentions that target other users.

#### Scenario: Self-mention highlights entire message item
- **WHEN** message content mentions the currently signed-in user
- **THEN** the full message item is highlighted
- **AND** mention token remains visibly highlighted

#### Scenario: Non-self mention highlights token only
- **WHEN** message content contains mention(s) that do not target the currently signed-in user
- **THEN** only mention token spans are highlighted
- **AND** the message row background remains unhighlighted for mention reason alone

