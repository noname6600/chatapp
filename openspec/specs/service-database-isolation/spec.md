# service-database-isolation Specification

## Purpose
TBD - created by archiving change service-database-isolation. Update Purpose after archive.
## Requirements
### Requirement: DB-backed services SHALL use dedicated database credentials
Each backend service with a PostgreSQL database dependency MUST use a service-specific database user and password instead of a shared database superuser credential.

#### Scenario: Service starts with dedicated credentials
- **WHEN** a DB-backed service starts in local or production-like runtime configuration
- **THEN** its connection settings reference a service-owned database username and password
- **AND** the configuration does not require shared `POSTGRES_USER` credentials for normal service operations

### Requirement: Database initialization SHALL create isolated roles and grants per service
Database bootstrap workflow MUST create distinct PostgreSQL roles and database grants for each DB-backed service database.

#### Scenario: Bootstrap enforces ownership boundaries
- **WHEN** initialization scripts provision database roles and databases
- **THEN** each service role has required privileges only on its own service database
- **AND** service roles do not have privileges on other service databases by default

### Requirement: Compose and runtime contracts SHALL expose service-scoped DB variables
Infrastructure and service runtime contracts MUST define explicit service-scoped database environment variables for database URL, username, and password, and local and production contracts MUST use the same variable key names for each DB-backed service.

#### Scenario: Compose config exposes service-scoped DB contract
- **WHEN** operators inspect local compose and environment examples
- **THEN** each DB-backed service has explicit database URL, username, and password variables
- **AND** variable naming and mapping are consistent across compose, service config, and runbook documentation
- **AND** the same service-scoped variable key names are used in production contracts

### Requirement: Isolation verification SHALL include role-authenticated connectivity checks
Operational verification procedures MUST include connectivity checks that authenticate with each service-owned database role.

#### Scenario: Role-authenticated checks validate isolation
- **WHEN** operators run post-bootstrap verification commands
- **THEN** each service role can connect to its own database successfully
- **AND** verification steps provide a deterministic way to detect missing roles, wrong passwords, or wrong grants

