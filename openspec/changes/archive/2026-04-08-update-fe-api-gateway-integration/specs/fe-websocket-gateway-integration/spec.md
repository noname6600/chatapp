## ADDED Requirements

### Requirement: WebSocket Connections Route Through Gateway
The frontend WebSocket connections (chat, presence, friendship) SHALL connect through the gateway on the gateway host/port instead of directly to individual services. The WebSocket endpoint SHALL maintain the same authenticated session as REST API requests.

#### Scenario: Chat WebSocket connects through gateway
- **WHEN** user opens a chat room
- **THEN** the WebSocket connection is established to `ws://localhost:8080/api/v1/chat/ws` (not `ws://localhost:8083/...`)

#### Scenario: Presence WebSocket connects through gateway
- **WHEN** user session starts
- **THEN** presence WebSocket connects to `ws://localhost:8080/api/v1/presence/ws` (not `ws://localhost:8084/...`)

#### Scenario: Friendship WebSocket connects through gateway
- **WHEN** user initializes real-time friend notifications
- **THEN** friendship WebSocket connects to `ws://localhost:8080/api/v1/friend/ws` (not `ws://localhost:8085/...`)

### Requirement: WebSocket Gateway Configuration Uses Environment Variables
WebSocket endpoint URLs SHALL be configurable via environment variables to support different environments (local development, staging, production).

#### Scenario: Development WebSocket uses localhost gateway
- **WHEN** frontend runs with default or `REACT_APP_WS_URL` not set
- **THEN** WebSocket connections target `ws://localhost:8080/api/v1/{service}/ws`

#### Scenario: Production WebSocket uses custom gateway
- **WHEN** frontend builds with `REACT_APP_WS_URL=wss://api.example.com/api/v1`
- **THEN** WebSocket connections target `wss://api.example.com/api/v1/{service}/ws`

### Requirement: WebSocket Authentication Uses JWT Token
WebSocket connections through the gateway SHALL authenticate using the same JWT bearer token used for REST API requests.

#### Scenario: JWT token sent in WebSocket handshake
- **WHEN** WebSocket connection is initiated
- **THEN** the connection includes the JWT token (via Authorization header or query parameter)
- **AND** the gateway validates the token before accepting the connection

### Requirement: WebSocket Gateway Integration Maintains Real-Time Messaging
WebSocket message flow through the gateway SHALL not introduce perceptible latency or break real-time capabilities. Message delivery timing and ordering SHALL be preserved.

#### Scenario: Chat messages delivered in order through gateway
- **WHEN** user sends two messages in sequence
- **THEN** both messages are received by other clients in the same order
- **AND** messages arrive within typical network latency (no additional gateway delay)

#### Scenario: Presence updates flow in real-time through gateway
- **WHEN** user comes online or goes offline
- **THEN** presence updates are broadcast to connected clients in real-time through the gateway

### Requirement: WebSocket Reconnection Works Through Gateway
When a WebSocket connection drops, the frontend SHALL attempt to reconnect through the gateway with exponential backoff and maintain connection state.

#### Scenario: WebSocket reconnects after network blip
- **WHEN** WebSocket connection is temporarily lost
- **THEN** frontend automatically attempts to reconnect to the gateway
- **AND** reconnection succeeds within configured timeout
- **AND** message queue is flushed upon successful reconnection

#### Scenario: WebSocket maintains session across reconnections
- **WHEN** user reconnects after temporary disconnect
- **THEN** the session state (user ID, room context) is preserved
- **AND** user receives any messages sent while disconnected (if supported by backend)
