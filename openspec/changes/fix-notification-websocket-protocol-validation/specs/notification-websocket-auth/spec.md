## ADDED Requirements

### Requirement: JWT token extraction from WebSocket query parameters

The notification WebSocket endpoint ("/ws/notifications") SHALL extract and validate JWT tokens from query string parameters during the WebSocket handshake.

#### Scenario: Successful token extraction

- **WHEN** a client initiates a WebSocket connection with URL `ws://localhost:8080/ws/notifications?token=<valid-jwt>`
- **THEN** the backend extracts the `token` query parameter and proceeds to validate it

#### Scenario: Handle missing token parameter

- **WHEN** a client initiates a WebSocket connection without a `token` query parameter
- **THEN** the backend rejects the connection with a clear error indicating token is missing

#### Scenario: Handle empty token value

- **WHEN** a client initiates a WebSocket connection with `?token=` (empty value)
- **THEN** the backend rejects the connection as invalid token format

#### Scenario: Gateway query parameter propagation

- **WHEN** a client connects through the API Gateway to `/ws/notifications?token=<jwt>`
- **THEN** the gateway correctly propagates the `token` query parameter to the backend notification service (not stripped or mangled)

### Requirement: JWT validation and userId extraction

The backend SHALL validate JWT tokens using the configured JWT decoder and extract the userId from the token's subject claim.

#### Scenario: Valid token accepted

- **WHEN** a client provides a JWT with valid signature and not expired
- **THEN** the backend decodes the token, extracts the userId from the subject claim, and establishes the WebSocket connection

#### Scenario: Expired token rejected

- **WHEN** a client provides an expired JWT
- **THEN** the backend rejects the validation and closes the handshake with a decode failure reason

#### Scenario: Invalid signature rejected

- **WHEN** a client provides a JWT with an invalid signature
- **THEN** the backend rejects the validation and logs the decode failure

#### Scenario: Malformed token rejected

- **WHEN** a client provides a string that is not a valid JWT format
- **THEN** the backend rejects the validation with a decode failure reason

#### Scenario: Missing subject claim

- **WHEN** a client provides a valid JWT that lacks a subject claim or has a non-UUID subject
- **THEN** the backend rejects the validation indicating userId extraction failed

### Requirement: Synchronization of JWT signing keys

The backend JWT decoder SHALL be configured with the same signing keys used by the authentication service, ensuring tokens can be reliably validated.

#### Scenario: Key rotation handled

- **WHEN** authentication service rotates JWT signing keys
- **THEN** the notification service backend is updated with new keys and can validate tokens signed with both old and new keys during rotation window

#### Scenario: Testing with valid test token

- **WHEN** a developer tests the WebSocket connection locally with a test JWT
- **THEN** the backend can validate the test token if it was signed with the test key configured in the backend
