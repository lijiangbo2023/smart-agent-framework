# Smart Agent Framework

**中文** | [English](README.md)

基于 **Spring Boot 3.x + AgentScope + Vue 3** 的通用智能体开发框架，支持快速搭建 AI 智能体，并通过 **HTTP API** 或 **钉钉机器人** 进行对话。

## 功能特性

| 功能 | 说明 |
|------|------|
| 主子 Agent 协调 | Supervisor 模式，数据驱动自动注册子 Agent |
| ReAct 推理 | AgentScope ReActAgent，支持 function calling |
| 记忆持久化 | SlidingWindowMemory + DatabaseSession，跨机器共享 |
| 会话并发锁 | 同一会话串行处理，防止并发请求导致数据覆盖 |
| 数据自动清理 | Session 和消息记录定期清理，保留天数可配置 |
| 敏感数据脱敏 | 手机号、身份证号自动掩码，覆盖对话和历史接口 |
| Redis 增强 | 分布式锁、Session 缓存、Token 共享、API 限流、消息去重（可选） |
| RAG 知识检索 | Milvus 向量数据库 + DashScope Embedding（可选） |
| MCP Client | 标准 MCP 协议（HTTP/SSE）接入外部工具（可选） |
| Tool Call | AgentScope 原生工具调用 |
| 动态配置 | Nacos 热更新 Prompt 和系统配置（可选） |
| Skill 系统 | Classpath + Git 仓库动态加载 |
| 钉钉机器人 | Stream 模式 + AI 流式卡片，仅支持单聊（可选） |
| Vue 3 前端 | 历史会话 + 对话详情 + 工具调用/推理过程展示 |
| Swagger API | springdoc-openapi，开箱即用 |

## 技术栈

| 组件 | 方案 |
|------|------|
| 后端框架 | Spring Boot 3.3.6 + Java 21 |
| Agent 框架 | AgentScope 1.0.12 |
| 配置中心 | Nacos 2.4.x（可选） |
| 数据库 | MySQL 8.x + Druid |
| 缓存 | Redis 7.x + Lettuce（可选） |
| 向量数据库 | Milvus 2.x（可选） |
| LLM | DashScope OpenAI 兼容接口 |
| 前端 | Vue 3 + Element Plus + DOMPurify |
| API 文档 | springdoc-openapi (Swagger UI) |

## 项目结构

```
smart-agent-framework/
├── pom.xml                    # 父 POM
├── start.sh                   # 启动/停止/重启脚本
├── smart-agent-core/          # 核心模块
│   ├── agent/                 #   Supervisor、SubAgent、Memory、Session、SessionLock
│   ├── callback/chatbot/      #   钉钉 Stream 回调处理
│   ├── component/             #   AgentChatComponent（对话编排）
│   ├── dingtalk/              #   钉钉 AccessToken、AI 卡片
│   ├── mcp/                   #   MCP Client 配置
│   ├── nacos/                 #   Prompt 热更新、系统配置管理
│   ├── persistence/           #   Entity、Mapper
│   ├── rag/                   #   Embedding、Milvus、RagService
│   ├── service/               #   消息服务、会话映射服务
│   └── util/                  #   JsonUtils、SensitiveUtils、SseEventHelper
├── smart-agent-start/         # 启动模块
│   ├── controller/            #   REST API Controller
│   ├── demo/                  #   示例 SubAgent 和工具
│   └── resources/             #   application.yml、logback-spring.xml
├── smart-agent-ui/            # Vue 3 前端
└── README.md
```

## 快速开始

### 依赖说明

依赖被划分为「最小依赖」和「可选依赖」：

| 类型 | 组件 | 用途 | 不安装的影响 |
|------|------|------|------|
| 最小依赖 | JDK 21+ | 运行环境 | 无法启动 |
| 最小依赖 | Maven 3.9+ | 构建工具 | 无法构建 |
| 最小依赖 | MySQL 8.x | 会话与对话持久化 | 无法启动 |
| 最小依赖 | DashScope API Key | LLM 推理服务 | 无法对话 |
| 可选 | Node.js 18+ | 前端开发与构建 | 无 Vue 前端，但 API 仍可用 |
| 可选 | Nacos 2.x | Prompt 热更新 + 系统配置（钉钉必需） | 无热更新，无法启用钉钉机器人 |
| 可选 | Milvus 2.x | RAG 知识检索 | RAG 功能自动禁用，其他功能正常 |

> **最小起步**：只需 JDK 21 + MySQL + DashScope API Key 即可跑通对话能力。

### 1. 获取 DashScope API Key（最关键的一步）

