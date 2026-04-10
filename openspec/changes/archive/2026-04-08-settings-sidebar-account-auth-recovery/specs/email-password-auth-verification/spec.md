## ADDED Requirements

### Requirement: Auth-service SHALL support email/password authentication
Auth-service SHALL allow users to authenticate with email and password in addition to existing Google-based login.

#### Scenario: Email/password login succeeds
- **WHEN** user submits valid email/password credentials
- **THEN** auth-service issues valid access/refresh tokens

#### Scenario: Invalid credentials are rejected
- **WHEN** user submits invalid email/password credentials
- **THEN** auth-service rejects authentication with a secure generic error

### Requirement: One verified email SHALL map to one account identity
The system SHALL enforce unique verified email ownership across identity providers so the same email resolves to one account identity.

#### Scenario: Existing email account reuses same identity
- **WHEN** user authenticates via a provider using an email that already belongs to an existing verified account
- **THEN** auth-service links/reuses the same account identity instead of creating a duplicate account

#### Scenario: Duplicate email account creation is blocked
- **WHEN** registration or linking attempts to create another account with an already verified email
- **THEN** auth-service rejects duplicate identity creation

### Requirement: Email verification SHALL be required for email credential trust
Auth-service SHALL support email verification tokens and confirmation flow for email/password accounts.

#### Scenario: Verification email is sent
- **WHEN** new email/password account is created or verification is requested
- **THEN** auth-service issues a verification token and sends a confirmation email

#### Scenario: Verification token confirms email
- **WHEN** user opens a valid unexpired verification link
- **THEN** account email is marked verified and can be trusted for identity linking/login policy

#### Scenario: Expired or invalid verification token is rejected
- **WHEN** user opens expired/invalid verification link
- **THEN** auth-service rejects verification and provides resend path