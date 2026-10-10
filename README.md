# Data-Agent：电商经营数据智能问答系统（Text2SQL Agent）

一个基于 Java Spring Boot + Spring AI + PostgreSQL/pgvector 的端到端 Text2SQL 智能体：把自然语言问题转成 SQL 并执行查询、自动生成图文报告。内置电商演示库（店铺 / 商品 / 订单 / 库存 / 退款），支持知识库检索、人工确认（HITL）、只读 SQL 安全校验、Python 沙箱分析、A2A 协议集成，全套可用 Docker 一条命令拉起。

## 核心功能

- **自然语言 → SQL 数据问答**：13 节点 StateGraph 编排，覆盖「查询改写 → 证据召回 → Schema 召回 → 表关系推断 → 可行性评估 → 规划 → 人工确认 → SQL 生成/执行 → Python 生成/执行/分析 → 报告生成」全链路。
- **四路 RAG 召回（pgvector HNSW）**：表结构 / 列结构 / 业务词表 / 问答示例四类知识共存同一向量空间（`bge-small-zh-v1.5`，512 维），通过 `vector_type` 元数据过滤实现四路独立召回，并叠加结构化外键关系元数据，显著减少 SQL 幻觉。
- **只读安全与权限隔离**：SQL 执行前强制只读校验（仅 SELECT，禁止 DROP/ALTER/DELETE/TRUNCATE/多语句）；运营仅见所属店铺、客服仅见单买家，手机号/地址自动脱敏。
- **人工确认（HITL）**：复杂问题在人工确认节点暂停，等待前端「提交人工输入」后继续执行。
- **Python Docker 沙箱分析**：`SimplePythonExecutor` 通过宿主 Docker 调用独立沙箱镜像执行分析脚本，与主服务隔离。
- **流式与 Agent 互操作**：SSE 流式输出 + A2A(JSON-RPC) 协议，可作为上层客服等主 Agent 的子 Agent。
- **节点级可观测**：每个节点记录 Prompt 快照、输入输出与耗时，调试页提供火焰图 / 调用树回放。
- **业务与运营管理页**：商品 / 订单 / 库存 / 退款 / 店铺管理，以及数据资产、知识库、调试管理、反馈管理。

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 21 · Spring Boot 3.5 · Spring AI · Jimmer(ORM) |
| 图编排 | Spring AI StateGraph（条件边 + 13 节点） |
| 向量/存储 | PostgreSQL 18 + pgvector(HNSW) · Redis · SQLite(演示库) |
| 前端 | Vue 3 · TypeScript · Vite · Element Plus |
| 模型 | 任意 OpenAI 兼容接口（如 deepseek / gpt / kimi 系列）+ 本地 Embedding(`bge-small-zh-v1.5`，512 维) |
| Embedding 服务 | 本地 FastEmbed（embedding-server, :8000，模型随镜像打包） |
| 集成 | A2A(JSON-RPC) · SSE 流式 |
| 部署 | Docker Compose（6 个容器一键拉起） |

## 项目目录结构

```
data-agent/
├── data-agent-backend-java/          # Java Spring Boot 后端 (:9933)
│   ├── src/main/java/.../server/     # 业务模块
│   │   ├── agent/                    # StateGraph 编排：节点/边/提示词
│   │   ├── asset/                    # 数据资产 / schema 摘要接口
│   │   ├── dataset/                  # 表/列元数据 + 知识 + 启动时 schema 入库
│   │   ├── mall/                     # 电商业务：商品/订单/库存/退款 CRUD
│   │   ├── kb/                       # 知识库管理（问答示例/词表/口径）
│   │   ├── model/                    # 模型（LLM + Embedding）运行时配置
│   │   ├── security/                 # 只读 SQL 校验 + 权限拦截 + 脱敏
│   │   ├── trace/                    # 节点级 trace（火焰图/调用树）
│   │   └── integration/a2a/          # A2A(JSON-RPC) 集成
│   ├── src/main/resources/
│   │   ├── application.yml           # 数据源/模型/向量库配置（Key 走环境变量）
│   │   ├── dev_20240627/             # 演示 SQLite：业务表 + 知识库 seed
│   │   └── prompts/*.st              # 各节点提示词模板
│   ├── python-sandbox/               # Python 分析沙箱（Docker 镜像）
│   ├── Dockerfile
│   └── pom.xml
├── data-agent-frontend/              # Vue3 + TS + Vite 前端
│   ├── src/views/                    # 数据问答/订单/商品/库存/退款/数据资产/知识库/调试/反馈
│   ├── src/api · components · router · stores
│   ├── Dockerfile + nginx.conf       # 构建托管 + /api 反代（SSE 不缓冲）
│   └── Dockerfile.prebuilt           # 低内存服务器：本地构建产物直接 COPY
├── embedding-server/                 # 本地 Embedding 服务 (:8000, bge-small-zh-v1.5)
├── docs/                             # 架构设计/提示词/SQL安全/数据源/生产规范
├── docker-compose.yml                # 一键编排（PG/Redis/Embedding/沙箱/后端/前端）
├── database.sql                      # PostgreSQL 初始化 dump（元数据+知识+示例）
├── install.sh                        # 服务器一键部署脚本
└── README.md
```

## 快速开始（Docker 一键）

**前置条件**：已安装 Docker 与 Docker Compose（建议 Docker Engine 24+，能访问外网拉取镜像与下载模型）。

1. 创建模型配置文件 `.env`（参考 [.env.example](.env.example)，问答功能必需）：

```bash
cp .env.example .env
# 编辑 .env，填入你的模型中转地址与 Key
```

2. 构建并启动全部服务（首次会拉镜像 + 初始化数据库，耗时较长）：

