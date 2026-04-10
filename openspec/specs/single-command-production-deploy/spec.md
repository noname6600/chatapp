## Purpose
Define the requirement for a single documented Docker Compose command that starts the complete production stack in a deterministic, reproducible way so operators can deploy the application without additional manual steps.

## Requirements

### Requirement: Single command starts production stack
The system SHALL provide one documented Docker Compose command that starts all required production components for frontend delivery, API delivery, and reverse proxy ingress on VPS.

#### Scenario: Operator starts stack on a prepared VPS
- **WHEN** the operator runs the documented production compose startup command with a valid production environment file on a prepared VPS
- **THEN** backend services, frontend runtime, and reverse proxy containers are created and started without requiring additional manual startup commands
- **AND** the startup command is executable as a single compose invocation

#### Scenario: Required component is missing from compose definition
- **WHEN** a required production component is omitted from the production compose definition
- **THEN** the deployment definition fails validation and SHALL be considered non-compliant with this requirement

### Requirement: Production startup flow is reproducible
The deployment process SHALL include a deterministic startup and verification sequence so different operators can achieve the same runtime outcome.

#### Scenario: Two operators run deployment with same inputs
- **WHEN** two operators deploy using the same compose file revision and equivalent env inputs
- **THEN** both deployments produce equivalent service topology and externally reachable frontend/API domains

#### Scenario: Startup verification is executed
- **WHEN** the compose startup process finishes
- **THEN** documented smoke checks SHALL verify frontend reachability at chatweb.nani.id.vn, API reachability at api.chatweb.nani.id.vn, and baseline authentication path availability
