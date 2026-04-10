# forgot-password-recovery Specification

## Purpose
Defines forgot-password initiation, secure reset confirmation, and login recovery entrypoint behavior.

## Requirements

### Requirement: Forgot-password request flow SHALL issue reset tokens by email
The system SHALL provide forgot-password initiation where user submits email and receives a reset link/token through email delivery.

#### Scenario: Forgot-password request accepted for existing email
- **WHEN** user submits a registered email in forgot-password form
- **THEN** auth-service issues reset token and sends reset email

#### Scenario: Forgot-password request does not reveal account existence
- **WHEN** user submits an unregistered email
- **THEN** API returns a generic success-style response without disclosing whether account exists

### Requirement: Password reset confirmation flow SHALL securely update password
The system SHALL validate reset token and allow setting a new password according to password policy.

#### Scenario: Reset with valid token succeeds
- **WHEN** user submits valid token and compliant new password
- **THEN** password is updated and old reset token becomes invalid

#### Scenario: Reset token replay is prevented
- **WHEN** previously used reset token is submitted again
- **THEN** auth-service rejects token as invalid/consumed

#### Scenario: Expired reset token is rejected
- **WHEN** user submits an expired reset token
- **THEN** auth-service rejects reset and prompts to request a new reset link

### Requirement: Login page SHALL include forgot-password entrypoint
The login page SHALL expose forgot-password entrypoint and route users through request and reset confirmation UX.

#### Scenario: Login page links to forgot-password flow
- **WHEN** user views login form
- **THEN** a Forgot Password action is visible and navigates to recovery flow

#### Scenario: Recovery completion returns user to login
- **WHEN** password reset completes successfully
- **THEN** user is redirected to login and can sign in with new password