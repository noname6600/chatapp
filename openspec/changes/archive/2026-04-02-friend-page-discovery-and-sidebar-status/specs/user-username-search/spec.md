## ADDED Requirements

### Requirement: User-service SHALL expose an exact-match username search endpoint
The system SHALL provide `GET /api/v1/users/search?username=<value>` (authenticated) that performs a case-insensitive exact lookup of the given username and returns the matching user's public profile, or HTTP 404 if no user with that username exists. The endpoint MUST require authentication and MUST NOT be accessible to unauthenticated callers.

#### Scenario: Exact username match returns public profile
- **WHEN** an authenticated user calls `GET /api/v1/users/search?username=alice`
- **AND** a user with username `alice` (case-insensitive) exists
- **THEN** the response is HTTP 200 with the matching user's `accountId`, `username`, `displayName`, and `avatarUrl`

#### Scenario: No match returns 404
- **WHEN** an authenticated user calls `GET /api/v1/users/search?username=doesnotexist`
- **AND** no user with that username exists
- **THEN** the response is HTTP 404

#### Scenario: Unauthenticated request is rejected
- **WHEN** an unauthenticated caller calls `GET /api/v1/users/search?username=alice`
- **THEN** the response is HTTP 401

#### Scenario: Missing username parameter is rejected
- **WHEN** an authenticated caller calls `GET /api/v1/users/search` without a `username` query parameter
- **THEN** the response is HTTP 400
