# gateway-jwt-auth

## Purpose
Gateway capability specification for gateway-jwt-auth.

## Requirements

### Requirement: Protected routes require valid JWT
The gateway SHALL validate the `Authorization: Bearer <token>` header on all routes except explicitly whitelisted public paths. Requests with a missing or invalid token SHALL be rejected before reaching downstream services.

#### Scenario: Valid JWT on protected route
- **WHEN** a client sends a request to `/api/users/**` with a valid `Authorization: Bearer <token>` header
- **THEN** the gateway SHALL forward the request to the downstream service

#### Scenario: Missing JWT on protected route
- **WHEN** a client sends a request to `/api/chat/**` without an `Authorization` header
- **THEN** the gateway SHALL return HTTP 401 and SHALL NOT forward the request downstream

#### Scenario: Expired JWT rejected
- **WHEN** a client sends a request with an expired JWT
- **THEN** the gateway SHALL return HTTP 401

#### Scenario: Invalid JWT signature rejected
- **WHEN** a client sends a request with a JWT signed by an unknown key
- **THEN** the gateway SHALL return HTTP 401

### Requirement: Public auth routes bypass JWT validation
Authentication endpoints SHALL be accessible without a JWT token.

#### Scenario: Login endpoint accessible without token
- **WHEN** a client sends `POST /api/auth/login` without an `Authorization` header
- **THEN** the gateway SHALL forward the request to auth-service and SHALL NOT return 401

#### Scenario: Register endpoint accessible without token
- **WHEN** a client sends `POST /api/auth/register` without an `Authorization` header
- **THEN** the gateway SHALL forward the request to auth-service

### Requirement: User identity forwarded to downstream services
After successful JWT validation, the gateway SHALL extract user identity claims and set them as request headers before forwarding.

#### Scenario: User ID header injected
- **WHEN** a valid JWT is validated containing a user ID claim
- **THEN** the gateway SHALL set `X-User-Id` header with the extracted user ID on the forwarded request

#### Scenario: Original Authorization header preserved
- **WHEN** JWT is validated successfully
- **THEN** the original `Authorization` header SHALL also be forwarded to the downstream service

