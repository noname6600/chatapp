## ADDED Requirements

### Requirement: Gateway SHALL Exempt Auth Bootstrap Endpoints from JWT Enforcement
The gateway MUST allow unauthenticated access to configured auth bootstrap endpoints used for login, register, and token refresh.

#### Scenario: Login endpoint bypasses JWT check
- **WHEN** a client sends a login request to a configured auth bootstrap endpoint without Authorization header
- **THEN** the gateway forwards the request to the backend instead of returning 401

#### Scenario: Register endpoint bypasses JWT check
- **WHEN** a client sends a register request to a configured auth bootstrap endpoint without Authorization header
- **THEN** the gateway forwards the request to the backend instead of returning 401

### Requirement: Gateway SHALL Enforce JWT on Protected Endpoints
The gateway MUST reject unauthenticated requests to protected endpoints with 401.

#### Scenario: Protected endpoint requires token
- **WHEN** a client requests a protected endpoint without a valid access token
- **THEN** the gateway responds with 401

### Requirement: Gateway SHALL Emit Structured 401 Rejection Reasons
The gateway MUST produce machine-readable rejection reason codes for authentication failures in logs and metrics.

#### Scenario: Invalid token reason is logged
- **WHEN** a protected request is rejected due to token validation failure
- **THEN** the gateway records a reason code representing the failure class (for example `missing_token`, `invalid_signature`, `expired_token`, or `claim_mismatch`)

#### Scenario: Route policy mismatch is logged
- **WHEN** an endpoint expected to be public auth is rejected by auth middleware
- **THEN** the gateway records a reason code indicating route policy mismatch
