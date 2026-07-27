# `njupt-ai.top` 阿里云香港 ECS 部署手册

目标环境：

- 域名：`https://njupt-ai.top`
- ECS：阿里云香港 C 区，Ubuntu 22.04，2 vCPU / 4 GiB
- 公网 IP：`47.76.155.36`
- 入口：Caddy 自动 HTTPS
- 应用：Docker Compose

香港节点无需工信部 ICP 备案。域名 A 记录已经指向 ECS；证书会在公网
80/443 可访问且 Compose 启动后由 Caddy 自动申请和续期。

## 0. 上线前先处理云资源

当前实例到期时间为 2026-08-26。正式上线前先续费，并建议：

- 将公网带宽从 1 Mbps 提升到至少 3–5 Mbps；
- 为系统盘创建快照；
- 安全组只公开 TCP 80、TCP/UDP 443；
- TCP 22 仅允许管理员当前公网 IP；
- 不公开 3306、6379、8080、8090、8091。

## 1. 连接服务器

在本机终端连接：

```bash
ssh root@47.76.155.36
```

如果实例使用非 root 管理员，将 `root` 换成实际用户名并在后续命令前保留
`sudo`。

## 2. 安装 Docker Engine

按 Docker 官方 Ubuntu APT 仓库方式安装：

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker version
sudo docker compose version
```

## 3. 为 4 GiB 机器增加交换空间

先检查：

```bash
swapon --show
```

如果没有输出，创建 2 GiB swap：

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

## 4. 上传项目

将项目上传到服务器的 `/opt/njupt-ai`，确保该目录中能看到：

```text
Dockerfile
docker-compose.prod.yml
.env.production.example
backend/
crawler-service/
knowledge-service/
deploy/
src/
```

上传后：

```bash
cd /opt/njupt-ai
```

## 5. 创建生产密钥

生成三个独立的十六进制随机值，分别用于 MySQL、Redis、JWT 和爬虫令牌：

```bash
openssl rand -hex 32
openssl rand -hex 32
openssl rand -hex 32
openssl rand -hex 32
```

生成 AI 配置加密主密钥：

```bash
openssl rand -base64 32
```

生成管理员 BCrypt 密码摘要：

```bash
sudo apt install -y apache2-utils
htpasswd -nBC 12 admin
```

命令会交互式要求输入密码，输出类似：

```text
admin:$2y$12$...
```

将冒号后面的完整 `$2y$...` 保存为 `ADMIN_PASSWORD_HASH`，不要保存明文密码。

## 6. 填写生产环境变量

```bash
cp .env.production.example .env.production
chmod 600 .env.production
nano .env.production
```

必须替换全部 `REPLACE_*`。尤其是：

```text
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD
REDIS_PASSWORD
ADMIN_PASSWORD_HASH
ADMIN_JWT_SECRET
AI_CONFIG_MASTER_KEY
DEEPSEEK_API_KEY
CRAWLER_SHARED_TOKEN
```

`PUBLIC_ORIGIN` 和 `CORS_ALLOWED_ORIGINS` 保持：

```env
PUBLIC_ORIGIN=https://njupt-ai.top
CORS_ALLOWED_ORIGINS=https://njupt-ai.top
RAG_READ_TIMEOUT=10s
RAG_INDEX_READ_TIMEOUT=60s
```

`EMBEDDING_PROVIDER=hash` 仅用于第一次冒烟测试。知识库正式提供服务前，应换成
真实的 OpenAI-compatible Embedding 服务，并填写对应 API Key、Base URL 和模型。

## 7. 首次启动

先验证配置：

```bash
sudo docker compose \
  --env-file .env.production \
  -f docker-compose.prod.yml \
  config --quiet
```

构建并启动：

```bash
sudo docker compose \
  --env-file .env.production \
  -f docker-compose.prod.yml \
  up -d --build
```

香港 ECS 当前只有 1 Mbps 带宽，第一次拉取基础镜像可能需要较长时间。不要在镜像
仍在下载时重复执行启动命令。

查看状态：

```bash
sudo docker compose \
  --env-file .env.production \
  -f docker-compose.prod.yml \
  ps
```

跟踪入口和后端日志：

```bash
sudo docker compose \
  --env-file .env.production \
  -f docker-compose.prod.yml \
  logs -f --tail=100 gateway backend
```

Caddy 在 80/443 可访问且 DNS 正确时自动签发证书，并自动把 HTTP 跳转到 HTTPS。

## 8. 验收

```bash
curl -I https://njupt-ai.top
curl -sS -X POST https://njupt-ai.top/api/session/anonymous
```

然后在浏览器依次检查：

- `https://njupt-ai.top/`
- `https://njupt-ai.top/chat`
- `https://njupt-ai.top/admin/login`
- 学生端匿名提问
- 管理员登录、文档上传、知识库索引
- 定向采集

公网访问以下地址应失败：

```text
47.76.155.36:3306
47.76.155.36:6379
47.76.155.36:8080
47.76.155.36:8090
47.76.155.36:8091
```

## 9. 日常更新

上传新代码后，在项目目录执行：

```bash
sudo docker compose \
  --env-file .env.production \
  -f docker-compose.prod.yml \
  up -d --build
```

查看最近日志：

```bash
sudo docker compose \
  --env-file .env.production \
  -f docker-compose.prod.yml \
  logs --tail=200
```

不要执行 `docker compose down -v`，其中的 `-v` 会删除数据库、向量库和上传文件卷。
