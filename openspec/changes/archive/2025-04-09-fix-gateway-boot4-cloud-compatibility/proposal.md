## Why

Local startup flow now includes a multi-service launcher, but gateway-service fails to boot under the local profile due to Spring Boot 4.0.1 and Spring Cloud auto-configuration incompatibilities. This blocks completion of launcher verification and prevents a stable auth-plus-gateway local baseline.

## What Changes

- Align gateway-service dependency and auto-configuration behavior so local `bootRun` starts successfully with `--args=--spring.profiles.active=local`
- Add minimal compatibility guardrails to prevent non-reactive or incompatible Spring Cloud auto-config paths from breaking reactive gateway bootstrap
- Add targeted tests and startup verification for auth-service plus gateway-service
- Update local runbook troubleshooting to distinguish command syntax issues from gateway dependency compatibility issues

## Capabilities

### New Capabilities
- `gateway-local-bootrun-compatibility`: Gateway service SHALL start in local profile via Gradle bootRun with stable Spring Boot/Spring Cloud compatibility and predictable startup checks

### Modified Capabilities
- `deployment-runbook-local-and-vps`: Local runbook troubleshooting SHALL include gateway compatibility diagnostics and recovery steps in addition to command syntax guidance

## Impact

- Affected code: chatappBE/gateway-service build and runtime configuration
- Affected docs: chatappBE/LOCAL_HYBRID_RUNBOOK.txt
- Affected workflow: openspec change add-run-all-services-local-script verification tasks (auth+gateway startup)
- No external API contract changes expected
