## ADDED Requirements

### Requirement: Structured handshake rejection logging

The WebSocket handshake interceptor SHALL log detailed rejection reasons when a client connection is rejected, enabling troubleshooting of authentication failures.

#### Scenario: Token parameter missing

- **WHEN** a client connects to `/ws/notifications` without a `token` query parameter
- **THEN** the backend logs rejection with reason `MISSING_TOKEN` including the list of query parameters that were present

#### Scenario: Token parameter present but invalid format

- **WHEN** a client connects with a `token` parameter that is blank or empty string
- **THEN** the backend logs rejection with reason `INVALID_FORMAT` and includes the token length

#### Scenario: Token decode fails

- **WHEN** a client connects with a token that cannot be decoded by the JWT decoder (invalid signature, expiration, etc.)
- **THEN** the backend logs rejection with reason `DECODE_FAILED` including the exception type and message (e.g., "JwtException: Invalid signature")

#### Scenario: Token decodes but userId is null

- **WHEN** a client connects with a valid JWT that has no subject claim or the subject cannot be parsed as UUID
- **THEN** the backend logs rejection with reason `NULL_USER_ID` and includes the JWT subject claim value

#### Scenario: Successful handshake

- **WHEN** a client connects with a valid JWT token
- **THEN** the backend logs success with the extracted userId and treats it as a normal connection

### Requirement: Handshake metrics

The system SHALL track WebSocket handshake success and failure metrics to enable monitoring and alerting.

#### Scenario: Increment success counter

- **WHEN** a client successfully completes the WebSocket handshake
- **THEN** the system increments counter `websocket.handshake.success` by 1

#### Scenario: Increment rejection counter by reason

- **WHEN** a client handshake is rejected
- **THEN** the system increments counter `websocket.handshake.rejected` with a tag `reason` set to the rejection reason (e.g., `MISSING_TOKEN`, `DECODE_FAILED`)

#### Scenario: Track active connections

- **WHEN** a client successfully connects
- **THEN** the gauge `websocket.connections.active` is incremented by 1

#### Scenario: Track disconnections

- **WHEN** a client disconnects
- **THEN** the gauge `websocket.connections.active` is decremented by 1

### Requirement: HTTP response headers for rejection

When a WebSocket handshake is rejected, the backend SHALL respond with HTTP headers that indicate the reason for rejection (for debugging in browser dev tools).

#### Scenario: Missing token header

- **WHEN** a client handshake is rejected due to missing token
- **THEN** the HTTP response includes header `X-WebSocket-Rejection-Reason: MISSING_TOKEN`

#### Scenario: Decode failure header

- **WHEN** a client handshake is rejected due to JWT decode failure
- **THEN** the HTTP response includes header `X-WebSocket-Rejection-Reason: DECODE_FAILED` and optionally `X-WebSocket-Error-Details: <exception-type>`

#### Scenario: Token parameter presence header

- **WHEN** any client attempts a WebSocket handshake
- **THEN** the HTTP response includes header `X-WebSocket-Token-Present: true` (if token query param exists) or `X-WebSocket-Token-Present: false`

### Requirement: Query parameter diagnostics

When extracting the token from the WebSocket request, the backend SHALL log diagnostic information about the request structure to aid in troubleshooting gateway routing issues.

#### Scenario: Log query parameters on connection attempt

- **WHEN** a client connects to `/ws/notifications`
- **THEN** the backend logs the names of all query parameters present in the request (e.g., `["token", "session_id"]` or `[]` if none)

#### Scenario: Log token parameter length

- **WHEN** a client connects with a `token` query parameter
- **THEN** the backend logs the token length (character count) to verify the token is not being truncated by gateway routing

#### Scenario: Detect gateway query parameter stripping

- **WHEN** a client connects through a gateway that strips query parameters
- **THEN** the backend logs `token` parameter as missing, prompting operators to check gateway configuration for WebSocket query parameter handling
