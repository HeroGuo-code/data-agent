# ============================================
# Data-Agent 后端：Maven 构建 + JRE 运行
# ============================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 先复制 pom 与 settings.xml 拉取依赖，利用层缓存提升重复构建速度
COPY pom.xml settings.xml ./
RUN mvn -B -q -s settings.xml dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -s settings.xml clean package -DskipTests \
    && ls -1 target/*.jar

# ---- 运行镜像 ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# 安装 curl 与 docker CLI（供 Python 分析沙箱在宿主 docker 中执行，best-effort）
# 若下载失败不阻塞启动：仅影响 Python 深度分析，不影响整体运行
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL https://download.docker.com/linux/static/stable/x86_64/docker-27.5.1.tgz -o /tmp/docker.tgz || true \
    && (tar -xzf /tmp/docker.tgz --strip-components=1 -C /usr/local/bin docker/docker || true) \
    && rm -f /tmp/docker.tgz \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/target/data-agent-backend-java-1.0.jar /app/app.jar

EXPOSE 9933
ENV JAVA_OPTS="-Xms256m -Xmx1024m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
