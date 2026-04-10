# local-hybrid-runtime-runbook

## Purpose
Define the requirement for a local development runbook that starts only infrastructure dependencies in Docker while backend application services run natively from the developer machine.

## Requirements
### Requirement: Local hybrid runbook defines infrastructure-only Docker startup
The system documentation MUST define a hybrid local runtime mode where Docker is used only for infrastructure dependencies and excludes backend application service containers.

#### Scenario: Infrastructure-only startup command is documented
- **WHEN** a developer follows the hybrid local runbook
- **THEN** the runbook provides an explicit Docker Compose command that starts only `postgres`, `redis`, `zookeeper`, and `kafka` from local compose configuration
- **AND** the runbook states that application service containers are not started in this mode

### Requirement: Local hybrid runbook includes CLI commands for each backend service
The hybrid local runbook MUST include explicit startup CLI commands for each backend service module so developers can run services natively with Gradle `bootRun`.

#### Scenario: Service command coverage is complete
- **WHEN** a developer reads the service startup section
- **THEN** the runbook includes commands for `auth-service`, `user-service`, `chat-service`, `presence-service`, `friendship-service`, `notification-service`, `upload-service`, and `gateway-service`
- **AND** each command is executable from the documented working directory

### Requirement: Local hybrid runbook includes infrastructure and startup verification commands
The hybrid local runbook MUST include command-line checks that verify infrastructure readiness and service health during startup.

#### Scenario: Operator verifies infra and service readiness
- **WHEN** a developer executes the verification section
- **THEN** the runbook provides Redis, Kafka, and PostgreSQL health or reachability checks
- **AND** the runbook provides at least one health-check command for gateway or backend service availability

### Requirement: Local hybrid runbook defines deterministic startup order and recovery commands
The hybrid local runbook MUST define the recommended service startup order and include commands for stop/cleanup and restart recovery.

#### Scenario: Startup order reduces dependency failures
- **WHEN** services are launched following documented sequence
- **THEN** auth and dependency-critical services are started before dependent services and gateway
- **AND** the runbook includes explicit commands to stop infrastructure containers and clear stale process conflicts