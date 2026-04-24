#!/usr/bin/env bash
# One-command first deploy for VPS production mode.
# Usage: ./scripts/vps-first-deploy.sh your@email.com

set -euo pipefail

EMAIL="${1:-}"
if [ -z "$EMAIL" ]; then
  echo "Usage: $0 <email>"
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

COMPOSE=(docker compose --env-file .env.production -f docker-compose.yml)
DOMAINS=("chatweb.nani.id.vn" "api.chatweb.nani.id.vn")

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: required command not found: $1"
    exit 1
  fi
}

diagnose_on_failure() {
  echo ""
  echo "### ACME diagnostics (auto)"
  mkdir -p certbot/www/.well-known/acme-challenge
  echo "diag-probe" > certbot/www/.well-known/acme-challenge/preflight-token

  "${COMPOSE[@]}" up -d nginx >/dev/null 2>&1 || true

  echo "- Local nginx route check via Host header"
  for domain in "${DOMAINS[@]}"; do
    echo "  > ${domain}"
    curl -sS -i -H "Host: ${domain}" \
      http://127.0.0.1/.well-known/acme-challenge/preflight-token | head -n 5 || true
  done

  echo "- Public domain check"
  for domain in "${DOMAINS[@]}"; do
    echo "  > ${domain}"
    curl -sS -i "http://${domain}/.well-known/acme-challenge/preflight-token" | head -n 5 || true
  done

  echo "- Port listeners (:80 and :443)"
  if command -v ss >/dev/null 2>&1; then
    ss -ltnp '( sport = :80 or sport = :443 )' || true
  else
    netstat -ltnp 2>/dev/null | grep -E ':80|:443' || true
  fi

  echo ""
  echo "ACME preflight failed. Fix routing so public HTTP reaches chatapp-nginx, then rerun this script."
}

require_cmd docker
require_cmd curl

if [ ! -f .env.production ]; then
  echo "ERROR: missing .env.production in $ROOT_DIR"
  echo "Create it from .env.production.example first."
  exit 1
fi

echo "### Validating compose config"
"${COMPOSE[@]}" config >/dev/null

echo "### Running first-time TLS issuance"
chmod +x nginx/init-letsencrypt.sh
if ! ./nginx/init-letsencrypt.sh "$EMAIL"; then
  diagnose_on_failure
  exit 1
fi

echo "### Starting full production stack"
"${COMPOSE[@]}" up -d --build

echo "### Basic post-deploy checks"
"${COMPOSE[@]}" ps
for domain in "${DOMAINS[@]}"; do
  echo "  > https://${domain}"
  curl -fsSI --max-time 20 "https://${domain}" | head -n 5
 done

echo ""
echo "VPS first deploy complete."
