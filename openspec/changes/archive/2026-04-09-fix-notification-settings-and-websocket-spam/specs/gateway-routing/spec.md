## MODIFIED Requirements

### Requirement: All client traffic routes through gateway
The gateway SHALL be the sole public entry point; all HTTP API calls and WebSocket connections from clients are proxied to the appropriate downstream microservice based on canonical path prefix rules. Canonical REST ingress for frontend clients SHALL use `/api/v1/*` paths and canonical websocket ingress SHALL expose each realtime service on a stable `/ws/*` path.

#### Scenario: HTTP request routed to auth-service
- **WHEN** a client sends `POST /api/v1/auth/login`
- **THEN** the gateway SHALL forward the request to `http://auth-service:8081/api/v1/auth/login` and return the downstream response to the client

#### Scenario: HTTP request routed to user-service
- **WHEN** a client sends `GET /api/v1/users/me`
- **THEN** the gateway SHALL forward the request to `http://user-service:8082/api/v1/users/me`

#### Scenario: HTTP request routed to chat-service with service-prefix rewrite
- **WHEN** a client sends `GET /api/v1/chat/rooms/my`
- **THEN** the gateway SHALL route to chat-service
- **AND** the forwarded downstream path SHALL be rewritten to `GET /api/v1/rooms/my`

#### Scenario: HTTP request routed to friendship-service with service-prefix rewrite
- **WHEN** a client sends `GET /api/v1/friendship/friends`
- **THEN** the gateway SHALL route to friendship-service
- **AND** the forwarded downstream path SHALL be rewritten to `GET /api/v1/friends`

#### Scenario: WebSocket connection to chat-service
- **WHEN** a client opens a WebSocket connection to `/ws/chat/**`
- **THEN** the gateway SHALL proxy the WebSocket upgrade to `ws://chat-service:8083/ws/chat/**`

#### Scenario: WebSocket connection to presence-service
- **WHEN** a client opens a WebSocket connection to `/ws/presence/**`
- **THEN** the gateway SHALL proxy the WebSocket upgrade to `ws://presence-service:8084/ws/presence/**`

#### Scenario: WebSocket connection to notification-service
- **WHEN** a client opens a WebSocket connection to `/ws/notifications`
- **THEN** the gateway SHALL proxy the WebSocket upgrade to `ws://notification-service:8086/ws/notifications`

#### Scenario: Unknown path returns 404
- **WHEN** a client sends a request to a path not matched by any route
- **THEN** the gateway SHALL return HTTP 404