## MODIFIED Requirements

### Requirement: Message is sent when user presses Enter key
The system SHALL send a message when the user presses Enter key in the message input field as an alternative to clicking a send button, SHALL avoid duplicate send triggers while one send is in-flight for the same draft, and SHALL not treat an optimistic placeholder as delivered until server confirmation is correlated. Optimistic placeholders MAY use client-side ordering aids for tail rendering, but they MUST NOT advance authoritative latest-sequence metadata used for unread or behind-latest indicators.

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

#### Scenario: Optimistic attachment send does not poison authoritative latest sequence
- **WHEN** the sender creates a pending image or attachment message before server confirmation arrives
- **THEN** the optimistic placeholder can render at the local tail
- **AND** unread and behind-latest metadata continue using authoritative confirmed sequence state until the confirmed message is correlated
