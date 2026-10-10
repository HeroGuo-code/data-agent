# Data Agent 嵌入服务（FastEmbed + bge-small-zh-v1.5）
# 本地存在 models/ 缓存时随镜像打包（离线构建）；缺失时构建期自动从 HuggingFace 下载
FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PORT=8000 \
    EMBED_MODEL=BAAI/bge-small-zh-v1.5 \
    FASTEMBED_CACHE_DIR=/app/models

WORKDIR /app

# 系统依赖（onnxruntime 运行时所需）
RUN apt-get update && apt-get install -y --no-install-recommends \
        libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN pip install -i https://pypi.org/simple -r requirements.txt

COPY embedding_server.py warmup.py ./
# 其余构建上下文：若本地存在 models/ 模型缓存则随镜像打包（离线构建）；
# 缺失时 warmup.py 会在构建期自动从 HuggingFace 下载（约 90MB）
COPY . .

# 构建时 warmup 校验（优先命中本地缓存；无缓存则联网下载后校验）
RUN python warmup.py

EXPOSE 8000

HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health')" || exit 1

CMD ["python", "embedding_server.py"]
