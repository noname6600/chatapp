# deployment-runbook-local-and-vps

## Purpose
Define the requirement for a unified deployment runbook that covers both local development mode and VPS production mode with consistent terminology, one-command startup, verified domain mapping, and deterministic rollback procedures.
## Requirements
### Requirement: Unified deployment runbook covers local and VPS modes
The system documentation MUST define one unified deployment runbook that clearly separates local mode and VPS production mode while keeping terminology and step naming consistent, and it MUST document per-service database isolation setup and verification for DB-backed services in both modes, including DB field-name parity checks between local and production contracts, and it MUST include explicit command-first local startup guidance using `--spring.profiles.active=local` plus troubleshooting steps for missing local profile values, and it MUST reference a dedicated local hybrid runbook for infrastructure-only Docker startup plus manual backend service startup.

#### Scenario: Operator selects deployment mode without ambiguity
- **WHEN** an operator opens the deployment runbook
- **THEN** the runbook presents clear entry points for local mode and VPS production mode
- **AND** each step name and term is used consistently across both sections
- **AND** both sections include guidance to provision and verify service-specific database roles and credentials
- **AND** both sections include a deterministic check that production DB field names match local DB field names
- **AND** local mode includes one-line Gradle command examples using explicit local profile activation and missing-variable troubleshooting guidance
- **AND** local mode references a dedicated hybrid runbook containing infra and service startup commands

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

