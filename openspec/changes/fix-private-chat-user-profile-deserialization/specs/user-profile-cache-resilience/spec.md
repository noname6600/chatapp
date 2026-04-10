## MODIFIED Requirements

### Requirement: Self profile read SHALL remain available during cache outages
The system SHALL treat Redis/cache failures in the authenticated self-profile read path (`GET /api/v1/users/me`) as non-fatal and continue with repository-backed lookup, and downstream consumers of the resulting profile representation SHALL tolerate additive non-breaking JSON fields.

#### Scenario: Cache get throws Redis connection error
- **WHEN** the profile cache read for the authenticated account throws a Redis connection failure
- **THEN** the service continues by loading profile data from the repository
- **AND** the endpoint returns success when repository data exists

#### Scenario: Cache miss falls back to repository
- **WHEN** cache lookup returns no profile value for the authenticated account
- **THEN** the service queries the repository for the same account
- **AND** the endpoint returns the repository-backed profile response

#### Scenario: Repository profile includes additive fields for consumers
- **WHEN** profile representations used by downstream consumers include additive fields beyond currently mapped consumer DTO properties
- **THEN** consumers do not fail deserialization solely because of unknown additive fields
- **AND** required profile fields continue to determine functional correctness
