## ADDED Requirements

### Requirement: All client traffic routes through gateway
The gateway SHALL be the sole public entry point; all HTTP API calls and WebSocket connections from clients are proxied to the appropriate downstream microservice based on path prefix.

#### Scenario: HTTP request routed to auth-service
- **WHEN** a client sends `POST /api/auth/login`
- **THEN** the gateway SHALL forward the request to `http://auth-service:8081/api/auth/login` and return the downstream response to the client

#### Scenario: HTTP request routed to chat-service
- **WHEN** a client sends `GET /api/chat/rooms`
- **THEN** the gateway SHALL forward the request to `http://chat-service:8083/api/chat/rooms`

#### Scenario: WebSocket connection to chat-service
- **WHEN** a client opens a WebSocket connection to `/ws/chat/**`
- **THEN** the gateway SHALL proxy the WebSocket upgrade to `ws://chat-service:8083/ws/chat/**`

#### Scenario: WebSocket connection to presence-service
- **WHEN** a client opens a WebSocket connection to `/ws/presence/**`
- **THEN** the gateway SHALL proxy the WebSocket upgrade to `ws://presence-service:8084/ws/presence/**`

#### Scenario: WebSocket connection to friendship-service
- **WHEN** a client opens a WebSocket connection to `/ws/friendship/**`
- **THEN** the gateway SHALL proxy the WebSocket upgrade to `ws://friendship-service:8085/ws/friendship/**`

#### Scenario: Unknown path returns 404
- **WHEN** a client sends a request to a path not matched by any route
- **THEN** the gateway SHALL return HTTP 404

### Requirement: Routes use static Docker Compose DNS
The gateway SHALL resolve downstream service addresses using Docker Compose internal DNS names. No external service registry SHALL be required.

#### Scenario: Service reachable by container name
- **WHEN** the gateway forwards a request to `http://auth-service:8081`
- **THEN** Docker DNS SHALL resolve `auth-service` to the running container's IP on the shared bridge network

#### Scenario: Scaling-ready URI design
- **WHEN** a service needs to scale to multiple instances
- **THEN** the route URI SHALL be changeable from `http://service-name:port` to `lb://service-name` without other code changes
