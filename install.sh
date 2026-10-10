#!/bin/bash
# ============================================================
# Data-Agent 服务器一键部署脚本
# 用法：把整个项目上传到服务器（如 /root/data-agent），解压后：
#   cd data-agent && bash install.sh
# 自定义域名（需已解析到本机）：SITE_DOMAIN=example.com bash install.sh
# 重复执行安全：已启动则跳过构建，直接修正配置并检查健康。
# ============================================================
set -uo pipefail

DOMAIN="${SITE_DOMAIN:-data.guohaoai.com}"
FRONTEND_PORT=18080          # 容器对宿主的唯一端口（宿主 Nginx 反代 80/443 -> 此端口）
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
NGINX_CONF="/etc/nginx/conf.d/${DOMAIN}.conf"

log() { echo -e "\033[32m[部署]\033[0m $*"; }
warn() { echo -e "\033[33m[警告]\033[0m $*"; }
fail() { echo -e "\033[31m[失败]\033[0m $*"; exit 1; }

[ "$(id -u)" = "0" ] || fail "请用 root 执行（宝塔 Web 终端默认即 root）"

cd "$APP_DIR" || fail "目录不存在：$APP_DIR"

# ---------- 1. Docker 环境 ----------
if ! command -v docker >/dev/null 2>&1; then
  log "未检测到 Docker，开始安装（境外服务器直连官方源）..."
  curl -fsSL https://get.docker.com | bash || fail "Docker 安装失败"
  systemctl enable --now docker || true
fi
docker info >/dev/null 2>&1 || fail "Docker daemon 未运行，请执行 systemctl start docker"
if docker compose version >/dev/null 2>&1; then
  log "Docker Compose 插件就绪"
else
  log "缺少 compose 插件，自动安装..."
  mkdir -p /usr/local/lib/docker/cli-plugins /usr/local/libexec/docker/cli-plugins 2>/dev/null
  curl -fsSL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
    -o /usr/local/lib/docker/cli-plugins/docker-compose \
    || curl -fsSL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
    -o /usr/local/libexec/docker/cli-plugins/docker-compose \
    || fail "compose 插件下载失败，请检查网络后重试"
  chmod +x /usr/local/lib/docker/cli-plugins/docker-compose /usr/local/libexec/docker/cli-plugins/docker-compose 2>/dev/null
  docker compose version >/dev/null 2>&1 || fail "compose 插件安装后仍不可用，请手动排查"
fi
log "Docker: $(docker --version)"

# ---------- 2. 内存与 swap（4G 机器 + 已有业务，必须防 OOM）----------
FREE_MB=$(free -m | awk '/^Mem:/{print $7}')
if [ "$FREE_MB" -lt 1500 ]; then
  warn "当前可用内存仅 ${FREE_MB}MB，建议确认现有服务的内存占用"
fi
if ! swapon --show | grep -q .; then
  log "无 swap，创建 2G swap 防 OOM..."
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# ---------- 3. 端口与 .env ----------
if ss -tlnp 2>/dev/null | grep -q ":${FRONTEND_PORT} "; then
  fail "端口 ${FRONTEND_PORT} 已被占用，请修改脚本顶部 FRONTEND_PORT 换一个端口"
fi
# FRONTEND_PORT 写入 .env 供 compose 引用
touch .env
grep -q '^FRONTEND_PORT=' .env || echo "FRONTEND_PORT=${FRONTEND_PORT}" >> .env
grep -q '^OPENAI_API_KEY=' .env || fail ".env 缺少 OPENAI_API_KEY（问答功能必需）"
log ".env 配置检查通过（FRONTEND_PORT=${FRONTEND_PORT}）"

# ---------- 4. 构建并启动（6 容器：pg/redis/embedding/python-sandbox/backend/frontend）----------
log "开始构建镜像（首次约 10~20 分钟，境外网络直连 Maven/npm/HF 均畅通）..."
docker compose build || fail "镜像构建失败"
docker compose build python-sandbox >/dev/null 2>&1 || warn "python-sandbox 镜像构建失败，仅影响 Python 深度分析功能"
docker compose up -d || fail "容器启动失败"

# ---------- 5. 等待健康 ----------
log "等待服务健康（后端 Spring Boot 启动约 40~90 秒）..."
OK=""
for i in $(seq 1 40); do
  sleep 6
  ST=$(docker inspect -f '{{.State.Health.Status}}' data-agent-backend 2>/dev/null || echo unknown)
  FE=$(docker inspect -f '{{.State.Health.Status}}' data-agent-frontend 2>/dev/null || echo unknown)
  echo "  [${i}] backend=${ST} frontend=${FE}"
  if [ "$ST" = "healthy" ] && [ "$FE" = "healthy" ]; then OK=1; break; fi
done
[ -n "$OK" ] || { docker logs --tail 40 data-agent-backend; fail "服务未在预期时间内健康"; }

# ---------- 6. 宿主 Nginx 站点（SSE 不缓冲 + 长超时）----------
if command -v nginx >/dev/null 2>&1 && [ -d /etc/nginx/conf.d ]; then
  cat > "$NGINX_CONF" <<EOF
server {
    listen 80;
    server_name ${DOMAIN};
    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:${FRONTEND_PORT};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        # SSE 关键：不缓冲 + 长超时（13 节点流式链路一次可跑数分钟）
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
        proxy_set_header Connection "";
    }
}
EOF
  nginx -t 2>/dev/null && { nginx -s reload 2>/dev/null || systemctl reload nginx; log "Nginx 站点已生效：${NGINX_CONF}"; } \
    || warn "nginx -t 校验失败，请检查 ${NGINX_CONF}（不影响容器运行）"
else
  warn "未找到系统 nginx 或 /etc/nginx/conf.d，请手动在宝塔添加站点反代 127.0.0.1:${FRONTEND_PORT}"
fi

# ---------- 7. 验收 ----------
sleep 2
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -m 8 "http://127.0.0.1:${FRONTEND_PORT}/" || echo 000)
API=$(curl -s -o /dev/null -w '%{http_code}' -m 8 "http://127.0.0.1:${FRONTEND_PORT}/api/asset/summary" || echo 000)
log "本机验收：前端=${HTTP} 后端API=${API}"
echo "============================================================"
log "部署完成！浏览器访问：https://${DOMAIN}"
echo "  - Cloudflare SSL 需为 Flexible（zone 级已配置，子域自动沿用）"
echo "  - 常用命令：cd ${APP_DIR} && docker compose stop|start|down"
echo "  - 查看日志：docker logs -f data-agent-backend"
echo "============================================================"
