#!/bin/bash
set -euo pipefail

MYSQL_DATABASE="${MYSQL_DATABASE:-smart_disaster_hub}"
MYSQL_USER="${MYSQL_USER:-sdh}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-sdh_local_pass}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root_local_pass}"
APP_WEB_URL="${APP_WEB_URL:-http://localhost}"
JWT_SECRET="${JWT_SECRET:-change-me-in-production-$(openssl rand -hex 16)}"
MYSQL_BOOTSTRAP_MARKER="/var/lib/mysql/.sdh-bootstrapped"
MYSQLD="/usr/sbin/mysqld"

export MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD APP_WEB_URL JWT_SECRET

log() {
  echo "[entrypoint] $*"
}

wait_for_mysql_root() {
  for i in $(seq 1 60); do
    if mysqladmin ping --silent -uroot "$@" 2>/dev/null; then
      return 0
    fi
    sleep 2
  done
  return 1
}

run_bootstrap_sql() {
  local -a auth=( -uroot )
  if ! mysql "${auth[@]}" -e "SELECT 1" >/dev/null 2>&1; then
    auth=( -uroot -p"${MYSQL_ROOT_PASSWORD}" )
  fi

  mysql "${auth[@]}" <<SQL
CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${MYSQL_USER}'@'localhost' IDENTIFIED BY '${MYSQL_PASSWORD}';
CREATE USER IF NOT EXISTS '${MYSQL_USER}'@'127.0.0.1' IDENTIFIED BY '${MYSQL_PASSWORD}';
GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${MYSQL_USER}'@'localhost';
GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${MYSQL_USER}'@'127.0.0.1';
ALTER USER 'root'@'localhost' IDENTIFIED BY '${MYSQL_ROOT_PASSWORD}';
FLUSH PRIVILEGES;
SQL
}

init_mysql_if_needed() {
  if [ -f "${MYSQL_BOOTSTRAP_MARKER}" ]; then
    log "MySQL already bootstrapped"
    return 0
  fi

  if [ ! -d /var/lib/mysql/mysql ]; then
    log "Initializing MySQL data directory..."
    ${MYSQLD} --initialize-insecure --user=mysql
  fi

  log "Bootstrapping database and users..."
  ${MYSQLD} --user=mysql &
  MYSQL_PID=$!

  wait_for_mysql_root || wait_for_mysql_root -p"${MYSQL_ROOT_PASSWORD}" || {
    log "MySQL bootstrap failed"
    exit 1
  }

  run_bootstrap_sql
  touch "${MYSQL_BOOTSTRAP_MARKER}"

  mysqladmin -uroot -p"${MYSQL_ROOT_PASSWORD}" shutdown
  wait "${MYSQL_PID}" 2>/dev/null || true
  log "MySQL bootstrap complete"
}

ensure_ml_models() {
  if [ -f /app/ml-service/inference/services/text_analyzer/models/model.pkl ]; then
    log "ML models already present"
    return 0
  fi

  log "Downloading ML models (first start may take several minutes)..."
  cd /app/ml-service
  /opt/venv/bin/python scripts/download_models.py
}

configure_nginx() {
  SSL_DOMAIN="${SSL_DOMAIN:-smartdisasterhub.site}"
  SSL_CERT="/etc/letsencrypt/live/${SSL_DOMAIN}/fullchain.pem"

  if [ -f "${SSL_CERT}" ]; then
    sed "s/__SSL_DOMAIN__/${SSL_DOMAIN}/g" /docker/nginx.ssl.conf > /etc/nginx/conf.d/default.conf
    log "TLS enabled for ${SSL_DOMAIN}"
  else
    cp /docker/nginx.http.conf /etc/nginx/conf.d/default.conf
    log "TLS not configured; serving HTTP only (run docker/init-ssl.sh on the host to enable HTTPS)"
  fi
}

init_mysql_if_needed
ensure_ml_models
configure_nginx

log "Starting all services (nginx on :80, APP_WEB_URL=${APP_WEB_URL})..."
exec /usr/bin/supervisord -c /etc/supervisor/conf.d/supervisord.conf
