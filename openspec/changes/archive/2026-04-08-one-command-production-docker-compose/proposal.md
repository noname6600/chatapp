## Why

Production deployment currently requires manual, error-prone setup across backend, frontend, environment variables, and reverse proxy configuration. A single, reproducible deployment path is needed so the application can be started on a VPS with one Docker Compose command and predictable domain behavior. Target domains are `chatweb.nani.id.vn` (frontend) and `api.chatweb.nani.id.vn` (backend API / WebSocket).

## What Changes

- Add a production deployment capability that runs the full stack with one command, including backend APIs, frontend app, and reverse proxy.
- Define standardized production environment contracts for all services (CORS, frontend URL, API URL, WebSocket URL, JWT/JWKS routing, and service discovery values).
- Add first-class Nginx reverse proxy behavior for domain routing, TLS termination, and WebSocket upgrade handling.
- Define DNS/domain mapping requirements and startup ordering so deployment is deterministic on a fresh VPS.
- Add operational guardrails for production runtime (public ports, internal-only ports, health checks, and restart behavior).

## Capabilities

### New Capabilities
- `single-command-production-deploy`: Run FE, BE, and reverse proxy in one production Docker Compose flow.
- `nginx-domain-and-tls-routing`: Route frontend and API domains via Nginx, including HTTPS and WebSocket proxy support.
- `production-env-contract`: Centralize required production environment variables and fallback behavior across services.
- `gateway-production-proxy-integration`: Ensure gateway behavior is correct behind Nginx for API forwarding, CORS boundary, and WebSocket proxying.

## Impact

- Affected backend deployment files in chatappBE (compose, env, service runtime config).
- Affected frontend production runtime config in chatappFE for API and WebSocket base URLs.
- New/updated Nginx production configuration and Compose service wiring targeting `chatweb.nani.id.vn` and `api.chatweb.nani.id.vn`.
- VPS operations: DNS A records for both domains pointed to VPS public IP, TLS certificate issuance/renewal, and port exposure policy.
