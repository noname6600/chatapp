## ADDED Requirements

### Requirement: Local service startup script launches services in separate windows
The system SHALL provide a PowerShell script `chatappBE/scripts/start-services-local.ps1` that launches one or more backend services, each in a separate PowerShell window titled with the service name, using the command `gradlew :xxx-service:bootRun --args=--spring.profiles.active=local` from the `chatappBE/` working directory.

#### Scenario: Start all services with no arguments
- **WHEN** a developer runs `.\scripts\start-services-local.ps1` from `chatappBE/` with no arguments (or `-All`)
- **THEN** 8 new PowerShell windows open, one per service (auth, user, chat, presence, friendship, notification, upload, gateway)
- **AND** each window title shows the service name
- **AND** each window executes the bootRun command with `--args=--spring.profiles.active=local`

#### Scenario: Start a subset of services via named switches
- **WHEN** a developer runs `.\scripts\start-services-local.ps1 -Auth -Chat -Gateway`
- **THEN** exactly 3 new PowerShell windows open for auth-service, chat-service, and gateway-service
- **AND** no other service windows are opened

#### Scenario: Script prints Docker infra reminder
- **WHEN** the script is invoked
- **THEN** the launching terminal prints a reminder that `docker-compose.local.yml` must be running before services start

### Requirement: Local service startup batch wrapper delegates to PowerShell script
The system SHALL provide `chatappBE/scripts/start-services-local.bat` that invokes `start-services-local.ps1` via `powershell -ExecutionPolicy Bypass -File`, forwarding all arguments, so developers using cmd can start services without manually typing the PowerShell invocation.

#### Scenario: Batch file starts services from cmd
- **WHEN** a developer runs `scripts\start-services-local.bat` from a cmd prompt in `chatappBE/`
- **THEN** the PowerShell script is executed with the same behavior as running it directly from PowerShell
