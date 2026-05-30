#!/bin/bash
set -euo pipefail

DOMAIN="${SSL_DOMAIN:-smartdisasterhub.site}"
EMAIL="${SSL_EMAIL:-smartdisasterhub@gmail.com}"
PROJECT_DIR="${PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
COMPOSE="--env-file ${PROJECT_DIR}/.env -f smartDisasterHub/deploy/docker-compose.yml -f smartDisasterHub/deploy/docker-compose.prod.yml"

log() {
  echo "[init-ssl] $*"
}

if [ "$(id -u)" -ne 0 ]; then
  log "Re-running with sudo..."
  exec sudo -E PROJECT_DIR="${PROJECT_DIR}" SSL_DOMAIN="${DOMAIN}" SSL_EMAIL="${EMAIL}" "$0" "$@"
fi

log "Domain: ${DOMAIN}"
log "Project: ${PROJECT_DIR}"

apt-get update -qq
apt-get install -y -qq certbot

mkdir -p /var/www/certbot
chmod 755 /var/www/certbot

cd "${PROJECT_DIR}"

if ! docker compose ${COMPOSE} ps --status running 2>/dev/null | grep -q smart-disaster-hub; then
  log "Starting container (HTTP mode for ACME challenge)..."
  docker compose ${COMPOSE} up -d
  sleep 10
fi

if [ ! -f "/etc/letsencrypt/live/${DOMAIN}/fullchain.pem" ]; then
  log "Requesting certificate from Let's Encrypt..."
  certbot certonly --webroot \
    -w /var/www/certbot \
    -d "${DOMAIN}" \
    -d "www.${DOMAIN}" \
    --email "${EMAIL}" \
    --agree-tos \
    --non-interactive
else
  log "Certificate already exists for ${DOMAIN}"
fi

if grep -q "^APP_WEB_URL=http://" "${PROJECT_DIR}/.env" 2>/dev/null; then
  sed -i "s|^APP_WEB_URL=.*|APP_WEB_URL=https://${DOMAIN}|" "${PROJECT_DIR}/.env"
  log "Updated APP_WEB_URL to https://${DOMAIN} in .env"
fi

log "Restarting container to enable HTTPS..."
docker compose ${COMPOSE} up -d --force-recreate

RENEW_HOOK="/etc/letsencrypt/renewal-hooks/deploy/reload-sdh.sh"
mkdir -p "$(dirname "${RENEW_HOOK}")"
cat > "${RENEW_HOOK}" <<EOF
#!/bin/bash
cd ${PROJECT_DIR}
docker compose ${COMPOSE} restart smart-disaster-hub
EOF
chmod +x "${RENEW_HOOK}"

if ! crontab -l 2>/dev/null | grep -q certbot; then
  (crontab -l 2>/dev/null; echo "0 3 * * * certbot renew --quiet") | crontab -
  log "Added daily certbot renewal cron job"
fi

log "Done. Test: https://${DOMAIN}"
