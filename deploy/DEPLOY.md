# Smart Disaster Hub — All-in-One Docker Deploy

Deploy files live in **`smartDisasterHub/deploy/`** (this repo). Build context is the **workspace root** — the parent folder that contains all three clones side by side:

```
workspace/
  smartDisasterHub/      ← this repo (includes deploy/)
  smartDisasterHubWeb/
  ml-service/
  .env                   ← create from deploy/.env.example
  .dockerignore          ← copy from deploy/.dockerignore
```

Single Docker image containing:

- **nginx** + Angular frontend (port 80 / 443)
- **Spring Boot** backend (internal 8080, proxied at `/api`)
- **FastAPI ML** service (internal 8000)
- **MySQL** database (internal 3306, persisted via volume)

## Requirements

- Docker 24+
- **Minimum 4 GB RAM** for the container (ML + Java + MySQL). AWS `t2.micro` (1 GB) is **not enough**.
- Recommended: **AWS EC2 `t3.medium`** (4 GB RAM)

## Local test

```bash
# From workspace root (parent of smartDisasterHub, smartDisasterHubWeb, ml-service):
cp smartDisasterHub/deploy/.dockerignore .dockerignore
cp smartDisasterHub/deploy/.env.example .env
docker compose -f smartDisasterHub/deploy/docker-compose.yml up --build
```

Open: http://localhost:8080

First start downloads ML models (~5–15 min):

```bash
docker compose -f smartDisasterHub/deploy/docker-compose.yml logs -f
```

## Build image only

```bash
docker build -f smartDisasterHub/deploy/Dockerfile -t smart-disaster-hub:latest .
```

## Run on AWS EC2

### 1. Launch instance

- AMI: **Ubuntu 22.04**
- Type: **t3.medium** (4 GB RAM)
- Storage: 30 GB gp3
- Security group: **TCP 80**, **TCP 443**, **22** from `0.0.0.0/0`

### 2. Install Docker

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2
sudo usermod -aG docker $USER
newgrp docker
```

### 3. Clone repos and build

```bash
mkdir -p ~/graduation_project && cd ~/graduation_project
git clone https://github.com/muratkeskin0/smartDisasterHub.git
git clone https://github.com/muratkeskin0/smartDisasterHubWeb.git
git clone https://github.com/muratkeskin0/ml-service.git

cp smartDisasterHub/deploy/.dockerignore .dockerignore
cp smartDisasterHub/deploy/.env.example .env
nano .env   # set secrets and APP_WEB_URL

docker compose -f smartDisasterHub/deploy/docker-compose.yml \
               -f smartDisasterHub/deploy/docker-compose.prod.yml \
               up -d --build
```

### 4. HTTPS (Let's Encrypt)

```bash
cd ~/graduation_project
chmod +x smartDisasterHub/deploy/docker/init-ssl.sh
./smartDisasterHub/deploy/docker/init-ssl.sh
```

Production uses ports **80** and **443** with TLS nginx config.

Open: **https://smartdisasterhub.site**

## Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `APP_WEB_URL` | Yes (prod) | Public URL for CORS and emails |
| `SSL_DOMAIN` | Prod HTTPS | Domain for TLS (default: smartdisasterhub.site) |
| `MYSQL_PASSWORD` | Yes | App DB user password |
| `MYSQL_ROOT_PASSWORD` | Yes | MySQL root password |
| `JWT_SECRET` | Yes | JWT signing secret |
| `ADMIN_PASSWORD` | Recommended | Change default admin seed |
| `MAIL_PASSWORD` | Optional | Gmail app password |
| `REDDIT_*` | Optional | OAuth when approved |

## Troubleshooting

**Container exits / OOM:** Use t3.medium or larger.

**Slow first start:** Model download + ML load is normal.

**502 on /api:** Backend still starting; wait 2–3 minutes.

**Health check failing:** Ensure ports 80/443 are open and backend finished booting.
