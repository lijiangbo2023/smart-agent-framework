# Smart Agent Framework

[中文文档](README_ZH.md) | **English**

A general-purpose AI Agent development framework based on **Spring Boot 3.x + AgentScope + Vue 3**, supporting rapid construction of AI agents with conversation via **HTTP API** or **DingTalk Bot**.

## Features

| Feature | Description |
|---------|-------------|
| Supervisor/SubAgent Orchestration | Supervisor pattern with data-driven auto-registration of sub-agents |
| ReAct Reasoning | AgentScope ReActAgent with function calling support |
| Memory Persistence | SlidingWindowMemory + DatabaseSession, shared across instances |
| Session Concurrency Lock | Serializes requests per session to prevent data overwrites |
| Automatic Data Cleanup | Scheduled cleanup of sessions and messages with configurable retention |
| Sensitive Data Masking | Auto-masking of phone numbers and ID numbers across all API outputs |
| Redis Enhancement | Distributed lock, session cache, token sharing, rate limiting, message dedup (optional) |
| RAG Knowledge Retrieval | Milvus vector database + DashScope Embedding (optional) |
| MCP Client | Standard MCP protocol (HTTP/SSE) for external tool integration (optional) |
| Tool Call | AgentScope native tool calling |
| Dynamic Configuration | Nacos hot-reload for prompts and system config (optional) |
| Skill System | Classpath + Git repository dynamic loading |
| DingTalk Bot | Stream mode + AI streaming cards, single-chat only (optional) |
| Vue 3 Frontend | Conversation history + chat details + tool call/reasoning display |
| Swagger API | springdoc-openapi, ready out of the box |

## Tech Stack

| Component | Solution |
|-----------|----------|
| Backend Framework | Spring Boot 3.3.6 + Java 21 |
| Agent Framework | AgentScope 1.0.12 |
| Config Center | Nacos 2.4.x (optional) |
| Database | MySQL 8.x + Druid |
| Cache | Redis 7.x + Lettuce (optional) |
| Vector Database | Milvus 2.x (optional) |
| LLM | DashScope OpenAI-compatible API |
| Frontend | Vue 3 + Element Plus + DOMPurify |
| API Documentation | springdoc-openapi (Swagger UI) |

## Project Structure

```
smart-agent-framework/
├── pom.xml                    # Parent POM
├── start.sh                   # Start/stop/restart script
├── smart-agent-core/          # Core module
│   ├── agent/                 #   Supervisor, SubAgent, Memory, Session, SessionLock
│   ├── callback/chatbot/      #   DingTalk Stream callback handling
│   ├── component/             #   AgentChatComponent (chat orchestration)
│   ├── dingtalk/              #   DingTalk AccessToken, AI cards
│   ├── mcp/                   #   MCP Client configuration
│   ├── nacos/                 #   Prompt hot-reload, system config management
│   ├── persistence/           #   Entity, Mapper
│   ├── rag/                   #   Embedding, Milvus, RagService
│   ├── service/               #   Message service, conversation mapping service
│   └── util/                  #   JsonUtils, SensitiveUtils, SseEventHelper
├── smart-agent-start/         # Startup module
│   ├── controller/            #   REST API Controller
│   ├── demo/                  #   Example SubAgent and tools
│   └── resources/             #   application.yml, logback-spring.xml
├── smart-agent-ui/            # Vue 3 frontend
└── README.md
```

## Quick Start

### Prerequisites

Dependencies are divided into "required" and "optional":

| Type | Component | Purpose | Impact if Missing |
|------|-----------|---------|-------------------|
| Required | JDK 21+ | Runtime | Cannot start |
| Required | Maven 3.9+ | Build tool | Cannot build |
| Required | MySQL 8.x | Session & message persistence | Cannot start |
| Required | DashScope API Key | LLM inference | Cannot chat |
| Optional | Node.js 18+ | Frontend development | No Vue UI, but API still works |
| Optional | Nacos 2.x | Prompt hot-reload + system config (required for DingTalk) | No hot-reload, DingTalk bot disabled |
| Optional | Milvus 2.x | RAG knowledge retrieval | RAG auto-disabled, other features work |

> **Minimal setup**: Just JDK 21 + MySQL + DashScope API Key to get conversation working.

### 1. Get a DashScope API Key (Most Critical Step)

