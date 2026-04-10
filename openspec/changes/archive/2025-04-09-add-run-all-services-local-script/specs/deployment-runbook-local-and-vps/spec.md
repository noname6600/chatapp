## MODIFIED Requirements

### Requirement: Unified deployment runbook covers local and VPS modes
The system documentation MUST define one unified deployment runbook that clearly separates local mode and VPS production mode while keeping terminology and step naming consistent, and it MUST document per-service database isolation setup and verification for DB-backed services in both modes, including DB field-name parity checks between local and production contracts. The local section MUST document both the single-service manual one-liner (`gradlew :xxx-service:bootRun --args=--spring.profiles.active=local`) and the multi-service convenience scripts (`scripts/start-services-local.ps1`, `scripts/start-services-local.bat`) as the recommended startup methods.

#### Scenario: Operator selects deployment mode without ambiguity
- **WHEN** an operator opens the deployment runbook
- **THEN** the runbook presents clear entry points for local mode and VPS production mode
- **AND** each step name and term is used consistently across both sections
- **AND** both sections include guidance to provision and verify service-specific database roles and credentials
- **AND** both sections include a deterministic check that production DB field names match local DB field names

#### Scenario: Operator starts multiple local services using the convenience script
- **WHEN** an operator is in `chatappBE/` and runs `.\scripts\start-services-local.ps1` (or `scripts\start-services-local.bat` from cmd)
- **THEN** the runbook documents the expected outcome: one terminal window per selected service opens
- **AND** the runbook notes that Docker infra (`docker-compose.local.yml`) must be running first
