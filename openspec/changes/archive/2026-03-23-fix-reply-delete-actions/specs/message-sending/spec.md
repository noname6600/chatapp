## MODIFIED Requirements

### Requirement: Message is sent when user presses Enter key
The system SHALL send a message when the user presses the Enter key in the message input field, and if reply context is active the outgoing payload SHALL include reply linkage metadata.

#### Scenario: Enter key sends message through HTTP endpoint
- **WHEN** user types text and presses Enter key
- **THEN** message is sent to `POST /api/v1/messages` with the message content

#### Scenario: Enter key sends message through WebSocket
- **WHEN** user types text and presses Enter in a WebSocket-connected chat
- **THEN** SEND command is dispatched to the WebSocket connection with message content

#### Scenario: Enter key does not send empty message
- **WHEN** user presses Enter with empty or whitespace-only input
- **THEN** message is not sent, input remains focused

#### Scenario: Reply context propagates in send flow
- **WHEN** user has selected a reply target from message item action and sends message
- **THEN** outgoing message metadata includes reference to the replied message and reply context is cleared after successful send

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