1. Visit [Alibaba Cloud DashScope Console](https://dashscope.console.aliyun.com/)
2. Log in with Alipay/Taobao account and complete verification
3. Create an API Key in "API-KEY Management" (format: `sk-xxxxxxxxxx`)
4. **Free credits**: New users receive millions of free tokens — no top-up needed for development with `qwen-plus` and other models

Recommended models:

| Model | Use Case |
|-------|----------|
| `qwen-plus` / `qwen3-plus` | Main Agent reasoning (default) |
| `qwen-turbo` | Sub-agents / fast response |
| `text-embedding-v2` / `v3` | RAG embedding |

### 2. Database Initialization

```bash
mysql -u root -p < smart-agent-core/src/main/resources/schema.sql
```

This creates the `smart_agent` database with three tables:

| Table | Purpose |
|-------|---------|
| `agent_session` | Agent session memory storage |
| `agent_chat_message` | User-Agent conversation messages |
| `agent_conversation_session_mapper` | Conversation-to-Session mapping (reserved, not yet active) |

### 3. Configure Environment Variables

Copy `.env.example` to `.env` and fill in real values, or `export` them directly.

Minimal `.env` example:

```bash
# ===== LLM (Required) =====
LLM_API_KEY=sk-your-dashscope-api-key
LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_MODEL=qwen-plus

# ===== Database (Required) =====
DB_HOST=localhost
DB_PORT=3306
DB_NAME=smart_agent
DB_USERNAME=root
DB_PASSWORD=your-password

# ===== DingTalk Bot (Optional, default false) =====
DINGTALK_STREAM_ENABLED=false

# ===== Nacos (Optional) =====
# NACOS_SERVER_ADDR=127.0.0.1:8848
# NACOS_NAMESPACE=public

# ===== Milvus (Optional) =====
# MILVUS_HOST=localhost
# MILVUS_PORT=19530

# ===== Redis (Optional) =====
# REDIS_URL=localhost
# REDIS_PASSWORD=
```

Load environment variables:

```bash
source .env
# Or inject directly in your IDE run configuration
```

### 4. Start the Backend

```bash
cd smart-agent-framework
mvn clean install -DskipTests

# Option 1: Start script (recommended)
./start.sh                # Foreground
./start.sh -d             # Background (daemon)
./start.sh -d -p 9090     # Background + custom port
./start.sh -s             # Stop
./start.sh -r             # Restart

# Option 2: Maven directly
cd smart-agent-start
mvn spring-boot:run
```

### 5. Verify Startup

Several ways to confirm the application is running correctly:

```bash
# 1. Check process and port
lsof -i:8080

# 2. Visit Swagger (most intuitive)
open http://localhost:8080/swagger-ui.html

# 3. Send a test conversation
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"userId":"test","sessionId":"s1","message":"hello"}'
```

A JSON response should be returned, and LLM call logs visible in `~/smart-agent/logs/log_info.log`. If it fails, see the [Troubleshooting](#troubleshooting) section below.

### 6. Start the Frontend (Optional)

```bash
cd smart-agent-ui
npm install
npm run dev
```

Visit http://localhost:5173 to use the chat interface.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/agent/chat` | Synchronous chat |
| POST | `/api/agent/chat/stream` | Streaming chat (SSE) |
| GET | `/api/agent/history/{userId}?page=1&size=20` | Get user conversation history (paginated, output masked) |
| GET | `/api/agent/conversation/{userId}/{sessionId}` | Get conversation details (output masked) |
| POST | `/api/agent/feedback` | Like/dislike feedback |

> **Note**: All chat endpoints apply sensitive data masking to Agent output (phone numbers and ID numbers auto-masked). Concurrent requests for the same user+session are rejected with "This session is being processed, please try again later."

### Synchronous Chat Example

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": "user001", "sessionId": "test001", "message": "hello"}'
```

### Streaming Chat Example

```bash
curl -N -X POST http://localhost:8080/api/agent/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"userId": "user001", "sessionId": "test001", "message": "hello"}'
```

### Like/Dislike Feedback Example

```bash
curl -X POST http://localhost:8080/api/agent/feedback \
  -H "Content-Type: application/json" \
  -d '{"messageId": 1, "action": "like", "currentStatus": "none"}'
```

`action` only supports `like` and `dislike`. Submitting the same action again cancels the feedback.

## Security & Data Governance

### Sensitive Data Masking

The framework includes `SensitiveUtils` which automatically masks sensitive data in all API outputs:

| Data Type | Masking Rule | Example |
|-----------|-------------|---------|
| Phone (11 digits) | Keep first 3 + last 4, mask middle 4 | `13812345678` → `138****5678` |
| ID Card (18 digits) | Keep first 6 + last 4, mask middle 8 | `110101199001011234` → `110101**********1234` |

Masking coverage: synchronous chat, streaming chat, history list, conversation details.

### Session Concurrency Lock

Only one request per user+session is allowed at a time, preventing concurrent requests from overwriting session data.

- **Single instance**: In-memory lock (`SessionLockService`), auto-released after 10-minute timeout
- **Multi-instance**: Requires distributed lock (Redis SETNX + TTL or MySQL row lock). Configure `redis.url` to auto-activate Redis-based lock.

Concurrent requests receive an HTTP 500 error with message "This session is being processed, please try again later."

### Automatic Data Cleanup

Built-in scheduled task runs daily at 3:00 AM to clean up expired data:

| Data Type | Default Retention | Config Key |
|-----------|:---:|------|
| Agent session memory (`agent_session`) | 7 days | `cleanup.session.retention-days` |
| Chat messages (`agent_chat_message`) | 90 days | `cleanup.message.retention-days` |

Customize in `application.yml`:

```yaml
cleanup:
  session:
    retention-days: 7
  message:
    retention-days: 90
```

## DingTalk Bot Integration

Develop and debug locally using DingTalk Bot Stream mode.

### Prerequisites

All of the following are **required**:

1. **DingTalk account**: Regular account, no enterprise verification needed
2. **Nacos config center**: DingTalk sensitive config (AppKey/AppSecret/RobotCode etc.) **must** be managed via Nacos
3. **AI Interactive Card template** (optional but recommended): For streaming card replies; without it, only plain text replies are available

### Step 1: Create DingTalk App & Bot

1. Log in to [DingTalk Open Platform](https://open.dingtalk.com)
2. Go to "App Development → Enterprise Internal App → Create App"
3. Add "Bot" capability to the app
4. Get **AppKey** and **AppSecret** from "Credentials & Basic Info"
5. Get **RobotCode** from the "Bot" configuration page

### Step 2: Create AI Card Template (Optional)

For streaming AI card replies (highly recommended for better UX):

1. DingTalk Open Platform → Your App → "Message Push → Interactive Cards"
2. Click "Create Card Template", select **AI Card** type
3. Configure card content area (supports Markdown rendering)
4. Publish template and copy the template ID (used as `aiCardTemplateId` in config)
5. If personal account hasn't enabled AI Card capability, submit an application

> Without an AI Card template, the bot still works but only sends non-streaming text replies.

### Step 3: Configure Sensitive Info in Nacos

Ensure Nacos is running, then create `system-config.json` in Group `smart-agent`:

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

### Step 4: Enable Stream Mode

```bash
export DINGTALK_STREAM_ENABLED=true
export NACOS_SERVER_ADDR=127.0.0.1:8848
```

Or in `application.yml`:

```yaml
dingtalk:
  stream:
    enabled: true
```

### Step 5: Start and Verify

After starting the application:

- You should see `DingTalk Stream connected` logs in `~/smart-agent/logs/log_info.log`
- Search for the bot name in DingTalk and start a private chat

### Current Limitations

- **Single-chat only** (private bot messages), group chat not yet implemented
- AI Cards require personal account capability activation; falls back to text replies when not available

### Debugging Tips

| Check Point | Method |
|-------------|--------|
| Stream connected? | Check startup logs for `DingTalk Stream` connection success messages |
| Config applied? | Visit Nacos console to verify `system-config.json` content |
| Bot online? | Check online status on DingTalk Open Platform "Bot" page |
| Messages arriving? | Send a message to the bot and check application logs for callback input |

## Nacos Configuration (Optional)

**Use cases**: Prompt hot-reload, system-level sensitive config (e.g. DingTalk credentials). **Without Nacos**, DingTalk bot is disabled, but HTTP API and Vue frontend are unaffected.

### Install & Start Nacos

```bash
wget https://github.com/alibaba/nacos/releases/download/2.4.3/nacos-server-2.4.3.zip
unzip nacos-server-2.4.3.zip
cd nacos/bin
sh startup.sh -m standalone
```

Visit http://localhost:8848/nacos (default credentials: nacos/nacos)

### Configure Prompt Hot-Reload

| Data ID | Group | Content |
|---------|-------|---------|
| `Supervisor-prompt` | `smart-agent` | Supervisor system prompt |
| `DemoAgent-prompt` | `smart-agent` | Demo sub-agent system prompt |

### Configure System Parameters

Data ID: `system-config.json`, Group: `smart-agent`:

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

## RAG Knowledge Base (Optional)

**Use case**: Let Agent answer questions based on private knowledge. **Without Milvus, the application still runs normally** — RAG is auto-disabled, other features are unaffected.

### Start Milvus

```bash
wget https://raw.githubusercontent.com/milvus-io/milvus/master/scripts/standalone_embed.sh
bash standalone_embed.sh start
```

### Create Collection

```
Collection: knowledge_base
Fields:
  - id: VARCHAR(256), primary key
  - text: VARCHAR(65535)
  - vector: FLOAT_VECTOR(1024)
Index: IVF_FLAT on vector field
```

### Use in Agent

```java
@Autowired
private RagService ragService;

// Retrieve knowledge
String context = ragService.retrieve("user question");

// Index document
ragService.index("doc-001", "document content...");
```

## MCP Tool Integration (Optional)

The framework supports external tool servers via MCP protocol (HTTP/SSE transport). Since most official MCP servers use stdio transport, you need **supergateway** to bridge to SSE.

### Quick Test

1. Install and start MCP bridge:

```bash
npm install -g supergateway
npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-everything" --port 3000
```

2. Configure in `application.yml`:

```yaml
mcp:
  server:
    url: http://localhost:3000/sse
    name: everything
```

3. Restart the application — Agent will automatically use MCP tools.

### Recommended MCP Servers

| MCP Server | Description | Start Command |
|------------|-------------|---------------|
| `@modelcontextprotocol/server-everything` | Test server (echo, add, etc.) | `npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-everything" --port 3000` |
| `@modelcontextprotocol/server-fetch` | Web content fetching | `npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-fetch" --port 3001` |
| `@modelcontextprotocol/server-filesystem` | File system operations | `npx -y supergateway --stdio "npx -y @modelcontextprotocol/server-filesystem /tmp" --port 3002` |

> **Note**: MCP is optional. Without an MCP Server, the framework runs normally — Agent only uses built-in @Tool tools.

## Troubleshooting

| Problem | Possible Cause | Solution |
|---------|---------------|----------|
| DashScope connection failure / 401 at startup | API Key not configured or invalid | Check `LLM_API_KEY` env var; verify key status in [DashScope Console](https://dashscope.console.aliyun.com/) |
| Nacos connection failure warning | Nacos not running or wrong address | Ignore if DingTalk/hot-reload not needed; otherwise start Nacos and check `NACOS_SERVER_ADDR` |
| DingTalk bot not responding | Stream not connected | 1) Confirm `DINGTALK_STREAM_ENABLED=true`; 2) Check `system-config.json` in Nacos; 3) Check Stream connection in startup logs |
| DingTalk only sends plain text, no streaming cards | `aiCardTemplateId` not configured or account lacks AI Card capability | Create AI Card template on DingTalk Open Platform; submit application if capability not enabled |
| Milvus connection failure warning | Milvus not running | Ignore if RAG not needed; otherwise start Milvus and check `MILVUS_HOST/MILVUS_PORT` |
| Database connection failure | MySQL not running or misconfigured | Check `DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD`; confirm `schema.sql` was executed |
| Swagger not accessible | App not fully started | Check `~/smart-agent/logs/log_info.log`, wait for `Started SmartAgentApplication` log |
| Port 8080 occupied | Another app using the port | Use `./start.sh -d -p 9090` to switch port |
| "Session is being processed" response | Concurrent requests for same session | Session lock protection; wait for previous request to complete. If persistent, check for unreleased locks (auto-expire after 10 minutes) |
| CORS `*` warning in non-local env | `cors.allowed-origins` not configured | Configure `cors.allowed-origins` with specific domains in `application.yml` for production |

