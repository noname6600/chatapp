## ADDED Requirements

### Requirement: Password change endpoint handles missing authentication principal safely
The system SHALL NOT throw server-side null-reference errors when handling `POST /api/v1/auth/password/change` requests without a resolved `JwtPrincipal`.

#### Scenario: Missing principal does not crash endpoint
- **WHEN** a password change request reaches the endpoint and authentication principal is null
- **THEN** the endpoint returns a deterministic unauthorized/auth failure response
- **AND** the endpoint does not throw `NullPointerException`

### Requirement: Authenticated password change flow remains functional
The system SHALL preserve existing successful behavior for authenticated password change requests after adding principal-null protection.

#### Scenario: Authenticated principal changes password successfully
- **WHEN** an authenticated request includes a valid `JwtPrincipal` and valid password-change payload
- **THEN** the endpoint invokes password change service with the principal account ID
- **AND** returns success response according to current API contract
