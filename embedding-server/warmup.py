"""构建期模型预热脚本：带重试与指数退避，容忍镜像源大文件下载偶发中断

本地缓存（embedding-server/models/）已随构建上下文打包时直接离线加载；
缓存缺失时自动从 HuggingFace 下载（国内网络可设 HF_ENDPOINT=https://hf-mirror.com）。
"""
import os
import sys
import time

from fastembed import TextEmbedding

MODEL = "BAAI/bge-small-zh-v1.5"
CACHE_DIR = os.environ.get("FASTEMBED_CACHE_DIR") or None
MAX_ATTEMPTS = 6


def _has_local_cache() -> bool:
    """检测本地缓存目录是否已包含模型文件（fastembed 缓存目录名形如 fast-<model>）"""
    if not CACHE_DIR:
        return False
    local_dir = os.path.join(CACHE_DIR, f"fast-{MODEL.split('/')[-1].lower()}")
    return os.path.isdir(local_dir) and "model_optimized.onnx" in os.listdir(local_dir)


for attempt in range(1, MAX_ATTEMPTS + 1):
    try:
        TextEmbedding(
            model_name=MODEL,
            cache_dir=CACHE_DIR,
            local_files_only=_has_local_cache(),
        ).embed(["warmup"])
        print(f"[warmup] OK on attempt {attempt}", flush=True)
        sys.exit(0)
    except Exception as exc:  # noqa: BLE001
        print(f"[warmup] attempt {attempt}/{MAX_ATTEMPTS} failed: {exc}", flush=True)
        if attempt == MAX_ATTEMPTS:
            raise
        time.sleep(min(90, 10 * attempt))

sys.exit(1)
