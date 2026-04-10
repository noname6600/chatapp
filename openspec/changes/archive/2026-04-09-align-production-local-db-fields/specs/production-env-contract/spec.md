## MODIFIED Requirements

### Requirement: Production environment variables are explicitly defined
The system SHALL define a documented production environment contract containing all required runtime variables for compose orchestration and service configuration, including explicit host mapping for chatweb.nani.id.vn and api.chatweb.nani.id.vn, and DB-backed service keys that match local contract field names.

#### Scenario: Operator prepares production env file
- **WHEN** an operator follows deployment documentation
- **THEN** the required variable list includes frontend host, API host, CORS origins, API or WebSocket endpoints, and required secrets or credentials
- **AND** database field names for DB-backed services match the local contract key names

#### Scenario: Required variable is missing
- **WHEN** startup is attempted with a missing required production variable
- **THEN** deployment validation SHALL fail or produce an explicit runtime error that identifies the missing variable class
