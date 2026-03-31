## MODIFIED Requirements

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

## ADDED Requirements

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
