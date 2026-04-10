## Why

Running every backend service inside Docker during local development causes unnecessary resource pressure and lag. We need a documented hybrid workflow where only infrastructure dependencies run in Docker, while each service runs directly from the local machine for faster iteration and easier debugging.

## What Changes

- Add a new local development runbook text file that documents a hybrid startup model.
- Define explicit CLI commands to start infrastructure containers (databases, Redis, Kafka, and related dependencies) without containerizing all application services.
- Document service-by-service local startup commands so developers can boot each backend service outside Docker.
- Add verification and troubleshooting steps for infrastructure and service startup order.

## Capabilities

### New Capabilities
- `local-hybrid-runtime-runbook`: Defines a local development runbook for running only infrastructure in Docker and starting application services manually.

### Modified Capabilities
- `deployment-runbook-local-and-vps`: Extend local-mode requirements to explicitly support and document the hybrid Docker-plus-manual-service workflow.

## Impact

- Affected docs: local deployment and developer runbook documentation.
- Affected systems: local developer runtime workflow for backend infrastructure and service boot commands.
- No production behavior change; this is a documentation and operational workflow enhancement for local development.
