# ============================================
# Data-Agent 前端：Node 构建 + Nginx 托管并代理 API
# ============================================
FROM node:22-slim AS build
ENV CI=true
RUN npm install -g corepack && corepack enable
WORKDIR /build
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile --config.dangerouslyAllowAllBuilds=true
COPY . .
# 仅构建（跳过 type-check 以提升镜像构建稳定性；如需类型检查可改用 pnpm build）
RUN pnpm build-only

# ---- 运行镜像：Nginx ----
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
