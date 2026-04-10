## MODIFIED Requirements

### Requirement: Unified deployment runbook covers local and VPS modes
The system documentation MUST define one unified deployment runbook that clearly separates local mode and VPS production mode while keeping terminology and step naming consistent, and it MUST document per-service database isolation setup and verification for DB-backed services in both modes, including DB field-name parity checks between local and production contracts.

#### Scenario: Operator selects deployment mode without ambiguity
- **WHEN** an operator opens the deployment runbook
- **THEN** the runbook presents clear entry points for local mode and VPS production mode
- **AND** each step name and term is used consistently across both sections
- **AND** both sections include guidance to provision and verify service-specific database roles and credentials
- **AND** both sections include a deterministic check that production DB field names match local DB field names

#### Scenario: Runbook distinguishes command syntax issues from gateway compatibility startup failures
- **WHEN** an operator uses the documented local one-line command format and gateway fails during bootstrap
- **THEN** troubleshooting guidance classifies the failure as gateway compatibility/bootstrap
- **AND** troubleshooting guidance preserves the documented command syntax recommendation and points to dependency/config compatibility remediation
