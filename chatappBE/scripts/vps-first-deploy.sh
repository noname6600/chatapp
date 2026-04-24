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
TOKEN_REL_PATH=".well-known/acme-challenge/preflight-token"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: required command not found: $1"
    exit 1
  fi
}

diagnose_on_failure() {
  local token_value="${1:-diag-probe}"

  mkdir -p certbot/www/.well-known/acme-challenge
  printf '%s\n' "$token_value" > "certbot/www/${TOKEN_REL_PATH}"

  echo ""
  echo "### ACME diagnostics (auto)"

  "${COMPOSE[@]}" up -d nginx >/dev/null 2>&1 || true

  echo "- Local Host-header route fingerprints"
  for domain in "${DOMAINS[@]}"; do
    fingerprint_request "local:${domain}" "http://127.0.0.1/${TOKEN_REL_PATH}" "$domain"
  done

  echo "- Public domain fingerprints"
  for domain in "${DOMAINS[@]}"; do
    fingerprint_request "public:${domain}" "http://${domain}/${TOKEN_REL_PATH}"
  done

  echo "- Port listeners (:80 and :443)"
  if command -v ss >/dev/null 2>&1; then
    ss -ltnp '( sport = :80 or sport = :443 )' || true
  else
    netstat -ltnp 2>/dev/null | grep -E ':80|:443' || true
  fi

  echo "- Docker edge mapping"
  docker ps --filter name=chatapp-nginx --format 'table {{.Names}}\t{{.Ports}}' || true

  rm -f "certbot/www/${TOKEN_REL_PATH}" || true

  echo ""
  echo "ACME preflight failed before cert issuance."
  echo "Interpretation:"
  echo "- If local Host-header check fails: ingress is not reaching expected edge responder on this host."
  echo "- If local passes but public fails: DNS/CDN/proxy is routing to a different upstream."
  echo "- If both pass but issuance still fails: investigate certbot webroot mount/path mismatch."
  echo "Fix routing source, then rerun this script."
}

fingerprint_request() {
  local label="$1"
  local url="$2"
  local host_header="${3:-}"
  local headers_file body_file status content_type server body_snippet

  headers_file="$(mktemp)"
  body_file="$(mktemp)"

  if [ -n "$host_header" ]; then
    status="$(curl -sS -m 15 -D "$headers_file" -o "$body_file" -w '%{http_code}' -H "Host: ${host_header}" "$url" || true)"
  else
    status="$(curl -sS -m 15 -D "$headers_file" -o "$body_file" -w '%{http_code}' "$url" || true)"
  fi

  content_type="$(grep -i '^Content-Type:' "$headers_file" | tail -n 1 | cut -d':' -f2- | xargs || true)"
  server="$(grep -i '^Server:' "$headers_file" | tail -n 1 | cut -d':' -f2- | xargs || true)"
  body_snippet="$(head -c 120 "$body_file" | tr '\n' ' ' | tr '\r' ' ')"

  echo "  > ${label}"
  echo "    status=${status:-n/a} server=${server:-<none>} content-type=${content_type:-<none>}"
  echo "    body='${body_snippet:-<empty>}'"

  rm -f "$headers_file" "$body_file"
}

verify_token_request() {
  local url="$1"
  local expected="$2"
  local host_header="${3:-}"
  local status body

  if [ -n "$host_header" ]; then
    status="$(curl -sS -m 15 -o /tmp/acme_verify_body.$$ -w '%{http_code}' -H "Host: ${host_header}" "$url" || true)"
  else
    status="$(curl -sS -m 15 -o /tmp/acme_verify_body.$$ -w '%{http_code}' "$url" || true)"
  fi
  body="$(cat /tmp/acme_verify_body.$$ 2>/dev/null || true)"
  rm -f /tmp/acme_verify_body.$$ || true

  [ "$status" = "200" ] && [ "$body" = "$expected" ]
}

precheck_ingress_route_source() {
  local token_value domain local_ok public_ok
  token_value="acme-route-check-$(date +%s)"
  local_ok=1
  public_ok=1

  mkdir -p certbot/www/.well-known/acme-challenge
  printf '%s\n' "$token_value" > "certbot/www/${TOKEN_REL_PATH}"

  echo "### Starting edge nginx for ingress precheck"
  "${COMPOSE[@]}" up -d nginx >/dev/null

  for domain in "${DOMAINS[@]}"; do
    if ! verify_token_request "http://127.0.0.1/${TOKEN_REL_PATH}" "$token_value" "$domain"; then
      local_ok=0
    fi

    if ! verify_token_request "http://${domain}/${TOKEN_REL_PATH}" "$token_value"; then
      public_ok=0
    fi
  done

  if [ "$local_ok" -ne 1 ] || [ "$public_ok" -ne 1 ]; then
    diagnose_on_failure "$token_value"
    exit 1
  fi

  rm -f "certbot/www/${TOKEN_REL_PATH}"
  echo "### Ingress precheck passed (local Host-header + public domain)"
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

echo "### Verifying ingress route source before cert issuance"
precheck_ingress_route_source

echo "### Running first-time TLS issuance"
chmod +x nginx/init-letsencrypt.sh
if ! ./nginx/init-letsencrypt.sh "$EMAIL"; then
  diagnose_on_failure
  exit 1
fi

echo "### Starting full production stack"
"${COMPOSE[@]}" up -d --build

echo "### Verifying CORS env and preflight contract"
chmod +x scripts/verify-cors-contract.sh
ENV_FILE=.env.production COMPOSE_FILE=docker-compose.yml ./scripts/verify-cors-contract.sh

echo "### Basic post-deploy checks"
"${COMPOSE[@]}" ps
for domain in "${DOMAINS[@]}"; do
  echo "  > https://${domain}"
  curl -fsSI --max-time 20 "https://${domain}" | head -n 5
 done

echo ""
echo "VPS first deploy complete."
