# 用于 GraphAgent 的 Python 分析沙箱镜像（轻量，替代庞大的 continuumio/anaconda3）。
# 构建：docker build -t data-agent-python:latest python-sandbox
# 运行时可用环境变量 DATA_AGENT_PYTHON_DOCKER_IMAGE 覆盖镜像名。
FROM python:3.11-slim
RUN python -m pip install --no-cache-dir --upgrade pip && \
    python -m pip install --no-cache-dir pandas numpy
CMD ["python"]
