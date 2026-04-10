## Context

Running backend services locally requires starting each of the 8 Spring Boot services individually. The existing approach uses `gradlew.bat :xxx-service:bootRun --args=--spring.profiles.active=local` but there is no script to launch multiple services at once. Each service needs its own terminal window so its stdout/stderr is visible. The working directory for all `gradlew` invocations must be `chatappBE/` (the root of the multi-module Gradle project).

The previous change (`stabilize-local-profile-bootrun-command`) confirmed that all `application-local.yaml` files are complete and that the unquoted form `--args=--spring.profiles.active=local` is the correct syntax on Windows.

## Goals / Non-Goals

**Goals:**
- PowerShell script (`start-services-local.ps1`) that launches any subset or all 8 services, each in a separate terminal window
- Batch file (`start-services-local.bat`) as a cmd-compatible equivalent
- Update `LOCAL_HYBRID_RUNBOOK.txt` to reference the scripts as the preferred multi-service startup method

**Non-Goals:**
- Cross-platform (Linux/Mac) script — developers on this project use Windows
- Process manager or health-check loop — this is a simple fire-and-forget launcher
- Changes to Docker Compose, Gradle build files, or application config

## Decisions

### Decision 1: One window per service via `Start-Process`

Each service is launched in a new PowerShell window using `Start-Process powershell`. This keeps each service's log stream separate and allows individual windows to be closed without affecting others.

**Alternatives considered:**
- Background jobs (`Start-Job`): logs are hidden until polled — poor developer UX
- Multiplexer (Windows Terminal tabs via `wt`): not universally available; adds a dependency
- Single window with foreground/background mixing: hard to read interleaved logs

### Decision 2: Service selection via named switches

The PS script accepts named switch parameters (`-Auth`, `-User`, `-Chat`, etc.) so developers can start a subset. A `-All` switch (default when no switch is given) starts everything.

**Alternatives considered:**
- Positional service-name arguments: less discoverrable, harder to tab-complete
- Always start all: forces developers to kill unwanted services every time

### Decision 3: Script location in `chatappBE/scripts/`

Placed alongside the existing `auth-gateway-smoke.ps1` to keep all BE operational scripts together.

### Decision 4: Batch file delegates to PowerShell script

`start-services-local.bat` calls `powershell -ExecutionPolicy Bypass -File scripts\start-services-local.ps1` with forwarded arguments. This avoids duplicating logic and keeps the batch file trivially thin.

## Risks / Trade-offs

- [Risk] User's PowerShell execution policy blocks script → Mitigation: batch wrapper uses `-ExecutionPolicy Bypass`; runbook documents the workaround to run directly with `powershell -ExecutionPolicy Bypass -File ...`
- [Risk] New terminal window can be confused with an existing service window → Mitigation: each window title is set to the service name
- [Risk] Services started before Docker infra is up will fail → Mitigation: runbook documents that `docker compose -f docker-compose.local.yml up -d` must run first; script prints a reminder

## Migration Plan

No migration needed. The scripts are additive — existing one-liner manual commands continue to work. The runbook update documents the scripts as the preferred shorthand.
