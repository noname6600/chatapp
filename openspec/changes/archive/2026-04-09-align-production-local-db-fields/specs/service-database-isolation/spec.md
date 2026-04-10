## MODIFIED Requirements

### Requirement: Compose and runtime contracts SHALL expose service-scoped DB variables
Infrastructure and service runtime contracts MUST define explicit service-scoped database environment variables for database URL, username, and password, and local and production contracts MUST use the same variable key names for each DB-backed service.

#### Scenario: Compose config exposes service-scoped DB contract
- **WHEN** operators inspect local compose and environment examples
- **THEN** each DB-backed service has explicit database URL, username, and password variables
- **AND** variable naming and mapping are consistent across compose, service config, and runbook documentation
- **AND** the same service-scoped variable key names are used in production contracts
