## ADDED Requirements

### Requirement: CORS policy enforced at gateway for all routes
The gateway SHALL be the single point of CORS enforcement for all downstream services. Individual microservices SHALL NOT define their own CORS configuration.

#### Scenario: Allowed origin preflight request
- **WHEN** a browser sends an `OPTIONS` preflight request with an allowed origin
- **THEN** the gateway SHALL respond with appropriate `Access-Control-Allow-*` headers and HTTP 200

#### Scenario: Disallowed origin rejected
- **WHEN** a browser sends a request with an origin not in the allowed list
- **THEN** the gateway SHALL not include CORS headers and the browser SHALL block the response

#### Scenario: Allowed HTTP methods
- **WHEN** a CORS request uses GET, POST, PUT, PATCH, DELETE, or OPTIONS
- **THEN** the gateway SHALL allow the method via CORS headers

#### Scenario: Credentials allowed
- **WHEN** a CORS request includes `credentials: true`
- **THEN** the gateway SHALL respond with `Access-Control-Allow-Credentials: true`

#### Scenario: Authorization header exposed
- **WHEN** a CORS preflight includes `Authorization` in `Access-Control-Request-Headers`
- **THEN** the gateway SHALL include `Authorization` in `Access-Control-Allow-Headers`

### Requirement: CORS configuration is environment-driven
The list of allowed origins SHALL be configurable via environment variable to support different values for local, staging, and production environments.

#### Scenario: Allowed origins from environment
- **WHEN** `CORS_ALLOWED_ORIGINS` environment variable is set
- **THEN** the gateway SHALL use those values as the allowed origins list
