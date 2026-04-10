## Purpose
Define the canonical token contract for authentication flows: every successful login or register response MUST include both an access token and a refresh token with expiry metadata, the issued access token MUST be immediately usable against protected endpoints, and any success response missing a refresh token MUST be treated as an observable contract violation.

## Requirements

### Requirement: Login and Register SHALL Return Canonical Session Tokens
The system MUST return a canonical authentication success payload for both login and register flows. For every successful authentication, the payload SHALL contain a non-empty access token and a non-empty refresh token with expiry metadata.

#### Scenario: Login success returns refresh token
- **WHEN** a user logs in with valid credentials
- **THEN** the response includes non-empty `accessToken` and non-empty `refreshToken`

#### Scenario: Register success returns refresh token
- **WHEN** a user registers successfully and automatic sign-in is enabled
- **THEN** the response includes non-empty `accessToken` and non-empty `refreshToken`

### Requirement: Issued Access Tokens SHALL Be Immediately Usable
The system MUST guarantee that an access token issued from successful login/register is accepted by protected endpoints immediately after issuance, subject to normal authorization rules.

#### Scenario: Immediate protected call after login succeeds
- **WHEN** a client calls a protected endpoint using an access token obtained from a successful login within the token validity window
- **THEN** the request is not rejected with 401 due to token format, issuer, audience, or clock-skew mismatch

### Requirement: Missing Refresh Token on Success SHALL Be Treated as Contract Violation
The system MUST treat any successful login/register response without refresh token as a contract violation and MUST emit an operational error signal.

#### Scenario: Contract violation is observable
- **WHEN** a success response is constructed without a refresh token
- **THEN** the system records a structured error event indicating `missing_refresh_token_on_success`
