# Capability: client-message-id-flow

### Requirement: Client provides clientMessageId on send
The system SHALL accept an optional `clientMessageId` string (max 100 characters) in the send message
request, via both the HTTP REST endpoint (`POST /api/v1/messages`) and the WebSocket `SEND` command.
When provided, this value SHALL be persisted on the `ChatMessage` entity.

#### Scenario: clientMessageId accepted in REST request
- **WHEN** a client sends `POST /api/v1/messages` with a `clientMessageId` field
- **THEN** the server accepts the request and the resulting message has that `clientMessageId` persisted

#### Scenario: clientMessageId accepted in WebSocket SEND command
- **WHEN** a client sends a WebSocket `SEND` command with a `clientMessageId` field
- **THEN** the server creates the message with that `clientMessageId` persisted

#### Scenario: clientMessageId is optional
- **WHEN** a client sends a message without a `clientMessageId` field
- **THEN** the server creates the message normally with `clientMessageId` as null

#### Scenario: clientMessageId exceeds max length
- **WHEN** a client provides a `clientMessageId` longer than 100 characters
- **THEN** the server rejects the request with a 400 validation error

### Requirement: Server echoes clientMessageId in response
The system SHALL include `clientMessageId` in the `MessageResponse` returned after successfully creating
a message, set to the same value provided by the client (or null if not provided).

#### Scenario: clientMessageId echoed in REST response
- **WHEN** a client sends a message with a `clientMessageId` of `"abc-123"`
- **THEN** the response body includes `"clientMessageId": "abc-123"`

#### Scenario: null clientMessageId in response when not provided
- **WHEN** a client sends a message without a `clientMessageId`
- **THEN** the response body includes `"clientMessageId": null`

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

### Requirement: clientMessageId-based optimistic reconciliation
The frontend SHALL use `clientMessageId` as the primary key to match a server-confirmed message with its optimistic placeholder in local message state, SHALL reconcile on send response, WebSocket events, and history refresh, and SHALL ensure placeholders without durable server match are not shown as sent.

#### Scenario: Server-confirmed message with clientMessageId replaces optimistic placeholder
- **WHEN** server-confirmed message arrives with `clientMessageId` that matches optimistic placeholder
- **THEN** optimistic placeholder is replaced by server-confirmed message without duplication

#### Scenario: Fallback to content-based matching when clientMessageId absent
- **WHEN** server-confirmed message arrives without `clientMessageId`
- **THEN** frontend falls back to matching by `(senderId, content, replyToMessageId)` for reconciliation

#### Scenario: Latest history load marks stale unresolved optimistic placeholder failed
- **WHEN** latest-history refresh completes and no persisted message matches an older pending placeholder by `clientMessageId`
- **THEN** placeholder transitions to `failed` instead of staying as sent/pending indefinitely

#### Scenario: Duplicate confirmations do not create duplicate rendered messages
- **WHEN** both REST and WebSocket confirmations are received for the same `clientMessageId`
- **THEN** local state keeps one canonical message entry

### Requirement: clientMessageId included in Kafka message event payload
The system SHALL include `clientMessageId` in the `ChatMessagePayload` published to the Kafka
`CHAT_MESSAGE_SENT` topic when a message is created.

#### Scenario: Kafka payload carries clientMessageId
- **WHEN** a message is created with a non-null `clientMessageId`
- **THEN** the published `ChatMessagePayload` event contains the same `clientMessageId` value

#### Scenario: Kafka payload carries null clientMessageId when not provided
- **WHEN** a message is created without a `clientMessageId`
- **THEN** the published `ChatMessagePayload` event contains `"clientMessageId": null`

#### Scenario: Downstream consumers unaffected when field absent
- **WHEN** a Kafka consumer that does not know about `clientMessageId` deserializes a `ChatMessagePayload`
- **THEN** deserialization succeeds without errors due to `@JsonIgnoreProperties(ignoreUnknown = true)`
