## ADDED Requirements

### Requirement: Private Chat Initiation Resolves to a Conversation
The system SHALL support private chat initiation as a create-or-open operation that resolves to exactly one private conversation for the requesting user and target user.

#### Scenario: Opens existing private conversation
- **WHEN** user initiates a private chat with a target user and an existing private room already links the same two users
- **THEN** system returns success with the existing room identifier
- **AND** the client can navigate directly to that room

#### Scenario: Creates new private conversation when absent
- **WHEN** user initiates a private chat with a target user and no private room exists for the pair
- **THEN** system creates a new private room for the pair
- **AND** system returns success with the new room identifier

### Requirement: Private Chat Initiation Enforces Eligibility Rules
The system SHALL reject private chat initiation requests that violate eligibility or access rules and SHALL provide stable failure outcomes.

#### Scenario: Reject self-target initiation
- **WHEN** user initiates a private chat with their own user id as target
- **THEN** system rejects the request with a validation failure

#### Scenario: Reject unknown or unavailable target user
- **WHEN** user initiates a private chat with a target user id that does not exist or is not available for direct messaging
- **THEN** system rejects the request with a not-allowed or not-found failure

#### Scenario: Client receives actionable error outcome
- **WHEN** private chat initiation is rejected
- **THEN** response includes an error code/message category the client can map to user-facing feedback
