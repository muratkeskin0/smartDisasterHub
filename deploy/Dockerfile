# syntax=docker/dockerfile:1

FROM node:20-bookworm-slim AS frontend-build
WORKDIR /src
COPY smartDisasterHubWeb/package.json smartDisasterHubWeb/package-lock.json ./
RUN npm ci
COPY smartDisasterHubWeb/ ./
RUN npm run build

FROM eclipse-temurin:17-jdk-jammy AS backend-build
WORKDIR /src
COPY smartDisasterHub/mvnw smartDisasterHub/pom.xml ./
COPY smartDisasterHub/.mvn ./.mvn
COPY smartDisasterHub/src ./src
RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM ubuntu:22.04 AS runtime
ENV DEBIAN_FRONTEND=noninteractive \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    MYSQL_DATABASE=smart_disaster_hub \
    MYSQL_USER=sdh \
    MYSQL_PASSWORD=change-me \
    MYSQL_ROOT_PASSWORD=change-me-root \
    APP_WEB_URL=http://localhost \
    JWT_SECRET=change-me-jwt

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl openssl nginx supervisor \
    openjdk-17-jre-headless mysql-server \
    python3 python3-pip python3-venv python3-dev \
    build-essential g++ \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /var/log/supervisor /var/www/frontend /docker /app /run/mysqld \
    && chown mysql:mysql /run/mysqld

WORKDIR /app

COPY ml-service/requirements.txt /app/ml-service/requirements.txt
COPY ml-service/scripts /app/ml-service/scripts
COPY ml-service/inference /app/ml-service/inference

RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir --upgrade pip \
    && /opt/venv/bin/pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu \
    && /opt/venv/bin/pip install --no-cache-dir -r /app/ml-service/requirements.txt

COPY --from=backend-build /src/target/smartDisasterHub-*.jar /app/backend.jar
COPY --from=frontend-build /src/dist/smartDisasterHubWebAngular/browser /var/www/frontend

COPY smartDisasterHub/deploy/docker/nginx.http.conf smartDisasterHub/deploy/docker/nginx.ssl.conf /docker/
COPY smartDisasterHub/deploy/docker/supervisord.conf /etc/supervisor/conf.d/supervisord.conf
COPY smartDisasterHub/deploy/docker/entrypoint.sh smartDisasterHub/deploy/docker/start-backend.sh /docker/
RUN chmod +x /docker/entrypoint.sh /docker/start-backend.sh \
    && rm -f /etc/nginx/sites-enabled/default

VOLUME ["/var/lib/mysql", "/app/ml-service/inference/services/text_analyzer/models"]

EXPOSE 80 443

HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=5 \
  CMD curl -sf http://127.0.0.1/api/about >/dev/null || exit 1

ENTRYPOINT ["/docker/entrypoint.sh"]