## CORS Configuration

Allows all origins by default, **development use only**. Production environments **must** restrict to specific domains in `application.yml`:

```yaml
cors:
  allowed-origins: https://your-domain.com,https://admin.your-domain.com
```

> At startup, the framework checks the current profile: if not `local` and `cors.allowed-origins` is still `*`, a **WARN** log is emitted reminding you to configure specific domains.

## Redis Enhancement (Optional)

Configuring Redis auto-activates the following enhancements. **Without Redis, all features gracefully degrade to in-memory implementations — the application still starts normally.**

### Quick Setup

1. Install Redis 7.x (or 6.x):

```bash
# macOS
brew install redis && brew services start redis

# Docker
docker run -d --name redis -p 6379:6379 redis:7
```

2. Configure in `.env`:

```bash
REDIS_URL=localhost
# REDIS_PASSWORD=your-password   # Uncomment if password-protected
```

Or in `application.yml`:

```yaml
redis:
  url: localhost
  password: ""
```

### Activated Features

| Feature | Without Redis | With Redis |
|---------|:---:|:---:|
| **Session Lock** | In-memory, single-instance only | SETNX + TTL, multi-instance mutual exclusion |
| **Session Cache** | MySQL read/write per conversation | Read-Through cache, ~10x latency reduction for active sessions |
| **DingTalk Token Sharing** | Independent cache per instance, concurrent refresh on expiry | Global shared token + distributed refresh lock |
| **Rate Limiting** | No limit | Fixed-window counter, default 30 req/min/IP |
| **Message Dedup** | DingTalk redelivery may cause duplicate replies | SETNX dedup within 5 minutes |
| **Cleanup Task Lock** | Concurrent DELETE across instances may cause lock contention | Distributed lock ensures single-instance execution |

