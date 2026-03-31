## MODIFIED Requirements

### Requirement: Message is sent when user presses Enter key
The system SHALL send a message when the user presses the Enter key in the message input field, as an alternative to clicking a send button, and SHALL avoid duplicate sends from repeated triggers while a send is already in-flight.

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
- **WHEN** user has selected a reply target and sends message
- **THEN** outgoing payload includes `replyToMessageId` linked to the selected original message

#### Scenario: In-flight send suppresses duplicate trigger
- **WHEN** user triggers send repeatedly (rapid Enter/click) while current send request is still in-flight
- **THEN** only one outbound send is processed for that draft

#### Scenario: Message with selected mention remains sendable
- **WHEN** user selects a mention from suggestion list and then sends message
- **THEN** outgoing content contains a valid mention representation
- **AND** message send is accepted without undefined mention content errors

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
