## ADDED Requirements

### Requirement: Local and production DB contracts SHALL use identical field names
The system SHALL define one canonical set of database environment variable keys for DB-backed services and SHALL use the same keys in local and production contracts.

#### Scenario: Operator compares env contracts
- **WHEN** an operator compares local and production environment contracts for DB-backed services
- **THEN** the field names are identical across both environments for each service DB contract key
- **AND** no production-only alias keys are required for normal startup

### Requirement: DB field values MAY differ by environment
The system SHALL allow local and production to use different values for the same DB contract keys while preserving key-name parity.

#### Scenario: Production keeps independent credential values
- **WHEN** production deploys with different database hosts, passwords, or ports than local
- **THEN** startup succeeds using the same field names
- **AND** no source code or compose key renaming is required
