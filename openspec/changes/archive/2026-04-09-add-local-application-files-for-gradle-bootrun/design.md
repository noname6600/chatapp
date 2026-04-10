## Context

The backend currently supports local native startup through `gradlew :<service>:bootRun`, but practical startup still depends on loading `.env.local` into the shell before launching each service. That is workable in Docker Compose and PowerShell, but it is awkward in plain Windows `cmd` and creates a different startup contract from the checked-in Spring resource files.

This change affects every backend service that participates in local hybrid mode: auth-service, user-service, chat-service, presence-service, friendship-service, notification-service, upload-service, and gateway-service. The existing local runbook also needs to stop implying that `bootRun` is directly executable without additional environment preparation.

## Goals / Non-Goals

**Goals:**
- Make native local `bootRun` startup work from a plain shell without preloading `.env.local`.
- Define per-service local Spring configuration files for the infrastructure topology already documented in local hybrid mode.
- Keep local-only values isolated to local profile files so default application configuration remains environment-driven for containers and production.
- Update the runbook to document the new local startup contract and expected command sequence.

**Non-Goals:**
- Replace Docker Compose environment handling for containerized local or production runs.
- Redesign service-to-service ports, database names, or infrastructure topology.
- Remove support for environment variable overrides where they already exist.

## Decisions

### Decision: Use checked-in local Spring profile files per service
Each service will have a local profile resource file that contains the values needed for native local startup against the documented local hybrid infrastructure. This matches the user workflow of running `gradlew.bat :<service>:bootRun` directly from `cmd`.

Alternative considered: add a Windows launcher script that parses `.env.local` before `bootRun`.
Why not: it keeps local startup dependent on shell-specific parsing rules and does not help IDE or test profile consistency.

### Decision: Keep base application files environment-oriented
The existing `application.yaml` files already define environment placeholders and container-safe defaults. The new local files will override only the local values needed for native development, instead of moving local-only constants into the shared base configuration.

Alternative considered: move local values into `application.yaml` defaults.
Why not: that would blur local and non-local behavior and make container and production contracts harder to reason about.

### Decision: Document startup around `SPRING_PROFILES_ACTIVE=local`
The runbook should explicitly require local profile activation for native startup and should explain that the checked-in local files provide the configuration needed by that profile.

Alternative considered: rely on implicit default profile behavior.
Why not: explicit profile activation is easier to verify and avoids accidental drift if other profiles are added later.

## Risks / Trade-offs

- Checked-in local settings can drift from Docker local settings -> Mitigation: document parity expectations and keep local values aligned with the existing local compose topology and service ports.
- Some services need secrets or third-party credentials locally -> Mitigation: use safe local placeholders where possible and document any still-required manual overrides.
- Multiple service resource files increase maintenance overhead -> Mitigation: keep local files narrowly scoped to only the properties needed for native startup.

## Migration Plan

1. Add or update local Spring profile files for each backend service.
2. Validate that each service resolves its local dependencies using the documented local hybrid ports and hosts.
3. Update the local hybrid runbook to use the checked-in local profile approach for native startup.
4. Keep rollback simple by reverting the resource-file and runbook changes if local startup regressions appear.

## Open Questions

- Which locally required secrets should remain checked in as development-only placeholders versus remain environment-driven and user-supplied?