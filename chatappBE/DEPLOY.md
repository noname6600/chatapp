# Deployment Guide (Local + VPS Production)

This runbook uses consistent labels for both modes:
- Local mode: developer machine, local docker network, localhost access.
- VPS production mode: internet-facing deployment with Nginx + TLS.

Domain mapping for VPS production mode:
- Frontend domain: `chatweb.nani.id.vn`
- API and WebSocket ingress domain: `api.chatweb.nani.id.vn`

## Documentation Scope

This guide is the source of truth for deployment flow and links to related env contracts:
- Backend deploy runbook: `chatappBE/DEPLOY.md`
- Backend production env contract: `chatappBE/.env.production.example`
- Frontend env contracts: `chatappFE/.env.local`, `chatappFE/.env.production`, `chatappFE/.env.example`

---

## 1. Local Mode Quickstart

Use this for local integration testing of backend, frontend, and gateway routing.

### 1.0 Local mode variants
- Full local compose mode: starts backend services in Docker using `docker-compose.local.yml`.
- Hybrid local mode (recommended on low-resource machines): runs only infrastructure in Docker and runs backend services natively with Gradle.
- Hybrid local mode guide: `chatappBE/LOCAL_HYBRID_RUNBOOK.txt`

### 1.1 Local preflight
- [ ] Docker Engine is installed (`docker --version`)
- [ ] Docker Compose plugin is installed (`docker compose version`)
- [ ] `chatappBE/.env.local` exists and local values are correct
- [ ] `chatappBE/.env.local` includes service-scoped DB variables for each DB-backed service (`*_DATABASE_NAME`, `*_DATABASE_USER`, `*_DATABASE_PASSWORD`)
- [ ] Local and production DB key names are identical for DB-backed services (values can differ)
- [ ] `chatappFE/.env.local` exists and points to gateway defaults:
  - `VITE_API_URL=http://localhost:8080/api/v1`
  - `VITE_WS_URL=ws://localhost:8080`

### 1.2 Local startup command
```bash
cd chatappBE
docker compose --env-file .env.local -f docker-compose.local.yml up -d --build
```

### 1.2.1 Hybrid local startup shortcut
For lower local lag, use the dedicated hybrid runbook:
- `chatappBE/LOCAL_HYBRID_RUNBOOK.txt`
- This mode starts only DB/infrastructure containers in Docker (`auth-db`, `user-db`, `chat-db`, `friendship-db`, `notification-db`, `redis`, `zookeeper`, `kafka`).
- Backend services are started manually via Gradle `bootRun` commands.

### 1.3 Local verification notes
- Gateway health: `http://localhost:8080/actuator/health` returns `UP`
- Frontend app is reachable from local FE workflow
- API requests route through gateway (not direct service ports)
- WebSocket routes use gateway host and `/ws/**` paths

---

## 2. VPS Production Preflight Checklist

Complete all items before the one-command startup.

### 2.1 System requirements
- [ ] Ubuntu 22.04 LTS or comparable Linux distribution
- [ ] Docker Engine >= 24 installed (`docker --version`)
- [ ] Docker Compose plugin >= 2.20 installed (`docker compose version`)
- [ ] Minimum 2 vCPU, 4 GB RAM recommended for all services
- [ ] At least 20 GB free disk space

### 2.2 DNS records
- [ ] A record: `chatweb.nani.id.vn` -> VPS public IP
- [ ] A record: `api.chatweb.nani.id.vn` -> VPS public IP
- [ ] Records are propagated (`dig +short chatweb.nani.id.vn` returns VPS IP)

### 2.3 Firewall and network
- [ ] TCP 80 open (HTTP and ACME challenge)
- [ ] TCP 443 open (HTTPS and WSS)
- [ ] Internal service ports are not publicly exposed (5432, 6379, 29092)

