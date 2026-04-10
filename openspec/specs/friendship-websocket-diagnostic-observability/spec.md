## ADDED Requirements

### Requirement: Friendship WebSocket Handshake Diagnostics
The system MUST emit structured diagnostics for each friendship WebSocket connection attempt across frontend and backend handshake stages.

#### Scenario: Successful handshake path is fully observable
- **WHEN** the client attempts to connect to `/ws/friendship` with a valid token
- **THEN** logs MUST show connect attempt, backend handshake acceptance, token decode success, and connection established events with correlation-ready context

#### Scenario: Rejected handshake reports actionable reason
- **WHEN** handshake validation fails due to token parsing, token expiry, signature validation, or JWKS reachability problems
- **THEN** logs MUST include a rejection stage and concise failure reason that identifies whether failure occurred before or during JWT decode

### Requirement: Friendship WebSocket Reconnect Diagnostics
The system MUST expose reconnect transitions so repeated disconnect loops can be diagnosed.

#### Scenario: Reconnect loop is visible
- **WHEN** the socket closes unexpectedly while the user remains authenticated
- **THEN** the client MUST log close event and next retry schedule before attempting reconnection

#### Scenario: Manual disconnect suppresses reconnect
- **WHEN** socket disconnect is user/session initiated
- **THEN** the client MUST log manual-close state and MUST NOT schedule automatic reconnect