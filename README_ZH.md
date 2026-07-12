# Smart Agent Framework

**中文文档** | [English](README.md)

基于 **Spring Boot 3.x + AgentScope + Vue 3** 的通用 AI Agent 开发框架，支持通过 **HTTP API** 或 **钉钉机器人** 快速构建 AI 智能体。

> 🚀 **5 分钟快速启动**：复制 `.env.example` → `.env` → 填入 `LLM_API_KEY` → `docker compose up -d` → 打开 http://localhost:5173

## 开始前检查

- [ ] JDK 21+ 已安装 (`java -version`)
- [ ] Docker Desktop 已安装（推荐） **或** MySQL 8.x 本地运行
- [ ] DashScope API Key 已获取：https://dashscope.console.aliyun.com/ （免费注册，赠送 token）
- [ ] 端口 8080、5173 未被占用

## 功能特性

| 功能 | 说明 |
|------|------|
| Supervisor/SubAgent 编排 | Supervisor 模式，子 Agent 通过 `@Component` 自动注册 |
| ReAct 推理 | AgentScope ReActAgent + function calling |
| 记忆持久化 | SlidingWindowMemory + DatabaseSession，多实例共享 |
| 会话并发锁 | 同一会话请求串行化，防止数据覆盖 |
| 数据自动清理 | 定时清理过期会话和消息，保留天数可配 |
| 敏感信息脱敏 | API 输出自动脱敏手机号、身份证号 |
| Redis 增强 | 分布式锁、Session 缓存、Token 共享、限流、去重（可选） |
| RAG 知识检索 | Milvus 向量数据库 + DashScope Embedding（可选） |
| MCP 客户端 | 标准 MCP 协议（HTTP/SSE）集成外部工具（可选） |
| 动态配置 | Nacos 热加载提示词和系统配置（可选） |
| 技能系统 | Classpath + Git 仓库动态加载 |
| 钉钉机器人 | Stream 模式 + AI 流式卡片，支持单聊（可选） |
| Vue 3 前端 | 对话历史 + 流式回复 + 思考过程展示 + 用户认证 |
| Swagger API | springdoc-openapi，开箱即用 |

## 技术栈

| 组件 | 方案 |
|------|------|
| 后端框架 | Spring Boot 3.3.6 + Java 21 |
| Agent 框架 | AgentScope 1.0.12 |
| 配置中心 | Nacos 2.4.x（可选） |
| 数据库 | MySQL 8.x + Druid + MyBatis-Plus |
| 缓存 | Redis 7.x + Lettuce（可选） |
| 向量数据库 | Milvus 2.x + etcd + MinIO（可选） |
| 大模型 | 阿里云灵积 DashScope（OpenAI 兼容 API） |
| 前端 | Vue 3 + Element Plus + DOMPurify + marked |
| API 文档 | springdoc-openapi (Swagger UI) |
| 构建 | Maven 3.9+ 多模块 + Docker Compose |

## 系统架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                         浏览器 (Vue 3)                              │
│                     http://localhost:5173                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────────────────┐  │
│  │  登录注册 │  │  会话管理 │  │  对话面板                        │  │
│  │  JWT 认证 │  │  历史列表 │  │  Markdown · 思考过程 · 赞踩反馈 │  │
│  └──────────┘  └──────────┘  └──────────────────────────────────┘  │
└─────────────────────────────┬───────────────────────────────────────┘
                              │ HTTP / SSE
