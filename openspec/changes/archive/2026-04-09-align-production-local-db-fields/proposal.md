## Why

Local and production environment contracts have drifted in naming and structure for database-related fields, creating avoidable deployment errors and confusion. We need production contracts to use the same field model as local for all DB-backed services, while still allowing different production values.

## What Changes

- Standardize production environment field names to match local naming for all DB-backed services.
- Ensure production compose and service wiring consume the same DB variable keys used in local mode.
- Keep values environment-specific (production values remain independent from local values).
- Update deployment documentation to describe the unified field contract and required production-only value overrides.
- Add verification guidance to confirm DB field parity between local and production contracts.

## Capabilities

### New Capabilities
- `production-local-db-field-parity`: Defines a single DB environment variable naming contract shared by local and production modes, with environment-specific values.

### Modified Capabilities
- `service-database-isolation`: Clarify and enforce that both local and production use the same per-service DB field names and ownership model.
- `production-env-contract`: Update production environment contract requirements to align DB field keys with local contract keys.
- `deployment-runbook-local-and-vps`: Update runbook requirements to include explicit parity checks between local and production DB field names.

## Impact

- Affected files likely include `chatappBE/.env.local`, `chatappBE/.env.production.example`, `chatappBE/docker-compose.local.yml`, `chatappBE/docker-compose.yml`, and deployment/runbook docs.
- No backend API contract changes are required.
- Reduced configuration drift risk during local-to-production promotion.
