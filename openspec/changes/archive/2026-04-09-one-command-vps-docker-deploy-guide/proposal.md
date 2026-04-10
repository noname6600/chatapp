## Why

The current deployment guidance is fragmented, mixes local and VPS steps, and uses inconsistent wording, which causes setup errors and slow onboarding. This change is needed now to provide a single, reliable path for local and production Docker Compose deployments, including one-command VPS startup for frontend, backend, and Nginx under the intended domains.

## What Changes

- Standardize deployment documentation wording so local, production, and VPS instructions are clear, consistent, and sequence-driven.
- Define a one-command VPS startup flow using Docker Compose that brings up backend services, frontend runtime, and Nginx together.
- Define explicit domain routing expectations for production:
  - Frontend: chatweb.nani.id.vn
  - API/WebSocket: api.chatweb.nani.id.vn
- Define required environment contract and validation checks for both local and production deploy paths.
- Define operator-facing runbook requirements for preflight checks, startup, verification, troubleshooting, and rollback.

## Capabilities

### New Capabilities
- deployment-runbook-local-and-vps: Unified operator runbook requirements for local and VPS deployment, including one-command startup and verification.

### Modified Capabilities
- single-command-production-deploy: Refine requirements to require an end-to-end one-command VPS startup path and explicit verification outputs.
- production-env-contract: Refine requirements to include production domains, API/WS host mapping, and stricter variable completeness checks.
- nginx-domain-and-tls-routing: Refine requirements to explicitly enforce routing for chatweb.nani.id.vn and api.chatweb.nani.id.vn.
- gateway-production-proxy-integration: Refine requirements to align API and WebSocket proxy behavior with domain-based ingress and deployment documentation.

## Impact

- Affected areas: Docker Compose production workflows, Nginx host routing configuration, gateway proxy assumptions, and deployment documentation structure.
- Systems affected: frontend container, gateway/backend containers, Nginx edge proxy, DNS/domain configuration, and operator runbooks.
- No client API contract breaking changes are intended; this is primarily deployment-operability and specification clarity work.