┌─────────────────────────────▼───────────────────────────────────────┐
│                   Spring Boot 3.3 (8080)                            │
│  ┌───────────┐  ┌──────────────┐  ┌────────────────────────────┐  │
│  │ 用户认证   │  │  Agent 对话   │  │  RAG 知识库               │  │
│  │ 注册/登录  │  │  同步/流式    │  │  索引/检索                │  │
│  └───────────┘  └──────┬───────┘  └────────────────────────────┘  │
│                        │                                           │
│  ┌─────────────────────▼──────────────────────────────────────┐   │
│  │              SupervisorAgent (ReAct 推理)                   │   │
│  │  模型: Qwen-Turbo  │  提示词: Nacos 热加载                 │   │
│  └──────────┬──────────────────────────────┬──────────────────┘   │
│             │                              │                       │
│  ┌──────────▼──────────┐      ┌───────────▼──────────────────┐   │
│  │   DemoAgent          │      │   CodeAgent                 │   │
│  │   天气 · 计算器      │      │   代码审查 · API 设计       │   │
│  │   搜索 · 翻译        │      │   格式化 · 单元测试         │   │
│  │   知识库检索         │      │   知识库检索                │   │
│  └─────────────────────┘      └──────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  服务层: 会话锁 · 限流 · 数据清理 · 技能加载                │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
┌──────────────────┐  ┌──────────────┐  ┌──────────────────┐
│     MySQL 8.0    │  │   Redis 7    │  │  灵积 DashScope   │
│  会话 · 消息     │  │  锁 · 缓存   │  │  Qwen-Max         │
│  用户 · 反馈     │  │  限流 · 去重 │  │  Qwen-Turbo       │
└──────────────────┘  └──────────────┘  │  text-embedding   │
          │                   │          └──────────────────┘
┌──────────────────┐  ┌──────────────┐  ┌──────────────────┐
│   Milvus 2.4     │  │  Nacos 2.3   │  │  钉钉 Stream      │
│  ┌─ etcd ────┐   │  │  提示词热加载 │  │  WebSocket 长连接 │
│  │ ┌ MinIO ┐ │   │  │  系统配置    │  │  AI 流式卡片      │
│  │ │向量存储│ │   │  │              │  │                  │
│  │ └───────┘ │   │  └──────────────┘  └──────────────────┘
│  └───────────┘   │
└──────────────────┘
```

## 前端界面

| 登录 | 对话 |
|------|------|
| ![登录](docs/images/login.png) | ![对话](docs/images/chat.png) |

| 思考过程 & 执行路线 | 钉钉机器人 | Swagger API |
|--------------------|-----------|-------------|
| ![思考](docs/images/thinking.png) | ![钉钉](docs/images/dingtalk.png) | ![Swagger](docs/images/swagger.png) |

| 执行路线 |
|---------|
| ![路线](docs/images/route.png) |

## 目录结构

```
smart-agent-framework/
├── pom.xml                    # 父 POM
├── start.sh                   # 启动/停止/重启脚本 (Linux/Mac)
├── start.bat                  # 启动脚本 (Windows)
├── smart-agent-core/          # 核心模块
│   ├── agent/                 #   Supervisor、SubAgent、Memory、Session、SessionLock
│   ├── callback/chatbot/      #   钉钉 Stream 回调处理
│   ├── component/             #   AgentChatComponent（对话编排）
│   ├── dingtalk/              #   钉钉 AccessToken、AI 卡片
│   ├── mcp/                   #   MCP 客户端配置
│   ├── nacos/                 #   提示词热加载、系统配置管理
│   ├── persistence/           #   Entity、Mapper
│   ├── rag/                   #   Embedding、Milvus、RagService
│   ├── service/               #   消息服务、会话映射服务
│   └── util/                  #   JsonUtils、SensitiveUtils、SseEventHelper
├── smart-agent-start/         # 启动模块
│   ├── controller/            #   REST API Controller
│   ├── demo/                  #   示例 SubAgent 和 Tools
│   └── resources/             #   application.yml、logback-spring.xml
├── smart-agent-ui/            # Vue 3 前端
└── README.md
```

## 快速开始

### 第一步：填写密钥和密码

**所有密钥都在 `.env` 这一个文件里配置：**

```bash
# 1. 复制模板
cp .env.example .env

# 2. 编辑 .env —— 至少填写：
#    LLM_API_KEY=sk-你的真实Key    ← 从 https://dashscope.console.aliyun.com/ 获取
#    DB_PASSWORD=你的数据库密码     ← MySQL root 密码

