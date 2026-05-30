#!/bin/bash
set -euo pipefail

for i in $(seq 1 90); do
  if mysqladmin ping -h127.0.0.1 -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" --silent 2>/dev/null; then
    break
  fi
  sleep 2
done

for i in $(seq 1 120); do
  if curl -sf http://127.0.0.1:8000/health >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

exec java -jar /app/backend.jar --spring.profiles.active=docker
