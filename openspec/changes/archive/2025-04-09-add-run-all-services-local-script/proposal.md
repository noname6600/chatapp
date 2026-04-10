## Why

Running individual backend services locally requires typing a long Gradle command per service, and the quoted `--args="--spring.profiles.active=local"` form fails in Windows cmd due to shell quoting rules. Developers need a single-command way to boot any or all services with the local profile, using the correct unquoted `--args=--spring.profiles.active=local` syntax.

## What Changes

- Add a PowerShell convenience script `chatappBE/scripts/start-services-local.ps1` that starts any subset (or all) of the 8 backend services in separate terminal windows using the correct Gradle bootRun syntax
- Add a Windows cmd batch file `chatappBE/scripts/start-services-local.bat` for users who prefer cmd
- Update `LOCAL_HYBRID_RUNBOOK.txt` to reference the new scripts as the recommended multi-service startup method

## Capabilities

### New Capabilities
- `local-service-startup-script`: PowerShell and batch scripts that launch selected or all backend services via `gradlew :xxx-service:bootRun --args=--spring.profiles.active=local`, each in their own terminal/window

### Modified Capabilities
- `deployment-runbook-local-and-vps`: Update the local runbook section to document the new startup scripts alongside the manual one-liner form

## Impact

- New files: `chatappBE/scripts/start-services-local.ps1`, `chatappBE/scripts/start-services-local.bat`
- Modified: `chatappBE/LOCAL_HYBRID_RUNBOOK.txt`
- No API, schema, or production config changes
- No breaking changes