# 3. .env 已在 .gitignore 中 —— 永远不会被提交到 Git
```

> **DashScope API Key 在哪获取？** 访问 [阿里云灵积控制台](https://dashscope.console.aliyun.com/) → API-KEY 管理。新用户免费赠送数百万 token，无需充值。

### Docker Compose（推荐 — 一键启动）

```bash
# 构建并启动全部服务（MySQL、Redis、Milvus、Nacos、App）
docker compose up -d --build

# 访问：
#   前端:     http://localhost:5173   (先注册，再对话)
#   Swagger: http://localhost:8080/swagger-ui.html
```

### Windows

```cmd
REM 复制配置
copy .env.example .env
REM 用记事本编辑 .env —— 填入 LLM_API_KEY

REM 方式一：Docker Compose（推荐）
docker compose up -d --build

REM 方式二：Maven（需要本地 MySQL）
start.bat           REM local 环境
start.bat dev       REM dev 环境
```

### Linux / macOS

```bash
cp .env.example .env
# 编辑 .env 填入真实值

# 方式一：Docker Compose（推荐）
docker compose up -d --build

# 方式二：Maven
./start.sh              # 前台，local 环境
./start.sh -d -e dev    # 后台，dev 环境
```

### 环境依赖详情

依赖分为"必需"和"可选"两部分：

| 类型 | 组件 | 用途 | 缺失后的影响 |
|------|------|------|-------------|
| 必需 | JDK 21+ | 运行时 | 无法启动 |
| 必需 | Maven 3.9+ | 构建工具 | 无法构建 |
| 必需 | MySQL 8.x | 会话和消息持久化 | 无法启动 |
| 必需 | DashScope API Key | LLM 推理 | 无法对话 |
| 可选 | Node.js 18+ | 前端开发 | 无 Vue UI，API 仍可用 |
| 可选 | Nacos 2.x | 提示词热加载 + 系统配置（钉钉必需） | 无热加载，钉钉机器人不可用 |
| 可选 | Milvus 2.x | RAG 知识检索 | RAG 自动禁用，其他功能正常 |

> **最小配置**：JDK 21 + MySQL + DashScope API Key 即可开始对话。

### 1. 获取 DashScope API Key（最关键一步）

1. 访问 [阿里云灵积控制台](https://dashscope.console.aliyun.com/)
2. 用支付宝/淘宝账号登录并完成实名认证
3. 在"API-KEY 管理"中创建 API Key（格式：`sk-xxxxxxxxxx`）
4. **免费额度**：新用户赠送数百万 token — 开发测试用 `qwen-plus` 等模型无需充值

推荐模型：

| 模型 | 适用场景 |
|------|----------|
| `qwen-plus` / `qwen3-plus` | 主 Agent 推理（默认） |
| `qwen-turbo` | 子 Agent / 快速响应 |
| `text-embedding-v2` / `v3` | RAG 向量嵌入 |

### 2. 数据库初始化

```bash
mysql -u root -p < smart-agent-core/src/main/resources/schema.sql
```

这会创建 `smart_agent` 数据库，包含三张表：

| 表 | 用途 |
|----|------|
| `agent_session` | Agent 会话记忆存储 |
| `agent_chat_message` | 用户-Agent 对话消息 |
| `sys_user` | 用户认证信息 |

### 3. 配置环境变量

所有配置在 `.env` 中（从 `.env.example` 复制）：

```bash
cp .env.example .env
```

编辑 `.env` 填入真实值。应用通过 `${VAR:default}` 语法读取所有配置。

**基本对话最低配置**：

```bash
LLM_API_KEY=sk-你的真实Key    # 从 https://dashscope.console.aliyun.com/ 获取
DB_PASSWORD=你的MySQL密码
```

**Docker Compose** 自动读取 `.env`。  
**手动启动** Linux/macOS：`source .env && ./start.sh`  
**手动启动** Windows：设置系统环境变量或使用 IDE 运行配置。

### 4. 启动后端

```bash
cd smart-agent-framework
mvn clean install -DskipTests

