# Smart Agent Framework

基于 **Spring Boot 3.x + AgentScope + Vue 3** 的通用智能体开发框架。开箱即用的主子 Agent 协调、ReAct 推理、记忆持久化、RAG 知识检索、MCP 工具接入、动态配置等能力。

## 功能特性

| 功能 | 说明 |
|------|------|
| 主子 Agent 协调 | Supervisor 模式，数据驱动自动注册子 Agent |
| ReAct 推理 | AgentScope ReActAgent，支持 function calling |
| 记忆持久化 | SlidingWindowMemory + DatabaseSession，跨机器共享 |
| RAG 知识检索 | Milvus 向量数据库 + DashScope Embedding |
| MCP Client | 标准 MCP 协议（HTTP/SSE）接入外部工具 |
| Tool Call | AgentScope 原生工具调用 |
| 动态配置 | Nacos 热更新 Prompt 和系统配置 |
| Skill 系统 | Classpath + Git 仓库动态加载 |
| 钉钉机器人 | 可选，Stream 模式 + AI 流式卡片 |
| Vue 3 前端 | 历史会话 + 对话详情 + 工具调用/推理过程展示 |
| Swagger API | springdoc-openapi，开箱即用 |

## 技术栈

| 组件 | 方案 |
|------|------|
| 后端框架 | Spring Boot 3.3.6 + Java 21 |
| Agent 框架 | AgentScope 1.0.12 |
| 配置中心 | Nacos 2.4.x |
| 数据库 | MySQL 8.x + Druid |
| 向量数据库 | Milvus 2.x（可选） |
| LLM | DashScope OpenAI 兼容接口 |
| 前端 | Vue 3 + Element Plus |
| API 文档 | springdoc-openapi (Swagger UI) |

## 项目结构

```
smart-agent-framework/
├── pom.xml                    # 父 POM
├── start.sh                   # 启动/停止/重启脚本
├── smart-agent-core/          # 核心模块（Agent、Memory、Session、RAG、MCP、DingTalk...）
├── smart-agent-start/         # 启动模块（Controller、示例 Agent、配置文件、logback）
├── smart-agent-ui/            # Vue 3 前端
└── README.md
```

## 快速开始

### 1. 环境准备

- **JDK 21+**
- **Maven 3.9+**
- **MySQL 8.x**
- **Node.js 18+**（前端）
- **Nacos 2.x**（可选，不启动也能跑）
- **Milvus 2.x**（可选，RAG 功能需要）

### 2. 数据库初始化

```bash
mysql -u root -p < smart-agent-core/src/main/resources/schema.sql
```

这会创建 `smart_agent` 数据库和以下三张表：

| 表名 | 用途 |
|------|------|
| `agent_session` | Agent 会话记忆存储 |
| `agent_chat_message` | 用户与 Agent 的对话消息 |
| `agent_conversation_session_mapper` | 会话与 Session 的映射 |

### 3. 获取 DashScope API Key

1. 访问 [阿里云 DashScope](https://dashscope.console.aliyun.com/)
2. 开通服务并创建 API Key
3. 推荐模型：`qwen3.6-plus`（默认）、`qwen3.5-plus`（快速）、`qwen3.5-27b`（轻量）

### 4. 配置环境变量

项目通过环境变量管理敏感配置，参考 `.env.example` 文件：

```bash
# 方式一：设置环境变量（推荐）
export LLM_API_KEY=sk-your-dashscope-api-key
export DB_HOST=localhost
export DB_PORT=3306
export DB_USERNAME=root
export DB_PASSWORD=your-password

# 方式二：创建本地配置文件（已被 .gitignore 忽略）
cp .env.example .env
# 编辑 .env 填入真实值，然后 source .env
```

也可以直接编辑 `smart-agent-start/src/main/resources/application.yml`，或创建 `application-local.yml` 覆盖默认值。

### 5. 启动后端

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

启动成功后访问：
- Swagger UI: http://localhost:8080/swagger-ui.html
- API 文档: http://localhost:8080/v3/api-docs

### 6. 启动前端

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
| GET | `/api/agent/history/{userId}` | 获取用户历史对话列表 |
| GET | `/api/agent/conversation/{userId}/{sessionId}` | 获取指定会话的对话详情 |
| POST | `/api/agent/feedback` | 赞踩反馈 |

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

## MCP 工具接入测试

框架支持通过 MCP 协议（HTTP/SSE 传输）接入外部工具服务器。由于大多数官方 MCP 服务器使用 stdio 传输，需要通过 **supergateway** 桥接为 SSE。

### 快速测试

1. 安装并启动 MCP 桥接服务：

```bash
# 安装 supergateway（stdio → SSE 桥接）
npm install -g supergateway

# 启动 MCP 测试服务器（包含 echo、add、longRunningOperation 等工具）
npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-everything" --port 3000
```

2. 在 `application.yml` 中配置 MCP Server：

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

## Nacos 配置（可选）

### 安装启动 Nacos

```bash
# 下载 Nacos
wget https://github.com/alibaba/nacos/releases/download/2.4.3/nacos-server-2.4.3.zip
unzip nacos-server-2.4.3.zip
cd nacos/bin

# 单机模式启动
sh startup.sh -m standalone
```

访问 http://localhost:8848/nacos （默认账号密码：nacos/nacos）

### 配置 Prompt 热更新

在 Nacos 中创建配置：

| Data ID | Group | 内容 |
|---------|-------|------|
| `Supervisor-prompt` | `smart-agent` | Supervisor 的系统 Prompt |
| `DemoAgent-prompt` | `smart-agent` | Demo 子 Agent 的系统 Prompt |

### 配置系统参数

Data ID: `system-config.json`，Group: `smart-agent`：

```json
{
  "permissionUserIds": ["user001", "user002"],
  "allowTalkUserIds": ["user001", "user002"],
  "noPermissionText": "您暂无权限使用",
  "dingtalk": {
    "appKey": "your-dingtalk-app-key",
    "appSecret": "your-dingtalk-app-secret",
    "robotCode": "your-robot-code",
    "aiCardTemplateId": "your-card-template-id"
  }
}
```

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

### 使用 RAG 知识检索

1. 启动 Milvus（推荐 Docker）：

```bash
# 使用 Milvus Standalone
wget https://raw.githubusercontent.com/milvus-io/milvus/master/scripts/standalone_embed.sh
bash standalone_embed.sh start
```

2. 创建 Collection（使用 Milvus Attu UI 或 SDK）：

```
Collection: knowledge_base
Fields:
  - id: VARCHAR(256), primary key
  - text: VARCHAR(65535)
  - vector: FLOAT_VECTOR(1024)
Index: IVF_FLAT on vector field
```

3. 在你的 Agent 中注入 `RagService`：

```java
@Autowired
private RagService ragService;

// 检索知识
String context = ragService.retrieve("用户的问题");

// 索引文档
ragService.index("doc-001", "文档内容...");
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

## 钉钉机器人接入（可选）

1. 在[钉钉开放平台](https://open-dev.dingtalk.com/)创建企业内部应用
2. 启用机器人功能，选择 Stream 模式
3. 在 Nacos `system-config.json` 中配置 appKey、appSecret、robotCode
4. 如需 AI 流式卡片，需创建卡片模板并配置 aiCardTemplateId

## License

MIT
