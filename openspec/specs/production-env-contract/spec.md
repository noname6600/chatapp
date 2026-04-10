## Purpose
Define the requirement for an explicit, documented production environment contract so all required runtime variables are declared and consumed from environment inputs rather than hardcoded values, enabling safe domain changes and credential rotation.
## Requirements
### Requirement: Production environment variables are explicitly defined
The system SHALL define a documented production environment contract containing all required runtime variables for compose orchestration and service configuration, including explicit host mapping for chatweb.nani.id.vn and api.chatweb.nani.id.vn, and DB-backed service keys that match local contract field names.

#### Scenario: Operator prepares production env file
- **WHEN** an operator follows deployment documentation
- **THEN** the required variable list includes frontend host, API host, CORS origins, API or WebSocket endpoints, and required secrets or credentials
- **AND** database field names for DB-backed services match the local contract key names

#### Scenario: Required variable is missing
- **WHEN** startup is attempted with a missing required production variable
- **THEN** deployment validation SHALL fail or produce an explicit runtime error that identifies the missing variable class

### Requirement: Service configuration consumes environment contract
Backend and frontend runtime configuration SHALL source production values from environment variables instead of hardcoded domain-specific constants.

#### Scenario: Production domain value changes
- **WHEN** an operator updates the domain-related environment value
- **THEN** the resulting deployment reflects the new domain behavior without source-code modifications

#### Scenario: Credential rotation
- **WHEN** secrets or credentials are rotated in environment input
- **THEN** services start with updated values and no hardcoded secret override is required