# 方式一：启动脚本（推荐）
./start.sh                # 前台
./start.sh -d             # 后台（守护进程）
./start.sh -d -p 9090     # 后台 + 自定义端口
./start.sh -s             # 停止
./start.sh -r             # 重启

# 方式二：Maven 直接启动
cd smart-agent-start
mvn spring-boot:run
```

### 5. 验证启动

多种方式确认应用正常运行：

```bash
# 1. 检查进程和端口
lsof -i:8080

# 2. 访问 Swagger（最直观）
open http://localhost:8080/swagger-ui.html

# 3. 发送测试对话
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: change-this-api-key" \
  -d '{"userId":"test","sessionId":"s1","message":"你好"}'
```

返回 JSON 响应即为正常，LLM 调用日志可在 `~/smart-agent/logs/log_info.log` 查看。如失败见下方[常见问题](#常见问题)。

### 6. 启动前端（可选）

```bash
cd smart-agent-ui
npm install
npm run dev
```

访问 http://localhost:5173 使用对话界面。

## 多环境说明

| Profile | 配置文件 | 技能来源 | 认证 | CORS | Swagger |
|---------|----------|:---:|:---:|:---:|:---:|
| `local` | `application-local.yml` | 本地目录 | 关闭 | `*` | 开启 |
| `dev` | `application-dev.yml` | Git 仓库 | 关闭 | `*` | 开启 |
| `staging` | `application-staging.yml` | Git 仓库 | 开启 | 白名单 | 开启 |
| `prod` | `application-prod.yml` | Git 仓库 | 开启 | 白名单 | 关闭 |

切换：`SPRING_PROFILES_ACTIVE=dev` 或 `./start.sh -e dev`

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录（返回 JWT Token） |
| GET | `/api/auth/verify` | 验证 Token |
| POST | `/api/agent/chat` | 同步对话 |
| POST | `/api/agent/chat/stream` | 流式对话 (SSE) |
| GET | `/api/agent/history/{userId}?page=1&size=20` | 对话历史（分页） |
| GET | `/api/agent/conversation/{userId}/{sessionId}` | 会话详情 |
| POST | `/api/agent/feedback` | 赞踩反馈 |
| DELETE | `/api/agent/conversation/{userId}/{sessionId}` | 删除会话 |
| POST | `/api/rag/seed` | 初始化知识库样例 |
| GET | `/api/rag/search?query=xxx` | 检索知识库 |

> **注意**：所有对话接口均对 Agent 输出进行敏感信息脱敏（手机号、身份证号自动打码）。同一用户+会话的并发请求会被拒绝，返回"该会话正在处理中，请稍后再试"。

### 同步对话示例

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": "user001", "sessionId": "test001", "message": "你好"}'
```

### 流式对话示例

```bash
curl -N -X POST http://localhost:8080/api/agent/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"userId": "user001", "sessionId": "test001", "message": "你好"}'
```

### 赞踩反馈示例

```bash
curl -X POST http://localhost:8080/api/agent/feedback \
  -H "Content-Type: application/json" \
  -d '{"messageId": 1, "action": "like", "currentStatus": "none"}'
```

`action` 仅支持 `like` 和 `dislike`。再次提交相同操作会取消反馈。

## 安全与数据治理

### 敏感信息脱敏

框架内置 `SensitiveUtils`，自动对 API 输出中的敏感信息进行脱敏处理：

| 数据类型 | 脱敏规则 | 示例 |
|----------|----------|------|
| 手机号（11位） | 保留前3+后4，中间4位打码 | `13812345678` → `138****5678` |
| 身份证号（18位） | 保留前6+后4，中间8位打码 | `110101199001011234` → `110101**********1234` |

脱敏覆盖范围：同步对话、流式对话、历史列表、会话详情。

### 会话并发锁

同一用户+会话同时只允许一个请求，防止并发请求覆盖会话数据。

- **单实例**：内存级锁（`SessionLockService`），10 分钟超时自动释放
- **多实例**：需配置 Redis 分布式锁（SETNX + TTL）。配置 `redis.url` 自动激活 Redis 锁。