### Rate Limiting Configuration

```yaml
ratelimit:
  requests-per-minute: 30    # Max requests per IP per minute
```

Rate limiting only applies to `/api/agent/chat` and `/api/agent/chat/stream` endpoints. Exceeding the threshold returns HTTP 429.

### Multi-Instance Deployment

**Strongly recommended** to configure Redis for multi-instance deployments. Without it:

- Session locks cannot coordinate across instances; concurrent requests may overwrite session data
- Scheduled cleanup tasks run on every instance simultaneously, competing for MySQL row locks
- All instances refresh DingTalk tokens simultaneously on expiry, potentially triggering rate limits

## Extension Guide

### Add a Custom Sub-Agent

1. Implement the `AbstractSubAgent` interface:

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
        // Register custom tools...
        return ReActAgent.builder()
                .name(getAgentName())
                .sysPrompt("Your Agent Prompt")
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
    public String getDescription() { return "Describe your Agent's capabilities"; }

    @Override
    public Integer getMaxMessageLength() { return 20; }

    @Override
    public Duration getTimeWindow() { return Duration.ofMinutes(30); }
}
```

Add `@Component` and it auto-registers with the Supervisor — no other code changes needed.

### Add Custom Tools

Use AgentScope's `@Tool` annotation:

```java
public class MyTool {
    @Tool(name = "search", description = "Search for information")
    public String search(@ToolParam(name = "query", description = "Search keywords") String query) {
        return "Search results: " + query;
    }
}
```

Register in the Agent's `provide()` method:

```java
Toolkit toolkit = new Toolkit();
toolkit.registration().tool(new MyTool()).apply();
```

### Integrate MCP External Tools

Configure MCP Server address in `application.yml`:

```yaml
mcp:
  server:
    url: http://localhost:3000/sse
    name: my-mcp-server
