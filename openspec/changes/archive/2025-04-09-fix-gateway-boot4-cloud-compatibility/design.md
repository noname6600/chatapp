## Context

The local launcher change can already start selected services with the correct command shape (`--args=--spring.profiles.active=local`). However, gateway-service still fails during application bootstrap in local mode due to framework compatibility behavior between Spring Boot 4.0.1 and Spring Cloud autoconfiguration classes that assume unavailable servlet or legacy Boot classes in a reactive gateway runtime.

This blocks verification tasks that require auth-service and gateway-service to start together. The issue is not command syntax and not profile value resolution; it is gateway dependency/bootstrap compatibility.

## Goals / Non-Goals

**Goals:**
- Ensure `:gateway-service:bootRun --args=--spring.profiles.active=local` reaches successful startup and port bind in local mode
- Keep gateway reactive architecture intact (no servlet migration)
- Add deterministic verification and diagnostics so runbook users can distinguish command misuse from dependency/bootstrap incompatibility
- Unblock completion of the launcher verification tasks that depend on auth+gateway startup

**Non-Goals:**
- Redesign gateway routes, filters, or security model
- Introduce production behavior changes unrelated to startup compatibility
- Replace the launcher scripts produced by the separate startup-script change

## Decisions

### Decision 1: Resolve compatibility primarily at dependency alignment layer
The first implementation path should align gateway dependency versions (Boot/Cloud coordinates or managed artifacts) so incompatible autoconfiguration classes are not selected for this runtime.

Alternative considered: broad runtime exclusions in `application.yaml`.
Why not: exclusion lists are brittle and can mask future regressions.

### Decision 2: Use narrowly scoped configuration guards only when dependency alignment cannot fully remove conflicts
If some startup conflicts remain, add minimum necessary local/runtime exclusions with comments and tests.

Alternative considered: keeping a long exclusion list permanently.
Why not: high maintenance burden and fragile upgrade path.

### Decision 3: Add explicit verification and troubleshooting signals
Runbook and tests should capture that command syntax is correct while compatibility failures are a separate class of issues.

Alternative considered: leaving troubleshooting implicit in logs.
Why not: slower triage and repeated confusion during local startup.

## Risks / Trade-offs

- [Risk] Version alignment could affect gateway transitive behavior beyond startup -> Mitigation: run targeted gateway smoke and security/cors tests after alignment
- [Risk] Temporary exclusions may hide deeper dependency mismatch -> Mitigation: keep exclusions minimal and documented; prefer managed-version fix
- [Risk] Upstream framework upgrades could reintroduce startup breakage -> Mitigation: add local boot verification to change tasks and retain troubleshooting guidance

## Migration Plan

1. Apply dependency alignment in gateway build configuration.
2. Run local gateway startup with local profile and validate port 8080 bind.
3. Run auth+gateway startup path used by launcher verification.
4. Update runbook troubleshooting with compatibility-specific guidance.
5. Resume and complete pending tasks in the launcher-script change.

## Open Questions

- Should gateway stay on Boot 4.x now with a pinned compatible Spring Cloud set, or should it be temporarily down-leveled with the rest of the stack for consistency?
- Are there any existing CI jobs that should include explicit `:gateway-service:bootRun` startup validation in local profile?