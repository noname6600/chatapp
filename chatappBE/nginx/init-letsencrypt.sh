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
  exit 1
}

preflight_probe() {
  local token_file token_value domain url body
  token_file="${DATA_PATH}/www/.well-known/acme-challenge/preflight-token"
  token_value="acme-preflight-$(date +%s)"

  mkdir -p "$(dirname "$token_file")"
  printf '%s\n' "$token_value" > "$token_file"

  for domain in "${DOMAINS[@]}"; do
    url="http://${domain}/.well-known/acme-challenge/preflight-token"
    echo "### Preflight probe for ${domain} ..."
    body="$(curl -fsS --max-time 15 "$url" || true)"
    if [ "$body" != "$token_value" ]; then
      echo "Expected token: $token_value"
      echo "Received body: ${body:-<empty>}"
      fail_with_hint "ACME preflight probe failed for ${url}"
    fi
  done

  rm -f "$token_file"
  echo "### ACME preflight probe passed for all domains"
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
  echo "### Issuing certificate for $domain ..."
  compose run --rm certbot \
    certonly --webroot \
    --webroot-path=/var/www/certbot \
    --email "$EMAIL" \
    --agree-tos --no-eff-email \
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