```

Inject `McpClientWrapper` in the Agent and register to Toolkit:

```java
@Autowired
private io.agentscope.core.tool.mcp.McpClientWrapper mcpClient;

toolkit.registration().mcpClient(mcpClient).apply();
```

### Use the Skill System

**Classpath Skill**: Create a Skill directory under `src/main/resources/skills/`:

```
skills/
└── my-skill/
    ├── SKILL.md          # Required, with YAML frontmatter
    └── references/       # Optional resource files
```

`SKILL.md` format:

```markdown
---
name: my-skill
description: Skill description
---

Skill content...
```

**Git Skill**: After configuring a Git repository, use `GitSkillLoader`:

```java
@Autowired
private GitSkillLoader gitSkillLoader;

SkillBox skillBox = gitSkillLoader.loadSkillBox("MyAgent", toolkit);
```

## Logging

### Log Directory

Default log path: `~/smart-agent/logs/`

```
~/smart-agent/logs/
├── log_info.log          # Main log (INFO+)
├── error.log             # Error log (WARN+)
├── access.log            # HTTP request access log
├── startup.log           # Background startup output
├── info/                 # Historical info logs (date-based rolling)
├── error/                # Historical error logs
└── access/               # Historical access logs
```

### Log Format

- **Application log**: `timestamp [traceId] [thread] LEVEL className - message`
- **Error log**: `timestamp [traceId] [thread] LEVEL [class.method:line] - message`
- **Access log**: `METHOD URI STATUS elapsed_ms traceId`

### Request Tracing

Each HTTP request auto-generates a `traceId` in MDC for full-chain correlation. Supports external traceId via `X-Trace-Id` request header.

### Rolling Policy

| Parameter | Value |
|-----------|-------|
| Max file size | 50MB |
| History retention | 7 days |
| Total size cap | 20GB |

### Startup Script

```bash
./start.sh              # Foreground start
./start.sh -d           # Background (daemon)
./start.sh -p 9090      # Custom port
./start.sh -e prod      # Spring profile
./start.sh -s           # Stop background process
./start.sh -r           # Restart
./start.sh -h           # Show help
```

## License

MIT