1. 访问 [阿里云 DashScope 控制台](https://dashscope.console.aliyun.com/)
2. 用支付宝/淘宝账号登录，完成实名认证
3. 在「API-KEY 管理」中创建 API Key（形如 `sk-xxxxxxxxxx`）
4. **新用户免费额度**：阿里云会赠送百万 token 级别的免费额度，开发测试阶段无需充值即可使用 `qwen-plus` 等模型

可用的推荐模型：

| 模型 | 适用场景 |
|------|------|
| `qwen-plus` / `qwen3-plus` | 主 Agent 推理（默认） |
| `qwen-turbo` | 子 Agent / 快速响应 |
| `text-embedding-v2` / `v3` | RAG embedding |

### 2. 数据库初始化

```bash
mysql -u root -p < smart-agent-core/src/main/resources/schema.sql
```

会创建 `smart_agent` 数据库及以下三张表：

| 表名 | 用途 |
|------|------|
| `agent_session` | Agent 会话记忆存储 |
| `agent_chat_message` | 用户与 Agent 的对话消息 |
| `agent_conversation_session_mapper` | 会话与 Session 的映射（预留，当前未启用） |

### 3. 配置环境变量

复制 `.env.example` 为 `.env` 并填入真实值，或直接 `export` 到环境变量。

最小化 `.env` 配置示例：

```bash
# ===== LLM（必填）=====
LLM_API_KEY=sk-your-dashscope-api-key
LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_MODEL=qwen-plus

# ===== 数据库（必填）=====
DB_HOST=localhost
DB_PORT=3306
DB_NAME=smart_agent
DB_USERNAME=root
DB_PASSWORD=your-password

# ===== 钉钉机器人（可选，默认 false）=====
DINGTALK_STREAM_ENABLED=false

# ===== Nacos（可选）=====
# NACOS_SERVER_ADDR=127.0.0.1:8848
# NACOS_NAMESPACE=public

# ===== Milvus（可选）=====
# MILVUS_HOST=localhost
# MILVUS_PORT=19530
```

加载环境变量：

```bash
source .env
# 或者使用 IDE 启动配置直接注入
```

### 4. 启动后端

```bash
cd smart-agent-framework
mvn clean install -DskipTests

# 方式一：启动脚本（推荐）
./start.sh                # 前台启动
./start.sh -d             # 后台启动
./start.sh -d -p 9090     # 后台 + 自定义端口
./start.sh -s             # 停止
./start.sh -r             # 重启

# 方式二：Maven 直接启动
cd smart-agent-start
mvn spring-boot:run
```

### 5. 启动验证

确认应用正常运行的几种方式：

```bash
# 1. 检查进程与端口
lsof -i:8080

# 2. 访问 Swagger（最直观）
open http://localhost:8080/swagger-ui.html

# 3. 发起一次对话验证
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"userId":"test","sessionId":"s1","message":"你好"}'
```

正常情况下会返回 JSON 应答，并在 `~/smart-agent/logs/log_info.log` 中看到 LLM 调用日志。如果返回失败，请跳到下方[故障排查](#-故障排查)章节。

### 6. 启动前端（可选）

```bash
cd smart-agent-ui
npm install
npm run dev
```

访问 http://localhost:5173 即可使用对话界面。

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/agent/chat` | 同步对话 |
| POST | `/api/agent/chat/stream` | 流式对话（SSE） |
| GET | `/api/agent/history/{userId}?page=1&size=20` | 获取用户历史对话列表（分页，输出已脱敏） |
| GET | `/api/agent/conversation/{userId}/{sessionId}` | 获取指定会话的对话详情（输出已脱敏） |
| POST | `/api/agent/feedback` | 赞踩反馈 |

> **注意**：所有对话接口返回的 Agent 输出均经过敏感数据脱敏处理（手机号、身份证号自动掩码）。同一用户+会话的并发请求会被拒绝，返回「该会话正在处理中，请稍后再试」。

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

`action` 仅支持 `like` 和 `dislike`，重复提交相同 action 会取消反馈。

## 安全与数据治理

### 敏感数据脱敏

框架内置 `SensitiveUtils`，对所有 API 输出的 Agent 回复自动执行敏感数据掩码：

| 数据类型 | 脱敏规则 | 示例 |
|----------|----------|------|
| 手机号（11 位） | 保留前 3 后 4，中间 4 位掩码 | `13812345678` → `138****5678` |
| 身份证号（18 位） | 保留前 6 后 4，中间 8 位掩码 | `110101199001011234` → `110101**********1234` |

脱敏覆盖范围：同步对话、流式对话、历史对话列表、对话详情。

### 会话并发锁

同一用户 + 会话同时只允许一个请求处理，防止并发请求导致 Session 数据互相覆盖。

- **单实例部署**：使用内存锁（`SessionLockService`），锁超时 10 分钟自动释放
- **多实例部署**：需替换为分布式锁实现（如 Redis SETNX + TTL 或 MySQL 行锁），实现相同接口并标注 `@Primary` 即可

并发请求会收到 HTTP 500 错误，message 为「该会话正在处理中，请稍后再试」。

### 数据自动清理

框架内置定时任务，每天凌晨 3:00 自动清理过期数据：

| 数据类型 | 默认保留天数 | 配置项 |
|----------|:---:|------|
| Agent 会话记忆（`agent_session`） | 7 天 | `cleanup.session.retention-days` |
| 聊天消息（`agent_chat_message`） | 90 天 | `cleanup.message.retention-days` |

在 `application.yml` 中自定义：

```yaml
cleanup:
  session:
    retention-days: 7
  message:
    retention-days: 90
```

## 钉钉机器人接入

通过钉钉机器人 Stream 模式，在本地即可开发和调试。

### 前置条件

接入钉钉机器人需要同时满足以下条件，**缺一不可**：

1. **钉钉账号**：普通钉钉账号即可，无需企业认证
2. **Nacos 配置中心**：钉钉相关敏感配置（AppKey/AppSecret/RobotCode 等）**必须**通过 Nacos 管理
3. **AI 互动卡片模板**（可选但推荐）：用于流式卡片回复，不配置则只能使用纯文本回复

### 步骤一：创建钉钉应用与机器人

1. 登录 [钉钉开放平台](https://open.dingtalk.com)
2. 进入「应用开发 → 企业内部应用 → 创建应用」
3. 在应用中添加「机器人」能力
4. 在「凭证与基础信息」中获取 **AppKey** 和 **AppSecret**
5. 在「机器人」配置页中获取 **RobotCode**

### 步骤二：创建 AI 卡片模板（可选）

如果希望使用流式 AI 卡片回复（强烈推荐，体验更好）：

1. 登录钉钉开放平台 → 进入你的应用 → 左侧「消息推送 → 互动卡片」
2. 点击「创建卡片模板」，类型选择 **AI 卡片**
3. 配置卡片内容区域（支持 Markdown 格式渲染）
4. 发布模板，复制模板 ID（即配置中的 `aiCardTemplateId`）
5. 如果个人账户暂未开通 AI 卡片能力，可在开放平台提交申请开通

> 不配置 AI 卡片模板时，机器人仍可工作，但只能发送非流式文本回复。

### 步骤三：在 Nacos 中配置敏感信息

确保 Nacos 已启动，并在 Group `smart-agent` 中创建配置 `system-config.json`：

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

### 步骤四：启用 Stream 模式

```bash
export DINGTALK_STREAM_ENABLED=true
export NACOS_SERVER_ADDR=127.0.0.1:8848
```

或在 `application.yml` 中：

```yaml
dingtalk:
  stream:
    enabled: true
```

### 步骤五：启动应用并验证

启动应用后：

- 在 `~/smart-agent/logs/log_info.log` 中应能看到 `DingTalk Stream connected` 类的日志
- 在钉钉中搜索机器人名称，发起单聊即可开始对话

### 当前限制

- **仅支持单聊**（私聊机器人），群聊功能暂未实现
- AI 卡片需个人账户开通对应能力，未开通时降级为文本回复

### 调试建议

| 排查点 | 方法 |
|------|------|
| Stream 是否连接 | 查看启动日志中是否有 `DingTalk Stream` 相关连接成功信息 |
| 配置是否生效 | 访问 Nacos 控制台确认 `system-config.json` 内容正确 |
| 机器人是否在线 | 在钉钉开放平台「机器人」页面查看在线状态 |
| 消息是否到达 | 给机器人发消息后，查看应用日志中是否有回调入参 |

## Nacos 配置（可选）

**使用场景**：Prompt 热更新、系统级敏感配置（如钉钉信息）。**不使用**则无法启用钉钉机器人，但 HTTP API 与 Vue 前端不受影响。

### 安装启动 Nacos

```bash
wget https://github.com/alibaba/nacos/releases/download/2.4.3/nacos-server-2.4.3.zip
unzip nacos-server-2.4.3.zip
cd nacos/bin
sh startup.sh -m standalone
```

访问 http://localhost:8848/nacos （默认账号密码：nacos/nacos）

### 配置 Prompt 热更新

| Data ID | Group | 内容 |
|---------|-------|------|
| `Supervisor-prompt` | `smart-agent` | Supervisor 的系统 Prompt |
| `DemoAgent-prompt` | `smart-agent` | Demo 子 Agent 的系统 Prompt |

### 配置系统参数

Data ID: `system-config.json`，Group: `smart-agent`：

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

**使用场景**：让 Agent 基于私有知识回答问题。**不配置 Milvus 应用仍可正常运行**，RAG 功能将自动禁用，其他能力不受影响。

### 启动 Milvus

```bash
wget https://raw.githubusercontent.com/milvus-io/milvus/master/scripts/standalone_embed.sh
bash standalone_embed.sh start
```

### 创建 Collection

```
Collection: knowledge_base
Fields:
  - id: VARCHAR(256), primary key
  - text: VARCHAR(65535)
  - vector: FLOAT_VECTOR(1024)
Index: IVF_FLAT on vector field
```

### 在 Agent 中使用

```java
@Autowired
private RagService ragService;

// 检索知识
String context = ragService.retrieve("用户的问题");

// 索引文档
ragService.index("doc-001", "文档内容...");
```

## MCP 工具接入（可选）

框架支持通过 MCP 协议（HTTP/SSE 传输）接入外部工具服务器。由于大多数官方 MCP 服务器使用 stdio 传输，需要通过 **supergateway** 桥接为 SSE。

### 快速测试

1. 安装并启动 MCP 桥接服务：

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

3. 重启应用后，Agent 即可自动使用 MCP 工具。

### 推荐的 MCP 服务器

| MCP 服务器 | 说明 | 启动命令 |
|------------|------|----------|
| `@modelcontextprotocol/server-everything` | 测试服务器（echo, add 等） | `npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-everything" --port 3000` |
| `@modelcontextprotocol/server-fetch` | 网页内容抓取 | `npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-fetch" --port 3001` |
| `@modelcontextprotocol/server-filesystem` | 文件系统操作 | `npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-filesystem /tmp" --port 3002` |

> **注意**：MCP 功能为可选特性，不配置 MCP Server 时框架仍可正常运行，Agent 只使用内置的 @Tool 工具。

## 故障排查

| 问题 | 可能原因 | 解决方案 |
|------|------|---------|
| 启动报 DashScope 连接失败 / 401 | API Key 未配置或无效 | 检查 `LLM_API_KEY` 环境变量是否正确设置；确认 Key 在 [DashScope 控制台](https://dashscope.console.aliyun.com/) 中状态正常 |
| Nacos 连接失败警告 | Nacos 未启动或地址错误 | 如不需要钉钉/热更新功能，可忽略此警告；否则启动 Nacos 并检查 `NACOS_SERVER_ADDR` |
| 钉钉机器人无响应 | Stream 未连接 | 1) 确认 `DINGTALK_STREAM_ENABLED=true`；2) 检查 Nacos 中 `system-config.json` 是否正确；3) 查看启动日志中 Stream 连接状态 |
| 钉钉只能收到普通文本，无流式卡片 | `aiCardTemplateId` 未配置或账户未开通 AI 卡片能力 | 在钉钉开放平台创建 AI 卡片模板并填入；如未开通能力请提交申请 |
| Milvus 连接失败警告 | Milvus 未启动 | 如不需要 RAG，忽略此警告；否则启动 Milvus 并检查 `MILVUS_HOST/MILVUS_PORT` |
| 数据库连接失败 | MySQL 未启动或配置错误 | 检查 `DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD`；确认 `schema.sql` 已执行 |
| Swagger 打不开 | 应用未完全启动 | 查看 `~/smart-agent/logs/log_info.log`，等待 `Started SmartAgentApplication` 日志出现 |
| 端口 8080 被占用 | 其他应用占用端口 | 使用 `./start.sh -d -p 9090` 切换端口 |
| 返回「该会话正在处理中」 | 同一会话有并发请求 | 会话锁保护机制，等待前一个请求完成后重试；如持续出现，检查是否有请求异常未释放锁（锁 10 分钟自动过期） |
| 非 local 环境 CORS `*` 警告 | 未配置 `cors.allowed-origins` | 生产环境务必在 `application.yml` 中配置 `cors.allowed-origins` 为具体域名 |

