## Why

Running backend services locally with `gradlew :<service>:bootRun` currently depends on shell-loaded environment variables from `.env.local`. That makes Windows `cmd` startup fragile and inconsistent, especially for developers who want each service to start directly from a checked-in local Spring configuration file.

## What Changes

- Add dedicated local Spring application files for backend services so `bootRun` works in local mode without preloading `.env.local` into the shell.
- Define a consistent local configuration contract for DB-backed services and supporting integrations such as Redis, Kafka, OAuth, mail, and upload settings where needed.
- Update the local deployment/runbook guidance so native service startup documents the local profile file approach instead of assuming shell-managed environment injection.

## Capabilities

### New Capabilities
- `local-native-service-config-files`: Define per-service local Spring configuration files that allow native Gradle `bootRun` startup in local development without shell-based `.env.local` loading.

### Modified Capabilities
- `deployment-runbook-local-and-vps`: Local mode requirements will change to document and verify native service startup through checked-in local application files.

## Impact

- Affected code: backend service resource configuration files under `src/main/resources`
- Affected docs: local hybrid runbook and related startup guidance
- Affected systems: auth-service, user-service, chat-service, presence-service, friendship-service, notification-service, upload-service, gateway-service local developer workflow