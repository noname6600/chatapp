## Why

The intended one-line command `.\gradlew :auth-service:bootRun --args="--spring.profiles.active=local"` is still fragile because some local profile variables are incomplete or inconsistent across services. This causes startup failures that look like profile issues even when the command itself is correct.

## What Changes

- Define a single-command local startup contract where services can run with `--spring.profiles.active=local` without requiring shell preloading of `.env.local`.
- Fill missing or inconsistent local profile values across backend services so required DB, Kafka, Redis, auth, and inter-service endpoints are resolvable in local mode.
- Add deterministic verification steps to prove command-based startup works for representative services.
- Update local runbook guidance to document exact command usage and fallback override patterns for local secrets.

## Capabilities

### New Capabilities
- `local-bootrun-command-readiness`: Define requirements ensuring local profile configuration is complete enough for command-based Gradle `bootRun` startup without dotenv preloading.

### Modified Capabilities
- `deployment-runbook-local-and-vps`: Update local-mode guidance to require explicit command-based profile activation validation and troubleshooting for missing local variables.

## Impact

- Affected code: backend service local Spring profile files under `src/main/resources/application-local.yaml`
- Affected docs: local hybrid runbook command examples and troubleshooting notes
- Affected systems: auth-service, user-service, chat-service, presence-service, friendship-service, notification-service, upload-service, gateway-service local developer workflow