## CORS 配置

默认允许所有来源，**仅适用于开发环境**。生产环境**必须**在 `application.yml` 中限制为具体域名：

```yaml
cors:
  allowed-origins: https://your-domain.com,https://admin.your-domain.com
```

> 框架启动时会检测当前 profile：如果不是 `local` 且 `cors.allowed-origins` 仍为 `*`，会在日志中输出 **WARN** 级别告警，提醒配置具体域名。

## Redis 增强（可选）

配置 Redis 后自动激活以下增强功能，**不配置时所有功能自动降级为内存实现，应用仍可正常启动**。

### 快速启用

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
# REDIS_PASSWORD=your-password   # 有密码时取消注释
```

或在 `application.yml` 中：

```yaml
redis:
  url: localhost
  password: ""
```

### 激活的增强功能

| 功能 | 无 Redis 时 | 有 Redis 时 |
|------|-------------|-------------|
| **会话并发锁** | 内存锁，仅单实例有效 | Redis SETNX + TTL，多实例互斥 |
| **Session 缓存** | 每次对话读写 MySQL | Read-Through 缓存，活跃会话延迟降低 ~10 倍 |
| **钉钉 Token 共享** | 各实例独立缓存，过期时并发刷新 | 全局共享 Token + 分布式刷新锁 |
| **API 限流** | 无限制 | 滑动窗口计数器，默认 30 次/分钟/IP |
| **消息去重** | 钉钉重复投递可能重复回复 | Redis SETNX 5 分钟内去重 |
| **清理任务锁** | 多实例同时 DELETE 可能锁竞争 | 分布式锁保证只有一个实例执行 |

### API 限流配置

```yaml
ratelimit:
  requests-per-minute: 30    # 每个 IP 每分钟最大请求数
