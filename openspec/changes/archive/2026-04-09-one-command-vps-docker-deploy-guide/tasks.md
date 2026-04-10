## 1. Define Unified Deployment Documentation

- [x] 1.1 Identify target deployment documents to update for local and VPS guidance (for example root deploy guide, backend deploy guide, frontend deploy guide)
- [x] 1.2 Standardize terminology and step labels across local, production, and VPS sections
- [x] 1.3 Add explicit domain mapping statements for chatweb.nani.id.vn and api.chatweb.nani.id.vn
- [x] 1.4 Add deterministic preflight checklist for Docker, Docker Compose, DNS, ports, and environment files

## 2. One-Command VPS Startup Contract

- [x] 2.1 Ensure production Docker Compose path and command are documented as a single startup command for backend, frontend, and Nginx
- [x] 2.2 Verify compose definition includes all required core components and startup dependencies
- [x] 2.3 Add post-start verification section with expected healthy signals for frontend host, API host, and authentication baseline
- [x] 2.4 Add failure triage and rollback section with ordered recovery actions

## 3. Environment Contract Alignment

- [x] 3.1 Document required production variables for frontend host, API host, CORS origins, API endpoint, WebSocket endpoint, and required secrets
- [x] 3.2 Add explicit missing-variable failure behavior and validation guidance
- [x] 3.3 Verify backend and frontend runtime configuration references environment values instead of hardcoded production domains
- [x] 3.4 Add local environment variable defaults and local verification notes

## 4. Nginx and Gateway Ingress Alignment

- [x] 4.1 Update deployment guidance to show host-based routing for chatweb.nani.id.vn to frontend and api.chatweb.nani.id.vn to gateway
- [x] 4.2 Add TLS expectation wording for both hosts and certificate validity checks
- [x] 4.3 Document WebSocket upgrade behavior through api.chatweb.nani.id.vn and expected handshake signals
- [x] 4.4 Verify CORS and preflight expectations remain coherent through Nginx and gateway ingress path

## 5. Validation and Consistency Review

- [x] 5.1 Execute local deployment flow using documented steps and confirm expected outcomes
- [x] 5.2 Execute VPS production deployment flow using one compose command and confirm expected outcomes
- [x] 5.3 Run wording consistency review across all updated deployment docs to remove conflicting terms and sequencing
- [x] 5.4 Capture final operator acceptance checklist and sign-off criteria for future releases