### 2.4 Environment files and contract
- [ ] Copy `chatappBE/.env.production.example` to `chatappBE/.env.production`
- [ ] Fill all `CHANGE_ME` values (resend, oauth, cloudinary, service-scoped database passwords)
- [ ] Copy `chatappFE/.env.production.example` to `chatappFE/.env.production`
- [ ] Confirm backend domain variables:
  - `FRONTEND_URL=https://chatweb.nani.id.vn`
  - `CORS_ALLOWED_ORIGINS=https://chatweb.nani.id.vn,https://api.chatweb.nani.id.vn`
- [ ] Confirm frontend production variables in `chatappFE/.env.production`:
  - `VITE_API_URL=https://api.chatweb.nani.id.vn/api/v1`
  - `VITE_WS_URL=wss://api.chatweb.nani.id.vn`
- [ ] `.env.production` files are not committed to git

### 2.4.1 Service database isolation contract
- [ ] Configure per-service DB credentials in `.env.production`:
  - `AUTH_DATABASE_NAME`, `AUTH_DATABASE_USER`, `AUTH_DATABASE_PASSWORD`
  - `USER_DATABASE_NAME`, `USER_DATABASE_USER`, `USER_DATABASE_PASSWORD`
  - `CHAT_DATABASE_NAME`, `CHAT_DATABASE_USER`, `CHAT_DATABASE_PASSWORD`
  - `FRIENDSHIP_DATABASE_NAME`, `FRIENDSHIP_DATABASE_USER`, `FRIENDSHIP_DATABASE_PASSWORD`
  - `NOTIFICATION_DATABASE_NAME`, `NOTIFICATION_DATABASE_USER`, `NOTIFICATION_DATABASE_PASSWORD`
- [ ] Confirm DB key-name parity with `.env.local` (same keys, production-specific values)

Migration note:
- Existing deployments using shared Postgres admin-style DB aliases must migrate to canonical service-scoped DB keys before rollout.

### 2.5 TLS certificates (first deploy only)
```bash
cd chatappBE
chmod +x nginx/init-letsencrypt.sh
./nginx/init-letsencrypt.sh your@email.com
```
This script issues Let's Encrypt certificates for both production domains.

### 2.5.0 One-command first deploy (recommended)
```bash
cd chatappBE
chmod +x scripts/vps-first-deploy.sh
./scripts/vps-first-deploy.sh your@email.com
```
This single command runs compose validation, first-time TLS issuance, full stack startup, and basic HTTPS checks.
If ACME preflight fails, the script prints automatic diagnostics (local Host-header route check, public domain check, and port listeners).

### 2.5.1 ACME preflight validation (recommended before first issuance)
```bash
cd chatappBE
mkdir -p certbot/www/.well-known/acme-challenge
echo "preflight-ok" > certbot/www/.well-known/acme-challenge/preflight-token
curl -fsS -H "Host: chatweb.nani.id.vn" http://127.0.0.1/.well-known/acme-challenge/preflight-token
curl -fsS -H "Host: api.chatweb.nani.id.vn" http://127.0.0.1/.well-known/acme-challenge/preflight-token
curl -fsS http://chatweb.nani.id.vn/.well-known/acme-challenge/preflight-token
curl -fsS http://api.chatweb.nani.id.vn/.well-known/acme-challenge/preflight-token
rm -f certbot/www/.well-known/acme-challenge/preflight-token
```
Expected: all four curl commands print `preflight-ok`.

These checks are mandatory before retrying certificate issuance after an ACME 404.

If you get 404, verify all of the following before retrying certbot:
- Nginx is serving the same webroot path as certbot (`./certbot/www:/var/www/certbot`).
- You are running commands from `chatappBE` (project-root path mismatches cause wrong bind mounts).
- DNS for both domains points to the same VPS public IP.
- Port 80 is reachable from internet.
- Port listeners on `:80/:443` are owned by the expected edge runtime (`docker-proxy` in this deployment model).

