## MODIFIED Requirements

### Requirement: Idempotent send — duplicate clientMessageId returns existing message
The system SHALL detect when a send request carries a `clientMessageId` that already exists for the same
`roomId`, and SHALL return the existing message response instead of creating a duplicate.
The frontend SHALL generate a unique `clientMessageId` per outgoing message and include it in the send
request so the server-side idempotency guarantee is exercisable.

#### Scenario: Duplicate clientMessageId is detected and short-circuited
- **WHEN** a client sends a message with a `clientMessageId` that was already used for a previous message in the same room
- **THEN** the server returns the existing message (HTTP 200) without creating a new record and without publishing a new Kafka event

#### Scenario: Same clientMessageId in different rooms creates two messages
- **WHEN** a client sends messages with the same `clientMessageId` to two different rooms
- **THEN** the server creates two separate messages (deduplication is scoped per `(roomId, clientMessageId)`)

#### Scenario: No clientMessageId — no idempotency check performed
- **WHEN** a client sends a message without a `clientMessageId`
- **THEN** the server creates a new message regardless of any other message content

#### Scenario: Frontend generates clientMessageId before sending via HTTP
- **WHEN** the frontend calls the HTTP send message API
- **THEN** the request body SHALL include a `clientMessageId` UUID generated on the client

#### Scenario: Frontend generates clientMessageId before sending via WebSocket
- **WHEN** the frontend sends a SEND command over WebSocket
- **THEN** the WebSocket payload SHALL include the same `clientMessageId` UUID used for the optimistic message

## ADDED Requirements

### Requirement: clientMessageId-based optimistic reconciliation
The frontend SHALL use `clientMessageId` as the primary key to match a server-confirmed message with its
optimistic placeholder in the local message store. If no `clientMessageId` match is found (legacy messages),
the frontend SHALL fall back to content-based matching.

#### Scenario: Server-confirmed message with clientMessageId replaces optimistic placeholder
- **WHEN** a server-confirmed message arrives with a `clientMessageId` that matches an optimistic placeholder
- **THEN** the optimistic placeholder is replaced by the server-confirmed message without duplication

#### Scenario: Fallback to content-based matching when clientMessageId absent
- **WHEN** a server-confirmed message arrives without a `clientMessageId`
- **THEN** the frontend falls back to matching by `(senderId, content, replyToMessageId)` for reconciliation