```bash
docker compose up -d --build
```

3. 访问前端：<http://localhost:8080>（后端 API 由前端 Nginx 统一反代到 `/api/*`）。

> **Embedding 模型说明**：构建 embedding-server 镜像时，若本地存在 `embedding-server/models/` 缓存则直接打包离线加载；缺失时会自动从 HuggingFace 下载 `bge-small-zh-v1.5`（约 90MB）。国内网络建议先在宿主机下载模型文件放入 `embedding-server/models/fast-bge-small-zh-v1.5/`（含 `model_optimized.onnx`、`tokenizer.json` 等）。

**常用命令**：

```bash
docker compose ps              # 查看各服务健康状态
docker compose logs -f backend # 看后端日志
docker compose down            # 停止但不删数据
docker compose down -v         # 停止并清空数据卷（重建演示数据）
docker compose build           # 重新构建镜像
```

> 仅运行 Python 分析沙箱（后端通过宿主 Docker 调用）时启动对应 profile：`docker compose --profile sandbox up -d python-sandbox`。

## 配置说明

**Docker Compose 环境变量**（项目根目录 `.env`）：

| 变量 | 说明 | 默认 |
|------|------|------|
| `OPENAI_BASE_URL` | 模型中转地址（OpenAI 协议，不带 /v1） | `https://api.openai.com` |
| `OPENAI_API_KEY` | 模型 Key（勿硬编码、勿入库） | — |
| `OPENAI_MODEL` | 模型名 | `deepseek/deepseek-chat` |
| `FRONTEND_PORT` | 前端对外端口 | `8080` |

**端口一览**：

| 端口 | 服务 | 暴露方式 |
|------|------|----------|
| `8080` | 前端（Nginx，含 `/api` 反代） | 唯一对外端口 |
| `9933` | 后端 API | 仅容器网络内 |
| `5432` | PostgreSQL（pgvector） | 仅容器网络内 |
| `8000` | Embedding 服务 | 本地开发对外 |
| `6379` | Redis | 仅容器网络内 |

**本地开发（非 Docker）**：后端 `mvn spring-boot:run`（需先启动 PG + Embedding + Redis），前端 `pnpm install && pnpm dev`（Vite 默认端口，代理已配置到后端）。

## 低内存服务器部署（4G 内存实测）

- 后端 JVM 限制 `-Xmx700m`，Redis 限 `128mb`，防止多服务互相挤压内存。
- 小内存服务器建议前端在本地构建后改用 `data-agent-frontend/Dockerfile.prebuilt`（纯 COPY 产物，避免服务器端 Vite 构建耗尽内存导致 OOM）。
- SSE 双层代理逐层关闭缓冲：容器 `nginx.conf` 与宿主 Nginx 均为 `proxy_buffering off` + 300s 超时，保证 13 节点长链路流式输出不中断。
- 一键部署：`bash install.sh`（自动安装 Docker、创建 swap、构建启动、健康检查、生成宿主 Nginx 站点配置；可用 `SITE_DOMAIN=你的域名` 覆盖默认域名）。

## 数据与模拟数据

- **业务模拟数据**：`data-agent-backend-java/src/main/resources/dev_20240627/dev_databases/ecommerce_ops/ecommerce_ops.sqlite` —— 店铺 / 商品 / 订单 / 买家 / 退款 / 活动等电商业务表。
- **知识库数据**：`data-agent-backend-java/src/main/resources/dev_20240627/knowledge_base.sqlite` —— 业务词表 / 指标口径 / 问答示例。
- **PostgreSQL 初始化 dump**：`database.sql` —— agent 元数据表（`db_table` / `db_column` / `db_foreign_key` / `glossary_knowledge` / `question_knowledge` / `vector_store`）。
- 以上 seed 数据会随项目一起发布；运行期 `data-agent-backend-java/data/` 下的 sqlite 副本与 trace 为程序自动生成，不入版本库。启动后端时 `EcommerceSchemaInitializer` 会把演示库表/列/外键写入 PG 并向量化。

## 设计文档

| 文档 | 说明 |
|------|------|
| [docs/01-架构设计-模块划分.md](docs/01-%E6%9E%B6%E6%9E%84%E8%AE%BE%E8%AE%A1-%E6%A8%A1%E5%9D%97%E5%88%92%E5%88%86.md) | 整体架构与模块划分、数据流 |
| [docs/02-系统提示词.md](docs/02-%E7%B3%BB%E7%BB%9F%E6%8F%90%E7%A4%BA%E8%AF%8D.md) | 各节点系统提示词说明 |
| [docs/03-FunctionCall定义.json.md](docs/03-FunctionCall%E5%AE%9A%E4%B9%89.json.md) | FunctionCall 定义 |
| [docs/04-SQL安全与权限代码模板.md](docs/04-SQL%E5%AE%89%E5%85%A8%E4%B8%8E%E6%9D%83%E9%99%90%E4%BB%A3%E7%A0%81%E6%A8%A1%E6%9D%BF.md) | SQL 只读校验与权限/脱敏模板 |
| [docs/05-数据源对接规范.md](docs/05-%E6%95%B0%E6%8D%AE%E6%BA%90%E5%AF%B9%E6%8E%A5%E8%A7%84%E8%8C%83.md) | MySQL / 数据中台 API 接入规范 |
| [docs/06-测试与生产安全差异化.md](docs/06-%E6%B5%8B%E8%AF%95%E4%B8%8E%E7%94%9F%E4%BA%A7%E5%AE%89%E5%85%A8%E5%B7%AE%E5%BC%82%E5%8C%96.md) | 测试/生产安全差异与生产增强建议 |