### 2.6 Missing-variable failure behavior
- If required env variables are missing, compose startup or service boot will fail.
- Validate before startup:
```bash
cd chatappBE
docker compose --env-file .env.production -f docker-compose.yml config > /tmp/chatapp-compose-check.yaml
```
- If this command fails, fix missing variables first.

---

## 3. One-Command VPS Production Startup

This is the single command to start backend, frontend, and Nginx together:

```bash
cd chatappBE
docker compose --env-file .env.production -f docker-compose.yml up -d --build
```

### 3.1 Core component checklist in compose
The production compose file must include and start:
- Infrastructure: auth-db, user-db, chat-db, friendship-db, notification-db, redis, zookeeper, kafka
- Backend services: auth-service, user-service, chat-service, presence-service, friendship-service, notification-service, upload-service
- Edge components: gateway-service, frontend, nginx, certbot

### 3.2 Dependency expectations
- `gateway-service` waits on `auth-service` and `redis`
- `nginx` waits on `frontend` and `gateway-service`
- Most app services wait on shared infrastructure health checks

### 3.3 Runtime checks
```bash
docker compose --env-file .env.production -f docker-compose.yml ps
docker compose --env-file .env.production -f docker-compose.yml logs -f gateway-service
```
Allow around 60-90 seconds for warm-up.

### 3.4 Stop command
```bash
docker compose --env-file .env.production -f docker-compose.yml down
```

---

## 4. Post-Start Verification (Deterministic)

Run after every deploy and rollback.

### 4.1 Frontend host check
```bash
curl -sI https://chatweb.nani.id.vn | head -5
```
Expected: HTTP 200 or HTTP 301/302 to app entry with successful page load.

### 4.2 API health check
```bash
curl -s https://api.chatweb.nani.id.vn/actuator/health | jq .status
```
Expected: `"UP"`.

### 4.3 Auth baseline check
```bash
curl -s -o /dev/null -w "%{http_code}" -X POST https://api.chatweb.nani.id.vn/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"wrong"}'
```
Expected: `401` (route is reachable, invalid credentials rejected).

### 4.4 WebSocket upgrade check
```bash
# Install wscat if needed: npm i -g wscat
wscat -c "wss://api.chatweb.nani.id.vn/ws/presence"
```
Expected: HTTP 101 upgrade is possible through Nginx and gateway.

### 4.5 CORS preflight coherence check
```bash
curl -sI -X OPTIONS https://api.chatweb.nani.id.vn/api/v1/auth/login \
  -H "Origin: https://chatweb.nani.id.vn" \
  -H "Access-Control-Request-Method: POST" \
  | grep -i "access-control"
```
Expected: coherent CORS headers, no duplicated `Access-Control-Allow-Origin` value.

---

## 5. Failure Triage and Rollback

### 5.1 Ordered failure triage
1. Confirm compose state: `docker compose --env-file .env.production -f docker-compose.yml ps`
2. Inspect failing service logs: `docker compose --env-file .env.production -f docker-compose.yml logs --tail=200 <service-name>`
3. Validate env file and re-run compose config check.
4. Confirm DNS resolution for both production domains.
5. Confirm certificate files exist for both domains in nginx cert volume.
6. For ACME 404, confirm challenge file is externally reachable:
```bash
mkdir -p certbot/www/.well-known/acme-challenge
echo "probe" > certbot/www/.well-known/acme-challenge/probe
curl -i -H "Host: chatweb.nani.id.vn" http://127.0.0.1/.well-known/acme-challenge/probe
curl -i -H "Host: api.chatweb.nani.id.vn" http://127.0.0.1/.well-known/acme-challenge/probe
curl -i http://chatweb.nani.id.vn/.well-known/acme-challenge/probe
curl -i http://api.chatweb.nani.id.vn/.well-known/acme-challenge/probe
rm -f certbot/www/.well-known/acme-challenge/probe
```
Expected: all four checks return HTTP 200 with body `probe`.

7. Confirm listener ownership and edge port mapping:
```bash
ss -ltnp '( sport = :80 or sport = :443 )'
docker ps --filter name=chatapp-nginx --format "table {{.Names}}\t{{.Ports}}"
```

