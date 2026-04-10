## MODIFIED Requirements

### Requirement: Auth-service SHALL support email/password authentication
Auth-service SHALL allow users to authenticate with email and password in addition to existing Google-based login. For credential failures, auth-service SHALL return a deterministic structured error with a user-facing wrong-credentials message contract. Auth-service SHALL NOT return authentication success when account identity exists but linked user provisioning is incomplete.

#### Scenario: Email/password login succeeds
- **WHEN** user submits valid email/password credentials and the linked user profile is present
- **THEN** auth-service issues valid access/refresh tokens

#### Scenario: Invalid credentials are rejected with stable message contract
- **WHEN** user submits invalid email/password credentials
- **THEN** auth-service rejects authentication with a structured error payload containing a deterministic `message` for wrong credentials
- **AND** the response SHALL NOT be a success payload

#### Scenario: Incomplete account-user linkage is rejected
- **WHEN** credential validation passes but linked user provisioning is missing or failed
- **THEN** auth-service rejects authentication with an explicit incomplete-account error contract
- **AND** auth-service SHALL NOT issue access or refresh tokens
