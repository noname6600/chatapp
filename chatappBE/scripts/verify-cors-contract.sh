#!/usr/bin/env bash
# Verify production CORS env propagation and runtime CORS behavior across services.
# Usage:
#   ./scripts/verify-cors-contract.sh
# Optional env vars:
#   ENV_FILE=.env.production
#   COMPOSE_FILE=docker-compose.yml
#   CORS_CHECK_API_URL=https://api.chatweb.nani.id.vn/api/v1/auth/login

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ENV_FILE="${ENV_FILE:-.env.production}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
CORS_CHECK_API_URL="${CORS_CHECK_API_URL:-https://api.chatweb.nani.id.vn/api/v1/auth/login}"

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE")
CORS_SERVICES=(
  auth-service
  user-service
  chat-service
  presence-service
  friendship-service
  notification-service
  upload-service
  gateway-service
)

trim() {
  local value="$1"
  value="${value#\"}"
  value="${value%\"}"
  echo "$value" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'
}

load_env_value() {
  local key="$1"
  local value
  value="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 | cut -d'=' -f2- || true)"
  trim "$value"
}

split_csv() {
  local csv="$1"
  local raw
  IFS=',' read -r -a raw <<< "$csv"
  for item in "${raw[@]}"; do
    item="$(trim "$item")"
    if [ -n "$item" ]; then
      printf '%s\n' "$item"
    fi
  done
}

EXPECTED_CORS="$(load_env_value CORS_ALLOWED_ORIGINS)"
if [ -z "$EXPECTED_CORS" ]; then
  echo "ERROR: CORS_ALLOWED_ORIGINS is missing from ${ENV_FILE}"
  exit 1
fi

echo "### CORS contract check"
echo "Expected CORS_ALLOWED_ORIGINS: $EXPECTED_CORS"

env_mismatch=0
for service in "${CORS_SERVICES[@]}"; do
  actual="$(${COMPOSE[@]} exec -T "$service" printenv CORS_ALLOWED_ORIGINS 2>/dev/null || true)"
  actual="$(trim "$actual")"

  if [ -z "$actual" ]; then
    echo "ERROR: ${service} missing CORS_ALLOWED_ORIGINS at runtime"
    env_mismatch=1
    continue
  fi

  if [ "$actual" != "$EXPECTED_CORS" ]; then
    echo "ERROR: ${service} CORS_ALLOWED_ORIGINS mismatch"
    echo "  expected: $EXPECTED_CORS"
    echo "  actual  : $actual"
    env_mismatch=1
  fi

  if echo "$actual" | grep -qi 'localhost'; then
    echo "ERROR: ${service} CORS_ALLOWED_ORIGINS contains localhost in production value"
    env_mismatch=1
  fi

done

if [ "$env_mismatch" -ne 0 ]; then
  echo "CORS env propagation check failed."
  exit 1
fi

echo "CORS env propagation check passed."

echo "### Preflight checks"
preflight_failed=0
while IFS= read -r origin; do
  [ -z "$origin" ] && continue

  headers_file="$(mktemp)"
  status="$(curl -sS -X OPTIONS "$CORS_CHECK_API_URL" \
    -H "Origin: ${origin}" \
    -H "Access-Control-Request-Method: POST" \
    -H "Access-Control-Request-Headers: content-type" \
    -D "$headers_file" -o /dev/null -w '%{http_code}' || true)"

  allow_origin="$(grep -i '^Access-Control-Allow-Origin:' "$headers_file" | tail -n 1 | cut -d':' -f2- | xargs || true)"
  rm -f "$headers_file"

  if [ "$status" != "200" ] && [ "$status" != "204" ]; then
    echo "ERROR: preflight failed for ${origin}, status=${status}"
    preflight_failed=1
    continue
  fi

  if [ "$allow_origin" != "$origin" ]; then
    echo "ERROR: preflight allow-origin mismatch for ${origin}"
    echo "  expected allow-origin: ${origin}"
    echo "  actual allow-origin  : ${allow_origin:-<missing>}"
    preflight_failed=1
    continue
  fi

  echo "OK: preflight origin=${origin} status=${status}"
done < <(split_csv "$EXPECTED_CORS")

if [ "$preflight_failed" -ne 0 ]; then
  echo "Preflight contract check failed."
  exit 1
fi

echo "### Auth login route reachability"
login_code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$CORS_CHECK_API_URL" \
  -H 'Content-Type: application/json' \
  -d '{"email":"invalid@example.com","password":"invalid"}' || true)"
echo "Auth login status code: ${login_code}"

if [ "$login_code" -ge 500 ] 2>/dev/null; then
  echo "ERROR: auth login route returned server error ${login_code}"
  exit 1
fi

evidence_file="build/cors-verification-$(date +%Y%m%d-%H%M%S).log"
mkdir -p build
{
  echo "CORS verification timestamp: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo "ENV_FILE=${ENV_FILE}"
  echo "CORS_CHECK_API_URL=${CORS_CHECK_API_URL}"
  echo "Expected CORS_ALLOWED_ORIGINS=${EXPECTED_CORS}"
  echo
  echo "[runtime env values]"
  for service in "${CORS_SERVICES[@]}"; do
    actual="$(${COMPOSE[@]} exec -T "$service" printenv CORS_ALLOWED_ORIGINS 2>/dev/null || true)"
    echo "${service}=$(trim "$actual")"
  done
  echo
  echo "[auth-service CORS log snippet]"
  ${COMPOSE[@]} logs --tail=200 auth-service | grep -i 'cors\|origin\|forbidden\|invalid' || true
  echo
  echo "[gateway-service CORS log snippet]"
  ${COMPOSE[@]} logs --tail=200 gateway-service | grep -i 'cors\|origin\|forbidden\|invalid' || true
} > "$evidence_file"

echo "CORS contract verification passed."
echo "Evidence written to ${evidence_file}"
