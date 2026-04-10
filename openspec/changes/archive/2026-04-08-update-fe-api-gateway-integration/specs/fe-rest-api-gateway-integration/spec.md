## ADDED Requirements

### Requirement: Centralized REST API Gateway Configuration
The frontend SHALL use a single centralized gateway URL for all REST API requests to backend microservices. The gateway address SHALL be configurable via environment variables to support different environments (local development, staging, production).

#### Scenario: Development environment uses localhost gateway
- **WHEN** frontend runs with `REACT_APP_API_URL` not set
- **THEN** all API requests target `http://localhost:8080/api/v1` by default

#### Scenario: Production environment uses custom gateway URL
- **WHEN** frontend builds with `REACT_APP_API_URL=https://api.example.com/api/v1`
- **THEN** all API requests target `https://api.example.com/api/v1`

### Requirement: All REST API Clients Route Through Gateway
The frontend REST API clients (auth, user, chat, presence, friend, notification, upload) SHALL all route their requests through the gateway. Individual service URLs (ports 8081-8088) SHALL NOT be used directly.

#### Scenario: Auth API calls route through gateway
- **WHEN** user calls `authApi.post('/login', credentials)`
- **THEN** request goes to `http://localhost:8080/api/v1/auth/login` (not `http://localhost:8081/api/v1/auth/login`)

#### Scenario: Chat API calls route through gateway
- **WHEN** user sends message via `chatApi.post('/messages', payload)`
- **THEN** request goes to `http://localhost:8080/api/v1/chat/messages` (not `http://localhost:8083/api/v1/chat/messages`)

#### Scenario: User API calls route through gateway
- **WHEN** user calls `userApi.get('/profile')`
- **THEN** request goes to `http://localhost:8080/api/v1/user/profile` (not `http://localhost:8082/api/v1/user/profile`)

### Requirement: Gateway Configuration is Single Source of Truth
The API configuration in `src/config/api.config.ts` SHALL define the gateway base URL in a single location. All API client instances created in `src/api/clients.ts` SHALL use this centralized URL.

#### Scenario: Configuration file defines single base URL
- **WHEN** developer reads `src/config/api.config.ts`
- **THEN** they find a single `API_BASE_URL` or `API_GATEWAY_URL` variable (not individual service URLs)

#### Scenario: All clients reference centralized configuration
- **WHEN** a new API client is created (e.g., `notificationApi`)
- **THEN** it uses the centralized gateway URL from configuration, not a hardcoded service URL

### Requirement: JWT Authentication Header Included in All Requests
All REST API requests through the gateway SHALL automatically include the JWT authentication bearer token in the `Authorization` header.

#### Scenario: Authorization header sent with authenticated requests
- **WHEN** user is logged in and makes an API request
- **THEN** the request includes `Authorization: Bearer <jwt_token>` header
- **AND** the token is read from localStorage (`access_token`)

### Requirement: API Client Error Handling Works Through Gateway
API clients SHALL continue to provide consistent error handling and token refresh logic regardless of whether requests route through the gateway or directly to services.

#### Scenario: Token refresh triggered on 401 response
- **WHEN** an API request receives a 401 Unauthorized response
- **THEN** system attempts to refresh the token using the refresh API
- **AND** the original request is retried with the new token