```

限流仅针对 `/api/agent/chat` 和 `/api/agent/chat/stream` 端点。超过阈值返回 HTTP 429。

### 多实例部署

多实例部署时**强烈建议配置 Redis**，否则：

- 会话锁无法跨实例互斥，并发请求可能导致 Session 数据覆盖
- 定时清理任务会在每个实例同时执行，竞争 MySQL 行锁
- 钉钉 Token 过期瞬间所有实例同时刷新，可能触发限流

## 扩展指南

### 添加自定义子 Agent

1. 实现 `AbstractSubAgent` 接口：

```java
@Component
public class MySubAgentProvider implements AbstractSubAgent {

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
                .sysPrompt("你的 Agent Prompt")
                .model(model)
                .memory(new InMemoryMemory())
                .toolkit(toolkit)
                .build();
    }

    @Override
    public String getAgentName() { return "MyAgent"; }

    @Override
    public String getToolName() { return "my_tool"; }

    @Override
    public String getDescription() { return "描述你的 Agent 能力"; }

    @Override
    public Integer getMaxMessageLength() { return 20; }

    @Override
    public Duration getTimeWindow() { return Duration.ofMinutes(30); }
}
```

加上 `@Component` 注解即可自动注册到 Supervisor，无需修改任何其他代码。

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

### 接入 MCP 外部工具

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

### 使用 Skill 系统

**Classpath Skill**：在 `src/main/resources/skills/` 下创建 Skill 目录：

```
skills/
└── my-skill/
    ├── SKILL.md          # 必须，含 YAML frontmatter
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

