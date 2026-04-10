## Why

The current local setup relies on one PostgreSQL instance with shared superuser credentials across multiple service databases, which weakens service-level data isolation and does not mirror stricter production boundaries. We need a clear change that enforces per-service database ownership and updates compose and hybrid runbook guidance consistently.

## What Changes

- Introduce explicit per-service database isolation requirements for local and production-oriented runtime patterns.
- Define service-specific database users, credentials, and connection targets instead of shared database credentials.
- Update local Docker Compose contracts to provision and validate isolated database access per service.
- Update hybrid runbook and deployment documentation to include isolated database bootstrap and verification CLI commands.
- **BREAKING**: Services that currently rely on shared `POSTGRES_USER` and `POSTGRES_PASSWORD` must migrate to service-specific database credentials.

## Capabilities

### New Capabilities
- `service-database-isolation`: Defines per-service PostgreSQL database ownership, credentials, and access boundaries across runtime environments.

### Modified Capabilities
- `deployment-runbook-local-and-vps`: Update local and VPS runbook requirements to document isolated per-service database provisioning and verification workflows.

## Impact

- Affected backend runtime configuration: local compose environment and service DB connection settings.
- Affected operations docs: local hybrid runbook and deployment runbook database bootstrap and checks.
- Affected services: auth-service, user-service, chat-service, friendship-service, notification-service, and any service with PostgreSQL dependency.
- Migration required for existing local/prod environment variable conventions and startup scripts.
