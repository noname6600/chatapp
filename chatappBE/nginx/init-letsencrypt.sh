#!/bin/bash
# init-letsencrypt.sh
# Run ONCE on first deploy to obtain TLS certificates via Let's Encrypt.
# After the initial run, certbot-renew in the compose stack auto-renews.
#
# Usage:
#   chmod +x nginx/init-letsencrypt.sh
#   ./nginx/init-letsencrypt.sh your@email.com

set -euo pipefail

EMAIL="${1:?Usage: $0 <email>}"
DOMAINS=("chatweb.nani.id.vn" "api.chatweb.nani.id.vn")
RSA_KEY_SIZE=4096
DATA_PATH="./certbot"
COMPOSE_ARGS=( -f docker-compose.yml --env-file .env.production )
TOKEN_REL_PATH=".well-known/acme-challenge/preflight-token"

compose() {
  docker compose "${COMPOSE_ARGS[@]}" "$@"
}

fail_with_hint() {
  echo ""
  echo "ERROR: $1"
  echo ""
  echo "Troubleshooting hints:"
  echo "- Confirm both domains resolve to this server IP:"
  echo "  dig +short chatweb.nani.id.vn"
  echo "  dig +short api.chatweb.nani.id.vn"
  echo "- Confirm HTTP (port 80) is reachable from internet."
  echo "- Confirm nginx uses the same webroot mount as certbot: ./certbot/www -> /var/www/certbot"
  echo "- Confirm challenge file is readable via domain URL before issuance."
  echo "- Compare local Host-header check versus public-domain check to detect ingress-source mismatch."
  echo "- Check listener ownership for :80/:443 (expected docker-proxy for this stack)."
  exit 1
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
    status="$(curl -sS -m 15 -o /tmp/acme_init_verify_body.$$ -w '%{http_code}' -H "Host: ${host_header}" "$url" || true)"
  else
    status="$(curl -sS -m 15 -o /tmp/acme_init_verify_body.$$ -w '%{http_code}' "$url" || true)"
  fi
  body="$(cat /tmp/acme_init_verify_body.$$ 2>/dev/null || true)"
  rm -f /tmp/acme_init_verify_body.$$ || true

  [ "$status" = "200" ] && [ "$body" = "$expected" ]
}

print_listener_snapshot() {
  echo "- Port listeners (:80 and :443)"
  if command -v ss >/dev/null 2>&1; then
    ss -ltnp '( sport = :80 or sport = :443 )' || true
  else
    netstat -ltnp 2>/dev/null | grep -E ':80|:443' || true
  fi
}

preflight_probe() {
  local token_file token_value domain local_ok public_ok
  token_file="${DATA_PATH}/www/${TOKEN_REL_PATH}"
  token_value="acme-preflight-$(date +%s)"
  local_ok=1
  public_ok=1

  mkdir -p "$(dirname "$token_file")"
  printf '%s\n' "$token_value" > "$token_file"

  for domain in "${DOMAINS[@]}"; do
    if ! verify_token_request "http://127.0.0.1/${TOKEN_REL_PATH}" "$token_value" "$domain"; then
      local_ok=0
    fi

    if ! verify_token_request "http://${domain}/${TOKEN_REL_PATH}" "$token_value"; then
      public_ok=0
    fi
  done

  if [ "$local_ok" -ne 1 ] || [ "$public_ok" -ne 1 ]; then
    echo "### ACME ingress-source diagnostics"
    echo "- Local Host-header route fingerprints"
    for domain in "${DOMAINS[@]}"; do
      fingerprint_request "local:${domain}" "http://127.0.0.1/${TOKEN_REL_PATH}" "$domain"
    done

    echo "- Public domain fingerprints"
    for domain in "${DOMAINS[@]}"; do
      fingerprint_request "public:${domain}" "http://${domain}/${TOKEN_REL_PATH}"
    done

    print_listener_snapshot

    if [ "$local_ok" -ne 1 ] && [ "$public_ok" -ne 1 ]; then
      fail_with_hint "Route-source checks failed for both local Host-header and public-domain paths"
    elif [ "$local_ok" -ne 1 ]; then
      fail_with_hint "Local Host-header route check failed (likely host ingress source mismatch)"
    else
      fail_with_hint "Public-domain route check failed (likely DNS/CDN/proxy upstream mismatch)"
    fi
  fi

  rm -f "$token_file"
  echo "### ACME preflight probe passed (local Host-header + public-domain checks)"
}