**Git Skill**：配置 Git 仓库地址后，使用 `GitSkillLoader`：

```java
@Autowired
private GitSkillLoader gitSkillLoader;

SkillBox skillBox = gitSkillLoader.loadSkillBox("MyAgent", toolkit);
```

## 日志配置

### 日志目录

默认日志路径：`~/smart-agent/logs/`

```
~/smart-agent/logs/
├── log_info.log          # 主日志（INFO+）
├── error.log             # 错误日志（WARN+）
├── access.log            # HTTP 请求访问日志
├── startup.log           # 后台启动输出
├── info/                 # 历史 info 日志（按日期滚动）
├── error/                # 历史 error 日志
└── access/               # 历史 access 日志
```

### 日志格式

- **应用日志**：`时间 [traceId] [线程] 级别 类名 - 消息`
- **错误日志**：`时间 [traceId] [线程] 级别 [类.方法:行号] - 消息`
- **访问日志**：`METHOD URI STATUS 耗时ms traceId`

### 请求追踪

每个 HTTP 请求自动生成 `traceId` 放入 MDC，日志中可串联同一请求的完整链路。支持通过 `X-Trace-Id` 请求头传入外部 traceId。

### 滚动策略

| 参数 | 值 |
|------|------|
| 单文件大小上限 | 50MB |
| 历史保留天数 | 7 天 |
| 总大小上限 | 20GB |

### 启动脚本

```bash
./start.sh              # 前台启动
./start.sh -d           # 后台启动 (daemon)
./start.sh -p 9090      # 自定义端口
./start.sh -e prod      # 指定 Spring profile
./start.sh -s           # 停止后台进程
./start.sh -r           # 重启
./start.sh -h           # 查看帮助
```

## License

MIT