### 5.1.1 ACME 404 remediation matrix
- Symptom: local Host-header check fails and public check fails
  - Likely cause: traffic on host is not reaching intended edge responder
  - Action: resolve host-level ingress ownership conflict on `:80/:443`, then re-run preflight
- Symptom: local Host-header check passes but public check fails
  - Likely cause: DNS/CDN/proxy sends traffic to different upstream
  - Action: fix DNS target and disable/bypass CDN proxying during issuance, then retry
- Symptom: local and public checks pass but certbot still fails
  - Likely cause: certbot webroot mount/path mismatch or stale runtime path
  - Action: verify `./certbot/www:/var/www/certbot` mount in running containers and re-run issuance

### 5.2 Ordered rollback actions
1. Restore previous git revision for deployment files.
2. Restore previous env snapshot:
```bash
cp .env.production.backup .env.production
```
3. Restart stack:
```bash
docker compose --env-file .env.production -f docker-compose.yml up -d --build
```
4. Re-run the deterministic verification checks from section 4.
5. Re-run ACME preflight checks in section 2.5.1 before issuing certificates again.

---

## 6. Certificate Renewal

Certbot renews automatically in the `certbot-renew` container. Manual renewal:

```bash
docker compose --env-file .env.production -f docker-compose.yml run --rm certbot renew
docker compose --env-file .env.production -f docker-compose.yml exec nginx nginx -s reload
```

### 6.1 Post-issuance verification checklist
```bash
ls -l certbot/conf/live/chatweb.nani.id.vn/fullchain.pem
ls -l certbot/conf/live/api.chatweb.nani.id.vn/fullchain.pem
curl -sSI https://chatweb.nani.id.vn | head -5
curl -sSI https://api.chatweb.nani.id.vn | head -5
docker compose --env-file .env.production -f docker-compose.yml logs --tail=200 certbot-renew | grep -i "error\|failed\|acme\|challenge"
```
Expected:
- certificate files exist for both domains
- HTTPS headers are returned from both domains
- no recurring ACME challenge 404/unauthorized errors in renewal logs

### 6.2 Renewal dry-run and rollback
```bash
docker compose --env-file .env.production -f docker-compose.yml run --rm certbot renew --dry-run --webroot --webroot-path=/var/www/certbot
```

If dry-run fails:
1. Backup current deployment files and env.
2. Restore previous known-good `chatappBE/nginx/nginx.conf`, `chatappBE/docker-compose.yml`, and certbot paths.
3. Recreate edge services:
```bash
docker compose --env-file .env.production -f docker-compose.yml up -d --build nginx certbot-renew
```
4. Re-run ACME preflight probe and retry dry-run only after HTTP challenge URL is reachable.

### 6.3 Deterministic post-fix validation before rerunning one-command deploy
```bash
mkdir -p certbot/www/.well-known/acme-challenge
echo "postfix-ok" > certbot/www/.well-known/acme-challenge/postfix-token
curl -fsS -H "Host: chatweb.nani.id.vn" http://127.0.0.1/.well-known/acme-challenge/postfix-token
curl -fsS -H "Host: api.chatweb.nani.id.vn" http://127.0.0.1/.well-known/acme-challenge/postfix-token
curl -fsS http://chatweb.nani.id.vn/.well-known/acme-challenge/postfix-token
curl -fsS http://api.chatweb.nani.id.vn/.well-known/acme-challenge/postfix-token
ss -ltnp '( sport = :80 or sport = :443 )'
docker ps --filter name=chatapp-nginx --format "table {{.Names}}\t{{.Ports}}"
rm -f certbot/www/.well-known/acme-challenge/postfix-token
```
Expected:
- all local Host-header and public-domain checks return `postfix-ok`
- listeners on `:80/:443` match expected edge ownership
- chatapp-nginx publishes both `80:80` and `443:443`