verify_https_certs() {
  local domain cert_path
  for domain in "${DOMAINS[@]}"; do
    cert_path="${DATA_PATH}/conf/live/${domain}/fullchain.pem"
    if [ ! -s "$cert_path" ]; then
      fail_with_hint "Missing certificate file: ${cert_path}"
    fi

    echo "### HTTPS probe for ${domain} ..."
    curl -fsSI --max-time 20 "https://${domain}" > /dev/null || fail_with_hint "HTTPS probe failed for https://${domain}"
  done
}

cleanup_dummy_cert_artifacts() {
  local domain="$1"
  local live_path archive_path renewal_path subject_line

  live_path="${DATA_PATH}/conf/live/${domain}"
  archive_path="${DATA_PATH}/conf/archive/${domain}"
  renewal_path="${DATA_PATH}/conf/renewal/${domain}.conf"

  if [ ! -f "${live_path}/fullchain.pem" ]; then
    return 0
  fi

  # Real certbot-managed certs are symlinks under live/. The bootstrap dummy cert is a regular file.
  if [ -L "${live_path}/fullchain.pem" ]; then
    return 0
  fi

  subject_line="$(openssl x509 -in "${live_path}/fullchain.pem" -noout -subject 2>/dev/null || true)"
  if echo "$subject_line" | grep -Eq 'CN\s*=\s*localhost'; then
    echo "### Removing dummy certificate artifacts for ${domain} ..."
    rm -rf "${live_path}" "${archive_path}" "${renewal_path}"
  fi
}

# Download recommended TLS parameters if missing
if [ ! -e "$DATA_PATH/conf/options-ssl-nginx.conf" ] || [ ! -e "$DATA_PATH/conf/ssl-dhparams.pem" ]; then
  echo "### Downloading recommended TLS parameters ..."
  mkdir -p "$DATA_PATH/conf"
  curl -s https://raw.githubusercontent.com/certbot/certbot/master/certbot-nginx/certbot_nginx/_internal/tls_configs/options-ssl-nginx.conf \
    > "$DATA_PATH/conf/options-ssl-nginx.conf"
  curl -s https://raw.githubusercontent.com/certbot/certbot/master/certbot/certbot/ssl-dhparams.pem \
    > "$DATA_PATH/conf/ssl-dhparams.pem"
fi

# Create dummy cert so nginx can start while we request the real one
for domain in "${DOMAINS[@]}"; do
  path="$DATA_PATH/conf/live/$domain"
  if [ ! -d "$path" ]; then
    echo "### Creating dummy certificate for $domain ..."
    mkdir -p "$path"
    openssl req -x509 -nodes -newkey rsa:$RSA_KEY_SIZE -days 1 \
      -keyout "$path/privkey.pem" \
      -out "$path/fullchain.pem" \
      -subj "/CN=localhost" 2>/dev/null
  fi
done

echo "### Starting nginx to serve ACME challenge ..."
compose up -d nginx

echo "### Validating nginx config before issuance ..."
compose exec nginx nginx -t

echo "### Reloading nginx with validated config ..."
compose exec nginx nginx -s reload

preflight_probe

# Request real certificate for each domain
for domain in "${DOMAINS[@]}"; do
  cleanup_dummy_cert_artifacts "$domain"

  echo "### Issuing certificate for $domain ..."
  compose run --rm certbot \
    certonly --webroot \
    --webroot-path=/var/www/certbot \
    --email "$EMAIL" \
    --agree-tos --no-eff-email \
    --cert-name "$domain" \
    --force-renewal \
    -d "$domain"
done

echo "### Running certbot renewal dry-run ..."
compose run --rm certbot renew --dry-run --webroot --webroot-path=/var/www/certbot

echo "### Reloading nginx ..."
compose exec nginx nginx -t
compose exec nginx nginx -s reload

verify_https_certs

echo ""
echo "Done! TLS certificates issued for: ${DOMAINS[*]}"
echo "Run 'docker compose --env-file .env.production -f docker-compose.yml up -d' to start the full stack."
