## MODIFIED Requirements

### Requirement: Centralized REST API Gateway Configuration
The frontend SHALL use a single centralized gateway URL for all REST API requests to backend microservices. The gateway address SHALL be configurable via environment variables to support different environments (local development, staging, production).

#### Scenario: Development environment uses localhost gateway
- **WHEN** frontend runs with `VITE_API_URL` not set
- **THEN** all API requests target `http://localhost:8080/api/v1` by default

#### Scenario: Production environment uses custom gateway URL
- **WHEN** frontend builds with `VITE_API_URL=https://api.example.com/api/v1`
- **THEN** all API requests target `https://api.example.com/api/v1`

### Requirement: All REST API Clients Route Through Gateway
The frontend REST API clients (auth, user, chat, presence, friend, notification, upload) SHALL all route their requests through the gateway. Individual service URLs (ports 8081-8088) SHALL NOT be used directly. API base URL and per-call endpoint suffixes SHALL compose to canonical gateway paths WITHOUT duplicated service segments.

#### Scenario: Auth API calls route through gateway
- **WHEN** user calls `authApi.post('/login', credentials)`
- **THEN** request goes to `http://localhost:8080/api/v1/auth/login` (not `http://localhost:8081/api/v1/auth/login`)

#### Scenario: Chat API calls route through gateway
- **WHEN** user calls `chatApi.get('/rooms/my')`
- **THEN** request goes to `http://localhost:8080/api/v1/chat/rooms/my`
- **AND** request path SHALL NOT contain duplicated segments such as `/chat/chat/...`

#### Scenario: User API calls route through gateway
- **WHEN** user calls `userApi.get('/me')`
- **THEN** request goes to `http://localhost:8080/api/v1/users/me`
- **AND** request path SHALL NOT contain duplicated segments such as `/users/users/me`

#### Scenario: Friendship API calls route through gateway
- **WHEN** user calls `friendApi.get('/friends')`
- **THEN** request goes to `http://localhost:8080/api/v1/friendship/friends`
- **AND** request path SHALL NOT contain duplicated segments

### Requirement: API Client Error Handling Works Through Gateway
API clients SHALL continue to provide consistent error handling and token refresh logic regardless of whether requests route through the gateway or directly to services.

#### Scenario: Token refresh triggered on 401 response for authenticated APIs
- **WHEN** an authenticated API request receives a 401 Unauthorized response and a refresh token exists
- **THEN** system attempts to refresh the token using the refresh API
- **AND** the original request is retried with the new token

#### Scenario: Missing refresh token does not surface as feature-specific runtime crash
- **WHEN** an authenticated API request receives 401 and no refresh token exists
- **THEN** the client SHALL transition to a controlled unauthenticated state
- **AND** feature stores/providers SHALL NOT throw unhandled `No refresh token` errors