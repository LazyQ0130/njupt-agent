#!/usr/bin/env bash
set -Eeuo pipefail

LOG_FILE=/root/njupt-bootstrap.log
DONE_FILE=/root/njupt-bootstrap.done
ARCHIVE=/root/njupt-ai-deploy.tar.gz
APP_DIR=/opt/njupt-ai

exec > >(tee -a "$LOG_FILE") 2>&1

echo "[$(date -Is)] NJUPT AI production bootstrap started"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "This script must run as root."
  exit 1
fi

if [[ ! -f "$ARCHIVE" ]]; then
  echo "Deployment archive not found: $ARCHIVE"
  exit 1
fi

if ss -ltnH '( sport = :80 or sport = :443 )' | grep -q .; then
  echo "TCP port 80 or 443 is already in use:"
  ss -ltnp '( sport = :80 or sport = :443 )' || true
  exit 20
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl openssl apache2-utils

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

cat >/etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

if ! swapon --show=NAME --noheadings | grep -q .; then
  if [[ ! -e /swapfile ]]; then
    fallocate -l 2G /swapfile
  fi
  chmod 600 /swapfile
  if ! file /swapfile | grep -q 'swap file'; then
    mkswap /swapfile
  fi
  swapon /swapfile
fi

if ! grep -q '^/swapfile[[:space:]]' /etc/fstab; then
  printf '/swapfile none swap sw 0 0\n' >>/etc/fstab
fi

mkdir -p "$APP_DIR"
tar -xzf "$ARCHIVE" -C "$APP_DIR"
cd "$APP_DIR"

if [[ ! -f .env.production ]]; then
  MYSQL_PASSWORD="$(openssl rand -hex 24)"
  MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"
  REDIS_PASSWORD="$(openssl rand -hex 24)"
  ADMIN_PLAIN_PASSWORD="$(openssl rand -hex 12)"
  ADMIN_PASSWORD_HASH="{bcrypt}$(htpasswd -bnBC 12 '' "$ADMIN_PLAIN_PASSWORD" | tr -d ':\n')"
  ADMIN_JWT_SECRET="$(openssl rand -hex 32)"
  AI_CONFIG_MASTER_KEY="$(openssl rand -base64 32 | tr -d '\n')"
  CRAWLER_SHARED_TOKEN="$(openssl rand -hex 32)"

  export MYSQL_PASSWORD MYSQL_ROOT_PASSWORD REDIS_PASSWORD
  export ADMIN_PASSWORD_HASH ADMIN_JWT_SECRET AI_CONFIG_MASTER_KEY
  export CRAWLER_SHARED_TOKEN

  python3 - <<'PY'
import os
import re
from pathlib import Path

source = Path(".env.production.example").read_text(encoding="utf-8")
replacements = {
    "REPLACE_WITH_RANDOM_HEX": os.environ["MYSQL_PASSWORD"],
    "REPLACE_WITH_DIFFERENT_RANDOM_HEX": os.environ["MYSQL_ROOT_PASSWORD"],
    "REPLACE_WITH_BCRYPT_HASH": "'" + os.environ["ADMIN_PASSWORD_HASH"] + "'",
    "REPLACE_WITH_AT_LEAST_64_RANDOM_HEX_CHARACTERS": os.environ["ADMIN_JWT_SECRET"],
    "REPLACE_WITH_BASE64_ENCODED_32_BYTE_KEY": os.environ["AI_CONFIG_MASTER_KEY"],
    "REPLACE_WITH_DEEPSEEK_KEY": "",
}

for old, new in replacements.items():
    source = source.replace(old, new, 1)

# The second and third generic random placeholders belong to Redis and crawler.
source = source.replace("REPLACE_WITH_RANDOM_HEX", os.environ["REDIS_PASSWORD"], 1)
source = source.replace("REPLACE_WITH_RANDOM_HEX", os.environ["CRAWLER_SHARED_TOKEN"], 1)

unresolved = re.findall(r"REPLACE_WITH_[A-Z0-9_]+", source)
if unresolved:
    raise SystemExit(f"Unresolved production placeholders remain: {unresolved}")

Path(".env.production").write_text(source, encoding="utf-8")
PY

  chmod 600 .env.production
  {
    printf 'URL=https://njupt-ai.top\n'
    printf 'ADMIN_USERNAME=admin\n'
    printf 'ADMIN_PASSWORD=%s\n' "$ADMIN_PLAIN_PASSWORD"
  } >/root/njupt-ai-admin-credentials.txt
  chmod 600 /root/njupt-ai-admin-credentials.txt
fi

docker compose --env-file .env.production -f docker-compose.prod.yml config --quiet
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build

docker compose --env-file .env.production -f docker-compose.prod.yml ps
free -h
df -h /

printf 'completed_at=%s\n' "$(date -Is)" >"$DONE_FILE"
chmod 600 "$DONE_FILE"

echo "[$(date -Is)] NJUPT AI production bootstrap completed"
