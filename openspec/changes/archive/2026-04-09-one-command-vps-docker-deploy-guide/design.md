## Context

Deployment guidance currently spans mixed local and production instructions with inconsistent terms, causing operators to miss prerequisites or execute commands in the wrong order. The system already has production-oriented capabilities for one-command startup, environment contracts, Nginx routing, and gateway proxy compatibility, but the wording and acceptance expectations are not fully aligned to the target domains and VPS-first operational flow.

Stakeholders include operators deploying to VPS, backend/frontend developers validating local parity, and maintainers responsible for release quality.

## Goals / Non-Goals

**Goals:**
- Provide a consistent requirement set for local and VPS deployment guidance.
- Require a one-command VPS Docker Compose startup that brings backend, frontend, and Nginx online together.
- Align deployment requirements to explicit production domains:
  - chatweb.nani.id.vn for frontend
  - api.chatweb.nani.id.vn for API and WebSocket ingress
- Define environment completeness and verification checkpoints so deploy outcomes are predictable.
- Ensure wording consistency across deployment-related capabilities to reduce ambiguity.

**Non-Goals:**
- Re-architecting microservice internals or API business logic.
- Replacing Docker Compose with Kubernetes or another orchestrator.
- Defining certificate issuance automation internals beyond required deploy behavior.

## Decisions

1. Introduce one new capability for operator runbook behavior.
- Decision: Add deployment-runbook-local-and-vps as a dedicated capability.
- Rationale: Existing capabilities describe system behavior but do not fully encode operator-facing sequencing, troubleshooting, and rollback expectations across local and VPS modes.
- Alternative considered: Expand only existing capabilities.
- Why not chosen: It would blur infrastructure behavior requirements with runbook process requirements and reduce maintainability.

2. Modify existing production capabilities instead of duplicating them.
- Decision: Update single-command-production-deploy, production-env-contract, nginx-domain-and-tls-routing, and gateway-production-proxy-integration.
- Rationale: These already represent the correct behavior boundaries and only need clearer, stricter requirements tied to the intended production domains and one-command deploy expectation.
- Alternative considered: Create parallel new capabilities for each domain concern.
- Why not chosen: Would fragment contracts and create overlap.

3. Treat wording consistency as a requirements-level quality constraint.
- Decision: Require deterministic sequences, named preflight checks, explicit domain mappings, and explicit verification outputs.
- Rationale: Ambiguous wording has operational impact comparable to missing behavior requirements.
- Alternative considered: Leave wording cleanup for implementation docs only.
- Why not chosen: Without spec-level constraints, wording drift can reappear.

## Risks / Trade-offs

- Domain assumptions may change later (for example staging hostname updates) -> Mitigation: keep values environment-driven while requiring explicit default production mappings.
- Operators may still skip verification checks -> Mitigation: require post-start smoke checks and readiness criteria in runbook requirements.
- Scope growth into tooling automation (TLS scripts, CI deploy) -> Mitigation: keep this change focused on deploy contract and runbook behavior; defer automation specifics.
- Modified requirements can conflict with previously interpreted behavior -> Mitigation: provide full MODIFIED requirement blocks with precise scenario outcomes.

## Migration Plan

1. Add delta specs for one new capability and four modified capabilities.
2. Implement or update deployment text/config artifacts to satisfy the new and modified requirements.
3. Validate local and VPS paths against scenario-level acceptance checks.
4. Rollback strategy: if deployment guidance updates cause confusion, revert the change set and restore the last known archived deploy instructions while preserving environment files.

## Open Questions

- Should TLS certificate renewal behavior be explicitly required in this change or handled in a separate operational capability?
- Should local deployment require optional Nginx simulation, or remain direct service access for developer speed?
- Should the one-command production startup be hard-pinned to a specific compose file path convention?