并发请求返回 HTTP 500 错误，消息为"该会话正在处理中，请稍后再试"。

### 数据自动清理

内置定时任务每日凌晨 3:00 清理过期数据：

| 数据类型 | 默认保留 | 配置项 |
|----------|:---:|------|
| Agent 会话记忆 (`agent_session`) | 7 天 | `cleanup.session.retention-days` |
| 聊天消息 (`agent_chat_message`) | 90 天 | `cleanup.message.retention-days` |

在 `application.yml` 中自定义：

```yaml
cleanup:
  session:
    retention-days: 7
  message:
    retention-days: 90
```

## 钉钉机器人集成

本地开发调试使用钉钉机器人 Stream 模式。

### 前置条件

以下内容全部**必需**：

1. **钉钉账号**：普通账号即可，无需企业认证
2. **Nacos 配置中心**：钉钉敏感配置（AppKey/AppSecret/RobotCode 等）**必须**通过 Nacos 管理
3. **AI 互动卡片模板**（推荐）：用于流式卡片回复；无模板仅支持纯文本回复

### 第一步：创建应用

1. 登录 [钉钉开放平台](https://open.dingtalk.com)
2. "应用开发 → 企业内部应用 → 创建应用"
3. 消息接收模式选 **Stream 模式**（无需公网回调地址）
4. 添加"机器人"能力
5. 在"凭证与基础信息"获取 **AppKey** 和 **AppSecret**
6. 在"机器人"配置页获取 **RobotCode**

### 第二步：开通权限

进入"权限管理"申请以下三项全部：

| 权限 | 编码 | 用途 |
|------|------|------|
| 企业内机器人发送消息 | `Robot.SendMessage` | 机器人发送消息 |
| 互动卡片实例写 | `Card.Instance.Write` | 创建 AI 卡片 |
| AI卡片流式更新 | `Card.Streaming.Write` | 流式更新卡片内容 |

> ⚠️ 缺少 `Card.Streaming.Write` 会导致卡片空白或返回 403。

### 第三步：创建卡片模板

1. 开放平台 → "互动卡片" → "创建模板"
2. 选择 **AI 卡片** 类型（非普通卡片）
3. 模板中必须有一个 Markdown 组件，key 设为 `content`，用于接收流式文本
4. 发布并复制模板 ID（如 `xxxx.schema`）

### 第四步：配置 Nacos

确保 Nacos 运行，在 `smart-agent` 分组下创建 `system-config.json`：

```json
{
  "dingtalk": {
    "appKey": "your-app-key",
    "appSecret": "your-app-secret",
    "robotCode": "your-robot-code",
    "aiCardTemplateId": "your-card-template-id"
  }
}
```

### 第五步：发布启用

1. "版本管理" → "创建新版本" → **发布**
2. `.env` 中设置 `DINGTALK_STREAM_ENABLED=true`
3. 重启应用
4. 在钉钉中搜索机器人名称，发起单聊，@机器人 发送消息

> 每次修改权限或模板后，必须**重新发布**应用才能生效。

## Nacos 配置中心（可选）

**使用场景**：提示词热加载、系统级敏感配置（如钉钉凭证）。**无 Nacos 时**，钉钉机器人不可用，但 HTTP API 和 Vue 前端不受影响。

### 安装启动 Nacos

```bash
wget https://github.com/alibaba/nacos/releases/download/2.4.3/nacos-server-2.4.3.zip
unzip nacos-server-2.4.3.zip
cd nacos/bin
sh startup.sh -m standalone
```

访问 http://localhost:8848/nacos（默认账号密码：nacos/nacos）

### 配置提示词热加载

| Data ID | Group | 内容 |
|---------|-------|------|
| `Supervisor-prompt` | `smart-agent` | Supervisor 系统提示词 |
| `DemoAgent-prompt` | `smart-agent` | DemoAgent 系统提示词 |

### 配置系统参数

Data ID：`system-config.json`，Group：`smart-agent`：

```json
{
  "dingtalk": {
    "appKey": "your-dingtalk-app-key",
    "appSecret": "your-dingtalk-app-secret",
    "robotCode": "your-robot-code",
    "aiCardTemplateId": "your-card-template-id"
  }
}
```

## RAG 知识库（可选）

**使用场景**：让 Agent 基于私有知识回答问题。**无 Milvus 时，应用正常运行** — RAG 自动禁用，其他功能不受影响。

### 启动 Milvus

```bash
wget https://raw.githubusercontent.com/milvus-io/milvus/master/scripts/standalone_embed.sh
bash standalone_embed.sh start
```

### 创建 Collection

```
Collection: knowledge_base
Fields:
  - id: VARCHAR(256), 主键
  - text: VARCHAR(65535)
  - vector: FLOAT_VECTOR(1024)
Index: IVF_FLAT on vector field
```

### 在 Agent 中使用

```java
@Autowired
private RagService ragService;

// 检索知识
String context = ragService.retrieve("用户问题");

// 索引文档
ragService.index("doc-001", "文档内容...");
```

## MCP 工具集成（可选）

框架支持通过 MCP 协议（HTTP/SSE 传输）集成外部工具服务器。由于大部分官方 MCP Server 使用 stdio 传输，需使用 **supergateway** 桥接到 SSE。

### 快速测试

1. 安装并启动 MCP 桥接：

```bash
npm install -g supergateway
npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-everything" --port 3000
```

2. 在 `application.yml` 中配置：

```yaml
mcp:
  server:
    url: http://localhost:3000/sse
    name: everything
```

3. 重启应用 — Agent 自动使用 MCP 工具。

### 推荐 MCP Server

| MCP Server | 说明 | 启动命令 |
|------------|------|----------|
| `@modelcontextprotocol/server-everything` | 测试服务器（echo、add 等） | `npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-everything" --port 3000` |
| `@modelcontextprotocol/server-fetch` | 网页内容抓取 | `npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-fetch" --port 3001` |
| `@modelcontextprotocol/server-filesystem` | 文件系统操作 | `npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-filesystem /tmp" --port 3002` |

> **注意**：MCP 为可选组件。无 MCP Server 时框架正常运行 — Agent 仅使用内置 @Tool 工具。

## 常见问题

| 问题 | 可能原因 | 解决方案 |
|------|----------|----------|
| Docker Desktop 无法启动 | **WSL 版本过旧**（Windows 常见） | **管理员** PowerShell 运行 `wsl --update`，重启 Docker Desktop |
| Docker 拉取超时 / 连接拒绝 | Docker Hub 被墙（国内常见） | Docker 设置 → Docker Engine → 添加 `"registry-mirrors": ["https://hub.rat.dev"]` |
| `Table 'smart_agent.xxx' doesn't exist` | 数据库表未创建 | 设置 `FLYWAY_ENABLED=true` 或手动执行 `schema.sql` |
| 前端显示"请求失败" | CORS 问题或数据库未初始化 | 确认 MySQL 表已创建；数据库初始化后重启 smart-agent |
| DashScope 连接失败 / 401 | API Key 未配置或无效 | 检查 `.env` 中 `LLM_API_KEY`；在 [DashScope 控制台](https://dashscope.console.aliyun.com/) 验证 Key |
| Nacos 连接失败警告 | Nacos 未运行或地址错误 | 不使用钉钉/热加载可忽略；否则启动 Nacos 并检查 `NACOS_SERVER_ADDR` |
| 钉钉机器人"已读不回" | 权限缺失或配置错误 | 1) 检查 `Card.Instance.Write` 和 `Card.Streaming.Write` 权限；2) 验证 Nacos 中 `system-config.json`；3) 权限变更后**重新发布**应用 |
| 钉钉卡片无流式效果、一次性出现 | 卡片模板或 `createAndDeliver` 时序 | 确保 AI 卡片模板有 `content` key 的 markdown 组件；卡片生命周期由模板自动状态机管理 |
| Milvus 连接警告 | Milvus 未运行 | 不使用 RAG 可忽略 |
| 8080 端口被占用 | 其他应用占用 | `./start.sh -d -p 9090` 或修改 `docker-compose.yml` 端口映射 |
| 前端"请求失败，请重试" | 后端异常（查看日志） | `docker compose logs smart-agent` 查看具体错误 |

