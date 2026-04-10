## 1. Production Compose Foundation

- [x] 1.1 Define and finalize one-command production compose entrypoint including FE, BE, and Nginx services
- [x] 1.2 Ensure only Nginx is publicly exposed while app containers remain on internal Docker networks
- [x] 1.3 Add or refine health checks and restart policies required for stable first boot in production

## 2. Nginx Domain, TLS, and WebSocket Routing

- [x] 2.1 Implement Nginx host-based routing: `chatweb.nani.id.vn` → frontend, `api.chatweb.nani.id.vn` → gateway
- [x] 2.2 Configure HTTPS termination and TLS certificate wiring for `chatweb.nani.id.vn` and `api.chatweb.nani.id.vn`
- [x] 2.3 Configure WebSocket upgrade headers in Nginx for realtime endpoints via `api.chatweb.nani.id.vn`

## 3. Environment Contract Standardization

- [x] 3.1 Define required production environment variable contract and document each variable purpose
- [x] 3.2 Align backend service and gateway runtime configs to consume contract values without hardcoded production constants
- [x] 3.3 Set `VITE_API_URL=https://api.chatweb.nani.id.vn` and `VITE_WS_URL=wss://api.chatweb.nani.id.vn` (or equivalent env inputs) in production FE config

## 4. Gateway and CORS Proxy Integration

- [x] 4.1 Verify gateway routing behavior behind Nginx for forwarded API traffic
- [x] 4.2 Enforce non-duplicated CORS header behavior along proxy and gateway response path
- [x] 4.3 Validate browser preflight and authenticated API calls through production domain ingress

## 5. Deployment Runbook and Verification

- [x] 5.1 Create VPS preflight checklist: Docker/Compose versions, DNS A records for `chatweb.nani.id.vn` and `api.chatweb.nani.id.vn`, firewall ports 80/443, env file readiness
- [x] 5.2 Document one-command deployment and rollback procedures using image/env version snapshots
- [x] 5.3 Add smoke-test checklist: `chatweb.nani.id.vn` loads, `api.chatweb.nani.id.vn` API health/auth flow, WebSocket upgrade at `wss://api.chatweb.nani.id.vn`
