## MODIFIED Requirements

### Requirement: Login and Register SHALL Return Canonical Session Tokens
The system MUST return a canonical authentication success payload for both login and register flows only after account identity and linked user profile readiness are confirmed. For every successful authentication, the payload SHALL contain a non-empty access token and a non-empty refresh token with expiry metadata.

#### Scenario: Login success returns refresh token
- **WHEN** a user logs in with valid credentials and account-to-user linkage is complete
- **THEN** the response includes non-empty `accessToken` and non-empty `refreshToken`

#### Scenario: Register success returns refresh token
- **WHEN** a user registers successfully, automatic sign-in is enabled, and linked user provisioning completes
- **THEN** the response includes non-empty `accessToken` and non-empty `refreshToken`

#### Scenario: Incomplete provisioning blocks success tokens
- **WHEN** account creation succeeds but linked user provisioning is incomplete or fails
- **THEN** the system SHALL return an explicit incomplete-account error response
- **AND** no success response containing tokens SHALL be emitted
