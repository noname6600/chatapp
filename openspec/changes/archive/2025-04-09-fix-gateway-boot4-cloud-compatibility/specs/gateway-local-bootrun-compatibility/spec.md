## ADDED Requirements

### Requirement: Gateway local bootRun starts successfully under local profile
The system SHALL allow gateway-service to start via Gradle bootRun with local profile activation using `--args=--spring.profiles.active=local`, and the process SHALL complete Spring context initialization and bind to the configured local gateway port.

#### Scenario: Gateway boots with local profile args
- **WHEN** an operator runs `gradlew.bat :gateway-service:bootRun --args=--spring.profiles.active=local` from `chatappBE`
- **THEN** gateway-service reaches application started state without bootstrap class-resolution failure
- **AND** gateway-service binds to local port 8080

### Requirement: Gateway compatibility fix distinguishes dependency/bootstrap failures from command syntax errors
The system SHALL provide diagnostics and verification that separate command-format mistakes from framework compatibility failures.

#### Scenario: Correct command form but compatibility failure is reported explicitly
- **WHEN** the local command form is valid and gateway startup fails due to dependency/bootstrap incompatibility
- **THEN** troubleshooting output classifies the issue as compatibility/bootstrap, not command syntax
- **AND** remediation points to gateway dependency/config compatibility steps

### Requirement: Auth and gateway local baseline is verifiable for launcher workflow
The system SHALL support a local baseline where auth-service and gateway-service can both be started with local profile commands used by launcher verification.

#### Scenario: Auth and gateway startup verification succeeds
- **WHEN** infrastructure is running and operator starts auth-service and gateway-service with local profile commands
- **THEN** auth-service binds to port 8081 and gateway-service binds to port 8080
- **AND** gateway health endpoint is reachable locally