## CORS 配置

默认允许所有来源，**仅供开发使用**。生产环境**必须**在 `application.yml` 中限制为特定域名：

```yaml
cors:
  allowed-origins: https://your-domain.com,https://admin.your-domain.com
```

> 启动时，框架会检查当前 profile：若非 `local` 环境且 `cors.allowed-origins` 仍为 `*`，会输出 **WARN** 日志提醒配置具体域名。

## Redis 增强（可选）

配置 Redis 后自动激活以下增强功能。**无 Redis 时，所有功能优雅降级为内存实现 — 应用仍正常启动。**

### 快速配置

1. 安装 Redis 7.x（或 6.x）：

```bash
# macOS
brew install redis && brew services start redis

# Docker
docker run -d --name redis -p 6379:6379 redis:7
```

2. 在 `.env` 中配置：

```bash
REDIS_URL=localhost
# REDIS_PASSWORD=your-password   # 如有密码取消注释
```

或在 `application.yml` 中：

```yaml
redis:
  url: localhost
  password: ""
```

### 激活的功能

| 功能 | 无 Redis | 有 Redis |
|------|:---:|:---:|
| **Session 锁** | 内存级，仅单实例 | SETNX + TTL，多实例互斥 |
| **Session 缓存** | 每次对话读 MySQL | Read-Through 缓存，活跃会话延迟降低约 10 倍 |
| **钉钉 Token 共享** | 各实例独立缓存，过期时并发刷新 | 全局共享 Token + 分布式刷新锁 |
| **限流** | 无限制 | 固定窗口计数器，默认 30 req/min/IP |
| **消息去重** | 钉钉重推可能重复回复 | SETNX 5 分钟内去重 |
| **清理任务锁** | 多实例并发 DELETE 可能锁竞争 | 分布式锁保证单实例执行 |

