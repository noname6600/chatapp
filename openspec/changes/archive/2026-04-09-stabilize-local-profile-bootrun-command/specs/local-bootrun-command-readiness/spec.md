## ADDED Requirements

### Requirement: Local profile startup must work with one-line Gradle command
The system MUST support local native startup with command-driven profile activation (`--spring.profiles.active=local`) for backend services without requiring shell preloading of `.env.local`.

#### Scenario: Operator starts auth-service with command profile argument
- **WHEN** an operator runs `.\gradlew :auth-service:bootRun --args="--spring.profiles.active=local"` from `chatappBE`
- **THEN** auth-service resolves required local datasource, Kafka, mail, OAuth, and frontend settings from local profile configuration
- **AND** startup does not fail due to unresolved local placeholders

### Requirement: Local profiles must include complete service-critical variables
The system MUST provide complete local profile values for service-critical integrations so local startup does not depend on dotenv injection for required properties.

#### Scenario: Service has required local values for dependencies
- **WHEN** a backend service starts with local profile
- **THEN** required dependencies (database, Kafka, Redis, auth JWKS, inter-service URLs, and service-specific third-party properties where applicable) resolve to local development values
- **AND** missing-value startup failures are avoided for documented local workflows

### Requirement: Local profile topology alignment must be deterministic
The system MUST align local profile endpoints with documented local hybrid topology including dedicated DB ports and localhost service endpoints.

#### Scenario: DB-backed service uses correct dedicated local DB mapping
- **WHEN** a DB-backed service starts in local profile
- **THEN** it uses the expected dedicated local DB host and port for that service
- **AND** it does not cross-connect to another service's database mapping