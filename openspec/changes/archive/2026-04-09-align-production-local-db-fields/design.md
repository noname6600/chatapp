## Context

Local and production configuration currently diverge in how database environment keys are named and consumed, especially after introducing per-service database isolation. This drift increases operator error risk during promotion from local to production and causes unnecessary contract translation work in compose and runbooks.

The requested direction is to keep local and production DB field names identical while allowing values to differ by environment (hosts, passwords, ports, secrets). This is a cross-cutting change touching env contracts, compose wiring, and deployment documentation.

## Goals / Non-Goals

**Goals:**
- Define one canonical DB variable naming contract shared by local and production.
- Ensure production compose and runtime wiring consume the same DB field keys as local.
- Preserve environment-specific values so production security and topology remain independent.
- Add explicit runbook checks that verify key parity between local and production contracts.

**Non-Goals:**
- Changing service business logic or HTTP/WebSocket APIs.
- Forcing local and production to use the same credential values.
- Introducing new database engines or changing service ownership boundaries.

## Decisions

### Decision 1: Canonical per-service DB key set
Use the same key family in both local and production for each DB-backed service:
- `<SERVICE>_DATABASE_NAME`
- `<SERVICE>_DATABASE_USER`
- `<SERVICE>_DATABASE_PASSWORD`
- `<SERVICE>_DATABASE_URL` where runtime expects URL inputs

Rationale: This eliminates contract drift and reduces operator translation errors.

Alternative considered: Keep production aliases and map them in compose. Rejected because alias maintenance reintroduces drift risk.

### Decision 2: Values remain environment-specific
Only key names are standardized. Values remain environment dependent (different hosts/passwords/ports for local vs production).

Rationale: Parity of keys improves reliability; parity of values would weaken production hardening and is not required.

Alternative considered: Full value parity between local and production. Rejected for security and topology reasons.

### Decision 3: Verify parity in runbook as a required step
Deployment documentation includes a deterministic parity checklist comparing local and production DB key sets before startup.

Rationale: Prevents silent misconfiguration and catches missing production keys early.

Alternative considered: Rely on compose runtime failures only. Rejected because failures occur late and can be noisy.

## Risks / Trade-offs

- [Risk] Existing deployments may still contain legacy DB key names.
  Mitigation: Keep explicit migration guidance and preflight validation commands.

- [Risk] Teams may assume values must match between local and production.
  Mitigation: Document that only keys must match; values are environment-specific.

- [Trade-off] Stricter env contract may require one-time edits to production env templates.
  Mitigation: Provide exact key list and startup checks in runbooks.

## Migration Plan

1. Update production env contract docs to use local-equivalent DB key names.
2. Update production compose/runtime mappings to consume canonical keys.
3. Update runbook parity verification steps for local vs production key sets.
4. Validate compose rendering in both environments.
5. Rollback path: restore prior env key references and rerun compose config validation.

## Open Questions

- Should legacy production DB key aliases be temporarily supported during transition, or removed immediately?
- Should a CI lint check enforce local/production DB key parity in env examples?