### 限流配置

```yaml
ratelimit:
  requests-per-minute: 30    # 每 IP 每分钟最大请求数
```

限流仅对 `/api/agent/chat` 和 `/api/agent/chat/stream` 接口生效。超过阈值返回 HTTP 429。

### 多实例部署

**强烈建议**多实例部署时配置 Redis。无 Redis 时：

- Session 锁无法跨实例协调，并发请求可能覆盖会话数据
- 定时清理任务在每个实例同时执行，竞争 MySQL 行锁
- 所有实例在 Token 过期时同时刷新，可能触发限流

## 扩展指南

### 添加自定义子 Agent

1. 实现 `SubAgent` 接口：

```java
@Component
public class MySubAgentProvider implements SubAgent {

    private final OpenAIChatModel model;

    public MySubAgentProvider(@Qualifier("subDefaultModel") OpenAIChatModel model) {
        this.model = model;
    }

    @Override
    public ReActAgent provide() {
        Toolkit toolkit = new Toolkit();
        // 注册自定义工具...
        return ReActAgent.builder()
                .name(getAgentName())
                .sysPrompt("你的 Agent 提示词")
                .model(model)
                .memory(new InMemoryMemory())
                .toolkit(toolkit)
                .maxIters(5)
                .build();
    }

    @Override
    public String getAgentName() { return "MyAgent"; }

    @Override
    public String getToolName() { return "my_tool"; }

    @Override
    public String getDescription() { return "描述你的 Agent 能力，用于 Supervisor 路由决策"; }

    @Override
    public Integer getMaxMessageLength() { return 20; }

    @Override
    public Duration getTimeWindow() { return Duration.ofMinutes(30); }
}
```

添加 `@Component` 注解即自动注册到 Supervisor — 无需其他代码改动。

