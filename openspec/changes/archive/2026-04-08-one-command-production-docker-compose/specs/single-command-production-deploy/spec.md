## ADDED Requirements

### Requirement: Single command starts production stack
The system SHALL provide one documented Docker Compose command that starts all required production components for frontend delivery, API delivery, and reverse proxy ingress.

#### Scenario: Operator starts stack on a prepared VPS
- **WHEN** the operator runs the documented production compose startup command with a valid production environment file
- **THEN** backend services, frontend runtime, and reverse proxy containers are created and started without requiring additional manual startup commands

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
- **THEN** documented smoke checks SHALL verify frontend reachability, API reachability, and baseline authentication path availability
