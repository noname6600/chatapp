## ADDED Requirements

### Requirement: Unified deployment runbook covers local and VPS modes
The system documentation MUST define one unified deployment runbook that clearly separates local mode and VPS production mode while keeping terminology and step naming consistent.

#### Scenario: Operator selects deployment mode without ambiguity
- **WHEN** an operator opens the deployment runbook
- **THEN** the runbook presents clear entry points for local mode and VPS production mode
- **AND** each step name and term is used consistently across both sections

### Requirement: VPS production startup is executable with one Docker Compose command
The production runbook MUST define a one-command startup that brings backend services, frontend runtime, and Nginx online on a prepared VPS.

#### Scenario: One-command startup succeeds on prepared VPS
- **WHEN** an operator runs the documented production startup command on a VPS with required environment values and Docker prerequisites installed
- **THEN** backend, frontend, and Nginx containers are started in a single compose operation
- **AND** no additional manual start commands are required for core application availability

### Requirement: Domain mapping is explicitly documented and verified
The runbook MUST define and verify domain routing so frontend is served from chatweb.nani.id.vn and API or WebSocket ingress is served from api.chatweb.nani.id.vn.

#### Scenario: Domain verification confirms expected host mapping
- **WHEN** post-start verification is performed
- **THEN** requests to chatweb.nani.id.vn resolve to frontend delivery
- **AND** requests to api.chatweb.nani.id.vn resolve to API or WebSocket ingress through Nginx and gateway

### Requirement: Runbook defines deterministic verification and rollback steps
The runbook MUST include deterministic post-start checks, failure triage flow, and rollback instructions for both local and VPS deployment modes.

#### Scenario: Verification and rollback steps are actionable
- **WHEN** a deployment check fails
- **THEN** the runbook provides ordered troubleshooting actions and expected signals
- **AND** the runbook provides rollback steps that restore the previously working deployment state
