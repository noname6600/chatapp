## Context

The current production rollout for this project depends on many manual steps across backend compose services, frontend build/runtime configuration, and reverse proxy setup. Recent fixes have moved multiple service configuration values to environment variables, but operators still need to manually stitch FE, BE, domain routing, and runtime networking to get a stable deployment.

The target runtime is a VPS deployment using domain names for frontend and API traffic. The system must support browser clients (including WebSocket traffic), avoid duplicate CORS behavior between gateway and edge proxy, and be operable with a single command for startup.

Production domains:
- Frontend: `chatweb.nani.id.vn`
- Backend API and WebSocket: `api.chatweb.nani.id.vn`

Constraints:
- Existing microservice architecture and gateway remain in place.
- Deployment must continue to support Docker Compose-based orchestration.
- Frontend and backend domain split must be preserved (`chatweb.nani.id.vn` and `api.chatweb.nani.id.vn`).
- Secrets remain environment-driven and must not be hardcoded.

## Goals / Non-Goals

**Goals:**
- Provide a deterministic one-command production startup path.
- Standardize production environment contracts for all participating services.
- Introduce explicit Nginx routing behavior for frontend, API, TLS, and WebSocket upgrade handling.
- Define deploy and rollback steps that can be followed on a clean VPS.

**Non-Goals:**
- Re-architect microservices into Kubernetes or another orchestrator.
- Replace Spring Cloud Gateway with Nginx as the internal API router.
- Implement advanced autoscaling or blue/green deployment strategies.
- Redesign application features unrelated to deployment and runtime configuration.

## Decisions

### 1. Keep Docker Compose as the production orchestrator
Use a single production compose entrypoint to launch all required containers (backend services, frontend serving container, and Nginx edge). This minimizes operational overhead and aligns with current project tooling.

Alternatives considered:
- Multiple compose files plus manual startup order: rejected due to operator complexity.
- Kubernetes migration: rejected as out of scope for this change.

### 2. Make Nginx the only public ingress
Expose only Nginx on public ports and place app containers on internal Docker networks. Nginx performs host-based routing:
- `chatweb.nani.id.vn` -> frontend container
- `api.chatweb.nani.id.vn` -> gateway container

Nginx also manages WebSocket upgrade headers and TLS termination.

Alternatives considered:
- Expose gateway/frontend directly: rejected due to fragmented ingress and weaker operational control.
- Keep Caddy/Traefik optional switch: rejected to reduce moving parts.

### 3. Enforce environment contract at compose boundary
All deployment-critical values are passed through compose environment variables with explicit defaults only for local-safe values. Required production values include domain URLs, CORS origin(s), backend endpoints, and credentials/secrets.

Alternatives considered:
- Service-local defaults for production domains: rejected due to drift risk.
- Hardcoded production values: rejected for security and portability reasons.

### 4. Keep CORS authority at gateway/app layer, avoid proxy duplication
Nginx should not append conflicting CORS headers when gateway/services already provide policy. This prevents duplicate `Access-Control-Allow-Origin` responses.

Alternatives considered:
- Move all CORS logic to Nginx: rejected because application-level policy must remain explicit and testable.

### 5. Add deployment guardrails and verification gates
Startup sequence includes health checks and post-start smoke checks against domain endpoints and auth flow baseline. Restart policy and rollback are documented to reduce recovery time.

Alternatives considered:
- No health/smoke verification: rejected due to fragile first-run behavior.

## Risks / Trade-offs

- [Misconfigured DNS or TLS] -> Provide preflight checklist and explicit DNS record requirements before compose up.
- [CORS policy drift between env files and runtime] -> Centralize origin variables and define one canonical production env source.
- [WebSocket proxy breakage through Nginx] -> Add explicit proxy upgrade settings and smoke test for realtime endpoint.
- [Service startup race conditions] -> Use health checks and dependency ordering where supported; document retry behavior.
- [Single-host deployment limits] -> Accept reduced scalability initially; keep architecture compatible with future orchestration migration.

## Migration Plan

1. Prepare VPS and install Docker/Compose.
2. Provision DNS A records for `chatweb.nani.id.vn` and `api.chatweb.nani.id.vn` pointing to the VPS public IP.
3. Place production env file with required variables and secrets.
4. Build/pull required images.
5. Run one production compose command to start the stack.
6. Run smoke checks:
- `chatweb.nani.id.vn` loads frontend
- `api.chatweb.nani.id.vn/actuator/health` (or equivalent) responds
- auth login path reachable at `api.chatweb.nani.id.vn/api/auth/...`
- WebSocket upgrade succeeds at `api.chatweb.nani.id.vn/ws`
7. If smoke checks fail, rollback by restoring last known-good env/config and restarting previous image tags.

Rollback strategy:
- Keep previous image tags and previous env snapshot.
- Execute compose down/up with prior version set.
- Re-run smoke checks to confirm recovery.

## Open Questions

- Should TLS cert issuance be automated in-container (e.g., certbot sidecar) or handled externally by platform tooling?
- Should frontend static assets be served by Nginx directly or by a dedicated FE container behind Nginx?
- What minimum health endpoint contract should each backend service expose for production readiness?
- Is zero-downtime deploy required in this phase, or is brief maintenance-window restart acceptable?
