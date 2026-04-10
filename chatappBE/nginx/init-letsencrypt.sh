#!/bin/bash
# init-letsencrypt.sh
# Run ONCE on first deploy to obtain TLS certificates via Let's Encrypt.
# After the initial run, certbot in the compose stack auto-renews.
#
# Usage:
#   chmod +x nginx/init-letsencrypt.sh
#   ./nginx/init-letsencrypt.sh your@email.com

set -e

EMAIL="${1:?Usage: $0 <email>}"
DOMAINS=("chatweb.nani.id.vn" "api.chatweb.nani.id.vn")
RSA_KEY_SIZE=4096
DATA_PATH="./certbot"

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
docker compose -f docker-compose.yml --env-file .env.production up -d nginx

# Request real certificate for each domain
for domain in "${DOMAINS[@]}"; do
  echo "### Issuing certificate for $domain ..."
  docker compose -f docker-compose.yml --env-file .env.production run --rm certbot \
    certonly --webroot \
    --webroot-path=/var/www/certbot \
    --email "$EMAIL" \
    --agree-tos --no-eff-email \
    -d "$domain"
done

echo "### Reloading nginx ..."
docker compose -f docker-compose.yml --env-file .env.production exec nginx nginx -s reload

echo ""
echo "Done! TLS certificates issued for: ${DOMAINS[*]}"
echo "Run 'docker compose -f docker-compose.yml --env-file .env.production up -d' to start the full stack."
