## MODIFIED Requirements

### Requirement: Host-based domain routing through Nginx
The deployment SHALL route production traffic through Nginx based on request host so frontend and API traffic are separated by domain using chatweb.nani.id.vn and api.chatweb.nani.id.vn.

#### Scenario: Frontend domain request
- **WHEN** a client requests chatweb.nani.id.vn
- **THEN** Nginx forwards the request to the frontend runtime target

#### Scenario: API domain request
- **WHEN** a client requests api.chatweb.nani.id.vn
- **THEN** Nginx forwards the request to the gateway service target

### Requirement: TLS termination at edge proxy
The system SHALL terminate HTTPS at Nginx for both frontend and API hosts in production.

#### Scenario: HTTPS request to frontend host
- **WHEN** a client connects to chatweb.nani.id.vn over HTTPS
- **THEN** Nginx presents a valid certificate for that host and serves frontend content

#### Scenario: HTTPS request to API host
- **WHEN** a client connects to api.chatweb.nani.id.vn over HTTPS
- **THEN** Nginx presents a valid certificate for that host and forwards traffic to gateway over internal network

### Requirement: WebSocket upgrade support through proxy
Nginx SHALL preserve required headers and protocol behavior for WebSocket upgrade requests routed to backend realtime endpoints.

#### Scenario: WebSocket handshake via API host
- **WHEN** a client initiates a WebSocket upgrade request through api.chatweb.nani.id.vn
- **THEN** Nginx forwards upgrade and connection headers so the backend handshake succeeds