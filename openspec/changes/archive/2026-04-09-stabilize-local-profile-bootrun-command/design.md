## Context

Local native startup is expected to work with a single command pattern such as `.\gradlew :auth-service:bootRun --args="--spring.profiles.active=local"`. In practice, startup reliability still depends on whether each service has complete local profile values for required integrations (database, Kafka, Redis, auth JWKS, inter-service URLs, and selected third-party values).

This is a cross-service concern because local profile readiness must be consistent across all backend services, not only auth-service. The runbook also needs to codify command-first startup and troubleshooting for missing local variables.

## Goals / Non-Goals

**Goals:**
- Ensure representative local startup works via command-driven profile activation (`--spring.profiles.active=local`) without dotenv shell preprocessing.
- Complete and align `application-local.yaml` values across backend services for local hybrid topology.
- Define deterministic verification and troubleshooting steps for missing local values.

**Non-Goals:**
- Replace production environment contracts with checked-in local values.
- Introduce new runtime dependencies or dotenv libraries.
- Change core service topology, ports, or compose architecture beyond local profile alignment.

## Decisions

### Decision: Standardize on command-activated local profile startup
The implementation will support direct Gradle startup with `--args="--spring.profiles.active=local"` as the canonical invocation for cmd users.

Alternative considered: rely only on `SPRING_PROFILES_ACTIVE` environment variable.
Why not: command arguments are explicit per process, reduce shell-state leakage, and match the user's requested workflow.

### Decision: Keep base config environment-oriented; complete local profile overrides only
Each service will keep environment-driven defaults in `application.yaml`, while local-only resolvable values live in `application-local.yaml`.

Alternative considered: harden all local values directly in base config.
Why not: it increases risk of local defaults leaking into non-local deployments.

### Decision: Add runbook checks for missing-variable diagnostics
The local runbook will explicitly include command usage, required local profile assumptions, and a troubleshooting path for unresolved placeholders.

Alternative considered: no documentation update.
Why not: unresolved placeholder errors are common and require consistent operator guidance.

## Risks / Trade-offs

- Drift between local profile values and local compose ports -> Mitigation: include explicit parity checks in verification steps.
- Checked-in local third-party values may become stale -> Mitigation: document override methods and expected placeholders.
- Services may still fail if external dependencies are down -> Mitigation: keep startup order and infra health checks in runbook.

## Migration Plan

1. Audit each backend service for missing local profile values that block command-based startup.
2. Update local profile files with consistent local hybrid endpoints and required values.
3. Update runbook command section to prefer explicit profile args and include troubleshooting for missing values.
4. Validate representative services using cmd with one-line Gradle command and record expected startup signals.

## Open Questions

- Which third-party credentials should remain concrete local development values versus placeholders requiring manual override?