### 添加自定义工具

使用 AgentScope 的 `@Tool` 注解：

```java
public class MyTool {
    @Tool(name = "search", description = "搜索信息")
    public String search(@ToolParam(name = "query", description = "搜索关键词") String query) {
        return "搜索结果: " + query;
    }
}
```

在 Agent 的 `provide()` 方法中注册：

```java
Toolkit toolkit = new Toolkit();
toolkit.registration().tool(new MyTool()).apply();
```

### 集成 MCP 外部工具

在 `application.yml` 中配置 MCP Server 地址：

```yaml
mcp:
  server:
    url: http://localhost:3000/sse
    name: my-mcp-server
```

在 Agent 中注入 `McpClientWrapper` 并注册到 Toolkit：

```java
@Autowired
private io.agentscope.core.tool.mcp.McpClientWrapper mcpClient;

toolkit.registration().mcpClient(mcpClient).apply();
```

### Skill 技能系统

技能定义维护在独立仓库：**[smart-agent-framework-skills](https://github.com/lijiangbo2023/smart-agent-framework-skills)**

**可用技能：**

| 技能 | Agent | 说明 |
|------|-------|------|
| `general-assistant` | DemoAgent | 天气、翻译、计算、搜索 |
| `code-review` | CodeAgent | 多语言代码审查 |
| `api-designer` | CodeAgent | RESTful API 设计 |
| `data-analyst` | 共享 | 数据分析与可视化 |

**Classpath 技能**：在 `src/main/resources/skills/` 下创建技能目录：

```
skills/
└── my-skill/
    ├── SKILL.md          # 必需，YAML frontmatter 格式
    └── references/       # 可选资源文件
```

`SKILL.md` 格式：

```markdown
---
name: my-skill
description: 技能描述
---

技能内容...
```

**Git 技能**：在 `.env` 中配置技能仓库：

```bash
SKILL_GIT_REPO_URL=https://github.com/YOUR_USER/smart-agent-framework-skills.git
SKILL_GIT_TOKEN=ghp_xxxxxxxxxxxx
SKILL_GIT_BRANCH=main
```

或编程方式使用 `GitSkillLoader`：

```java
@Autowired
private GitSkillLoader gitSkillLoader;

SkillBox skillBox = gitSkillLoader.loadSkillBox("MyAgent", toolkit);
```

**本地开发**：在 `.env` 中设置 `SKILL_LOCAL_DIR`，技能直接从本地目录加载，修改后立即生效，无需重启。

## 日志

### 日志目录

默认日志路径：`~/smart-agent/logs/`

```
~/smart-agent/logs/
├── log_info.log          # 主日志 (INFO+)
├── error.log             # 错误日志 (WARN+)
├── access.log            # HTTP 请求访问日志
├── startup.log           # 后台启动输出
├── info/                 # 历史 info 日志（按日滚动）
├── error/                # 历史 error 日志
└── access/               # 历史 access 日志
```

### 日志格式

- **应用日志**：`时间戳 [traceId] [线程] LEVEL 类名 - 消息`
- **错误日志**：`时间戳 [traceId] [线程] LEVEL [类.方法:行号] - 消息`
- **访问日志**：`METHOD URI STATUS 耗时_ms traceId`

### 请求追踪

每个 HTTP 请求自动生成 `traceId` 存入 MDC，支持全链路关联。外部 traceId 可通过 `X-Trace-Id` 请求头传入。

### 滚动策略

| 参数 | 值 |
|------|-----|
| 最大文件大小 | 50MB |
| 历史保留 | 7 天 |
| 总大小上限 | 20GB |

### 启动脚本

```bash
./start.sh              # 前台启动
./start.sh -d           # 后台（守护进程）
./start.sh -p 9090      # 自定义端口
./start.sh -e prod      # Spring profile
./start.sh -s           # 停止后台进程
./start.sh -r           # 重启
./start.sh -h           # 显示帮助
```

## License

MIT · Jiangbo Li · lijiangbo2023@163.com
