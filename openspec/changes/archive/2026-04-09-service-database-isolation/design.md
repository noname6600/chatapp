## Context

Backend services currently map to separate logical PostgreSQL databases but mostly share common database credentials and bootstrap assumptions. This creates weak tenant boundaries between services and makes local runtime behavior diverge from stronger production hardening practices. The requested change requires all related artifacts to move toward service-owned database identities, update local compose contracts, and align operator runbooks for local hybrid and deployment flows.

The change is cross-cutting because it impacts compose environment variables, service connection settings, database initialization behavior, and operational verification commands. It also introduces migration risk for existing local and VPS secrets and startup workflows.

## Goals / Non-Goals

**Goals:**
- Define a consistent per-service database isolation model where each DB-backed service uses a dedicated database user and password.
- Require compose and service runtime configuration to reference service-specific credentials instead of shared superuser values.
- Document deterministic bootstrap and verification commands for isolated DB users and databases.
- Keep local-hybrid and deployment runbooks synchronized with the new DB isolation contract.

**Non-Goals:**
- No mandate to create one PostgreSQL container per service.
- No immediate requirement to change non-PostgreSQL services to use separate persistence engines.
- No schema redesign inside each service database.

## Decisions

1. Keep one PostgreSQL server, isolate by database user per service
- Decision: use one PostgreSQL runtime with separate databases and dedicated users per DB-backed service.
- Rationale: this improves isolation without multiplying operational overhead of multiple Postgres instances.
- Alternative considered: one PostgreSQL instance/container per service.
  - Rejected for now due to higher resource and maintenance cost.

2. Standardize credentials as service-scoped environment variables
- Decision: define service-scoped DB env keys and compose mappings for each DB-backed service.
- Rationale: explicit ownership minimizes accidental cross-service DB access.
- Alternative considered: preserve shared `POSTGRES_USER` in app services.
  - Rejected due to weak least-privilege posture.

3. Make runbooks enforce verification of role/database ownership
- Decision: include commands that verify both DB existence and access by the corresponding service user.
- Rationale: this catches misconfiguration early and is executable in local and VPS contexts.
- Alternative considered: document only DB existence checks.
  - Rejected because existence does not validate authorization boundaries.

4. Phase migration through compatibility window
- Decision: tasks require introducing isolated credentials and updating docs/config with explicit migration steps.
- Rationale: avoids abrupt breakage in environments still using shared credentials.
- Alternative considered: hard cutover with no transition notes.
  - Rejected due to high rollout risk.

## Risks / Trade-offs

- [Risk] Existing environments may fail startup if new DB secrets are missing. -> Mitigation: add explicit preflight checks and fallback migration instructions in runbooks.
- [Risk] Legacy scripts that assume shared `POSTGRES_USER` may break. -> Mitigation: update scripts and compose docs together in one change.
- [Trade-off] More env variables increase configuration complexity. -> Mitigation: provide a canonical variable matrix and copy-ready examples.
- [Risk] Incorrect grants could silently permit cross-service access. -> Mitigation: add role-level verification commands per service DB.

## Migration Plan

1. Define new service-specific DB credential contract and update compose references.
2. Update DB init/bootstrap workflow to create roles, databases, and grants for each DB-backed service.
3. Update local hybrid and deployment runbooks with create/check commands.
4. Validate service startup and DB access boundaries in local compose and hybrid workflows.
5. Remove or deprecate shared credential usage after successful migration window.

## Open Questions

- Should presence-service adopt PostgreSQL isolation now if it remains Redis/Kafka-only today?
- Should upload-service remain DB-less, or should future metadata persistence predefine an isolated DB role now?
- Do we require credential rotation guidance in this change, or defer to a separate ops hardening change?
