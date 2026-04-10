## ADDED Requirements

### Requirement: Join-from-invite-card enforces room join policy
The system SHALL apply authoritative room join eligibility checks for invite-card join actions before admitting the user.

#### Scenario: Authorized invite join succeeds
- **WHEN** eligible user triggers join from a valid invite card
- **THEN** backend admits the user to the room under existing policy constraints and returns success

#### Scenario: Unauthorized invite join is denied
- **WHEN** user triggers join from invite card but room policy denies access
- **THEN** backend rejects join and returns a policy/authorization error without membership change

### Requirement: Invite-based joins are idempotent and race-safe
Join operations triggered from invite cards SHALL be idempotent under retries and concurrent clicks.

#### Scenario: Existing member join request is treated as success
- **WHEN** invite-based join is requested for a user already in the room
- **THEN** backend returns successful already-member result and does not duplicate membership state

#### Scenario: Concurrent join requests create one membership outcome
- **WHEN** multiple invite-based join requests for the same user and room arrive concurrently
- **THEN** backend resolves to a single membership record with deterministic success response
