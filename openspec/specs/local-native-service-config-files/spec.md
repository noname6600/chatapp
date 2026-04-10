# local-native-service-config-files Specification

## Purpose
TBD - created by archiving change add-local-application-files-for-gradle-bootrun. Update Purpose after archive.
## Requirements
### Requirement: Native local bootRun profile files exist for backend services
The system MUST provide checked-in local Spring profile configuration files for each backend service used in hybrid local development so that `gradlew.bat :<service>:bootRun` can start with the local profile without requiring shell-based `.env.local` loading.

#### Scenario: Operator starts a backend service from plain cmd
- **WHEN** an operator runs `gradlew.bat :auth-service:bootRun` from `chatappBE` with `SPRING_PROFILES_ACTIVE=local`
- **THEN** auth-service resolves its required local datasource, Kafka, mail, OAuth, and frontend settings from checked-in local Spring configuration files
- **AND** startup does not require parsing `.env.local` in the shell first

### Requirement: Local profile values match documented hybrid infrastructure
The system MUST align each service's local profile configuration with the local hybrid topology documented by the runbook, including dedicated database ports for DB-backed services and local hostnames or ports for Redis, Kafka, gateway, and inter-service calls.

#### Scenario: DB-backed service connects to its dedicated local database
- **WHEN** an operator starts a DB-backed service in the local profile
- **THEN** the service uses its documented dedicated local PostgreSQL database name, credentials, and port
- **AND** it does not target another service's database container or a shared application database

### Requirement: Local profile files remain local-development scoped
The system MUST keep local native startup configuration in local profile files so container and production behavior continue to use environment-driven configuration contracts.

#### Scenario: Non-local environments continue using environment contracts
- **WHEN** the application starts without the local profile in containerized or production-like environments
- **THEN** the service continues resolving runtime configuration from its existing environment-based application configuration
- **AND** the local profile file does not become the required source of configuration outside local development

