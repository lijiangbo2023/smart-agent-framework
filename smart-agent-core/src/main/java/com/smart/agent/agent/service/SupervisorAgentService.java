package com.smart.agent.agent.service;

import com.smart.agent.agent.provider.SubAgent;
import com.smart.agent.agent.session.DatabaseSession;
import com.smart.agent.agent.session.SessionLockService;
import com.smart.agent.component.AgentChatComponent;
import com.smart.agent.constant.enums.MessageChannel;
import com.smart.agent.constant.enums.MessageStatus;
import com.smart.agent.exception.SessionBusyException;
import com.smart.agent.model.ChatContext;
import com.smart.agent.model.ChatResult;
import com.smart.agent.model.ChatStreamResult;
import com.smart.agent.nacos.AgentPromptManager;
import com.smart.agent.persistence.mapper.AgentSessionMapper;
import com.smart.agent.service.AgentChatMessageService;
import com.smart.agent.util.SseEventHelper;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.session.SessionManager;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import io.agentscope.core.tool.subagent.SubAgentProvider;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Supervisor Agent Service
 *
 * @description Core Supervisor Agent service responsible for coordinating and orchestrating multiple sub-agents.
 *              Upon receiving a user message, it builds a Supervisor Agent with session context, dispatches
 *              the request to the appropriate sub-agent, and aggregates the result. Supports both synchronous
 *              and streaming conversation modes.
 *              When Redis is configured, session data (DatabaseSession) automatically leverages a Redis cache
 *              layer to reduce MySQL read pressure.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Service
public class SupervisorAgentService {

    private static final String DEFAULT_SUPERVISOR_PROMPT = """
            你是一个智能助手，覆盖多种能力。
            你有若干子 Agent 工具可调用，每个工具的能力和适用场景由其 description 给出。

            工作原则：
            - 工具描述匹配用户意图就调；能直接回答的就直接回答，不要为了"看起来像 agent"而强行调工具。
            - 调用子 Agent 时，message 必须原样转发用户输入，禁止自行添加、修改、补充任何信息。
            - 子 Agent 返回的内容就是最终答案，不要复述、缩写、改写。
            - 当用户发送"保存"、"是"、"确认"等简短确认消息时，如果上一轮调用了某个子 Agent，必须再次调用同一个子 Agent 并原样转发用户消息。

            输出规则：
            - 子 Agent 返回的 markdown 内容必须原样输出，禁止任何"再加工"
            - 不要把 markdown 表格转成段落或编号列表
            - 不要精简、总结、改写、缩写子 Agent 的返回内容
            - 仅允许在工具返回内容的前后加 1 句简短导语/收尾语

            始终使用中文回答。所有结论必须来自工具返回，不要编造。
            """;

    private static final String AGENT_NAME = "Supervisor";
    private static final int MAX_MESSAGES = 30;
    private static final Duration TIME_WINDOW = Duration.ofMinutes(30);
    private static final String ERROR_FALLBACK_TEXT = "Agent未返回有效回复";

    private final OpenAIChatModel supervisorModel;
    private final List<SubAgent> subAgents;
    private final AgentSessionMapper agentSessionMapper;
    private final AgentPromptManager agentPromptManager;
    private final AgentChatMessageService agentChatMessageService;
    private final AgentChatComponent agentChatComponent;
    private final SessionLockService sessionLockService;
    private final Set<String> subAgentToolNames;
    private final Map<String, String> subAgentNameToTool;

    /** Pre-computed immutable metadata for each sub-agent, avoiding per-request getter calls and iteration. */
    private final List<SubAgentMeta> subAgentMetas;

    /** Shared immutable execution config, reused across all requests. */
    private final ExecutionConfig toolExecutionConfig;

    /** Shared stateless hook instance, safe to reuse across all ReActAgent builds. */
    private final Hook subAgentBypassStopHook;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    public SupervisorAgentService(@Qualifier("fastModel") OpenAIChatModel supervisorModel,
                                  List<SubAgent> subAgents,
                                  AgentSessionMapper agentSessionMapper,
                                  AgentPromptManager agentPromptManager,
                                  AgentChatMessageService agentChatMessageService,
                                  AgentChatComponent agentChatComponent,
                                  SessionLockService sessionLockService) {
        this.supervisorModel = supervisorModel;
        this.subAgents = subAgents;
        this.agentSessionMapper = agentSessionMapper;
        this.agentPromptManager = agentPromptManager;
        this.agentChatMessageService = agentChatMessageService;
        this.agentChatComponent = agentChatComponent;
        this.sessionLockService = sessionLockService;
        this.agentPromptManager.register(AGENT_NAME, DEFAULT_SUPERVISOR_PROMPT);

        // Pre-compute sub-agent metadata: immutable across requests
        Set<String> tools = new HashSet<>();
        Map<String, String> nameToTool = new HashMap<>();
        List<SubAgentMeta> metas = new ArrayList<>();
        for (SubAgent agent : subAgents) {
            tools.add(agent.getToolName());
            nameToTool.put(agent.getAgentName(), agent.getToolName());
            @SuppressWarnings("unchecked")
            SubAgentProvider<ReActAgent> provider = (SubAgentProvider<ReActAgent>) agent;
            metas.add(new SubAgentMeta(
                    agent.getAgentName(), agent.getToolName(), agent.getDescription(),
                    agent.getMaxMessageLength(), agent.getTimeWindow(), provider));
        }
        this.subAgentToolNames = Set.copyOf(tools);
        this.subAgentNameToTool = Map.copyOf(nameToTool);
        this.subAgentMetas = List.copyOf(metas);

        // Pre-compute shared immutable objects
        this.toolExecutionConfig = ExecutionConfig.builder()
                .timeout(Duration.ofMinutes(10L))
                .maxAttempts(1)
                .build();
        this.subAgentBypassStopHook = new SubAgentBypassStopHook();

        log.info("SupervisorAgentService initialized with sub-agents: {}", this.subAgentNameToTool);
    }

    private SupervisorSessionContext buildSupervisorWithSession(String userId, String sessionId) {
        String compositeKey = DatabaseSession.buildSessionKey(userId, sessionId);

        DatabaseSession supervisorSession = new DatabaseSession(
                agentSessionMapper, AGENT_NAME, MAX_MESSAGES, TIME_WINDOW, null, redisTemplate);

        Toolkit supervisorToolkit = new Toolkit();

        // Use pre-computed metadata — no per-request getter calls or iteration overhead
        for (SubAgentMeta meta : subAgentMetas) {
            DatabaseSession session = new DatabaseSession(
                    agentSessionMapper, meta.agentName(),
                    meta.maxMessageLength(), meta.timeWindow(), compositeKey, redisTemplate);
            supervisorToolkit.registration()
                    .subAgent(new SessionBoundSubAgentProvider(meta.provider(), session, compositeKey),
                            SubAgentConfig.builder()
                                    .toolName(meta.toolName())
                                    .description(meta.description())
                                    .forwardEvents(true)
                                    .session(session)
                                    .build())
                    .apply();
        }

        String sysPrompt = agentPromptManager.getPrompt(AGENT_NAME);

        ReActAgent supervisor = ReActAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(sysPrompt)
                .model(supervisorModel)
                .memory(new InMemoryMemory())
                .toolkit(supervisorToolkit)
                .hook(subAgentBypassStopHook)
                .maxIters(10)
                .toolExecutionConfig(toolExecutionConfig)
                .build();

        SessionManager sessionManager = SessionManager.forSessionId(compositeKey)
                .withSession(supervisorSession)
                .addComponent(supervisor);

        sessionManager.loadIfExists();

        return new SupervisorSessionContext(supervisor, sessionManager);
    }

    /**
     * Session-bound sub-agent provider.
     *
     * @description Wraps the original SubAgentProvider and binds it to a per-request session.
     *              Unlike the old LazySubAgentProvider, this always returns a real agent —
     *              {@code provide()} is only called when the Supervisor actually routes to this
     *              sub-agent, so there is no waste from eager creation.
     */
    private static class SessionBoundSubAgentProvider implements SubAgentProvider<ReActAgent> {
        private final SubAgentProvider<ReActAgent> original;
        private final io.agentscope.core.session.Session session;
        private final String compositeKey;

        SessionBoundSubAgentProvider(SubAgentProvider<ReActAgent> original,
                                     io.agentscope.core.session.Session session,
                                     String compositeKey) {
            this.original = original;
            this.session = session;
            this.compositeKey = compositeKey;
        }

        @Override
        public ReActAgent provide() {
            ReActAgent agent = original.provide();
            agent.loadFrom(session, compositeKey);
            return agent;
        }
    }

    private class SubAgentBypassStopHook implements Hook {
        @Override
        @SuppressWarnings("unchecked")
        public <T extends HookEvent> Mono<T> onEvent(T event) {
            if (event instanceof PostActingEvent postActing) {
                String toolName = postActing.getToolUse().getName();
                if (toolName != null && subAgentToolNames.contains(toolName)) {
                    postActing.stopAgent();
                }
            }
            return Mono.just(event);
        }
    }

    /**
     * Synchronous conversation
     *
     * @description Builds the Supervisor Agent with its session context, executes a synchronous conversation,
     *              and returns the result. When only a single sub-agent is invoked, the sub-agent's raw result
     *              is passed through directly to avoid Supervisor rewriting.
     * @param context chat context containing userId, sessionId, message, channel, and other metadata
     * @return conversation result containing the message ID and reply text
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public ChatResult chat(ChatContext context) {
        String userId = context.userId();
        String sessionId = context.sessionId();
        String lockValue = sessionLockService.tryLock(userId, sessionId);
        if (lockValue == null) {
            throw new SessionBusyException("该会话正在处理中，请稍后再试");
        }
        try {
            log.info("Supervisor received query, userId={}, sessionId={}", userId, sessionId);
            long startTime = System.currentTimeMillis();
            SupervisorSessionContext ctx = buildSupervisorWithSession(userId, sessionId);

            ChatResult result = agentChatComponent.chat(ctx.supervisor(), ctx.sessionManager(), context, ERROR_FALLBACK_TEXT);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Supervisor completed, userId={}, sessionId={}, elapsed={}ms", userId, sessionId, elapsed);

            String bypassText = tryBypassSingleSubagentResult(ctx.supervisor());
            if (bypassText != null) {
                agentChatMessageService.updateAgentOutput(result.messageId(), bypassText, MessageStatus.SUCCESS);
                return new ChatResult(result.messageId(), bypassText);
            }
            return result;
        } finally {
            sessionLockService.unlock(userId, sessionId, lockValue);
        }
    }

    /**
     * Streaming conversation
     *
     * @description Builds the Supervisor Agent with its session context and executes a streaming conversation.
     *              A custom text extractor handles sub-agent streaming events to enable real-time output.
     * @param context chat context containing userId, sessionId, message, channel, and other metadata
     * @return streaming conversation result
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public ChatStreamResult chatStream(ChatContext context) {
        String userId = context.userId();
        String sessionId = context.sessionId();
        String lockValue = sessionLockService.tryLock(userId, sessionId);
        if (lockValue == null) {
            throw new SessionBusyException("该会话正在处理中，请稍后再试");
        }
        try {
            SupervisorSessionContext ctx = buildSupervisorWithSession(userId, sessionId);
            ChatStreamResult streamResult = agentChatComponent.chatStream(ctx.supervisor(), ctx.sessionManager(), context,
                    buildStreamTextExtractor(ctx.supervisor(), sessionId));

            Flux<Event> wrappedStream = streamResult.eventStream()
                    .doFinally(signal -> sessionLockService.unlock(userId, sessionId, lockValue));

            return new ChatStreamResult(streamResult.messageId(), wrappedStream, streamResult.latestTextRef());
        } catch (Exception e) {
            sessionLockService.unlock(userId, sessionId, lockValue);
            throw e;
        }
    }

    private Function<Event, String> buildStreamTextExtractor(ReActAgent supervisor, String sessionId) {
        Map<String, Long> subAgentStartTimes = new ConcurrentHashMap<>();
        return event -> {
            Msg msg = event.getMessage();
            if (msg != null) {
                trackSubAgentTiming(msg, sessionId, subAgentStartTimes);
            }
            if (event.getType() != EventType.AGENT_RESULT) {
                return null;
            }
            if (msg == null) {
                return null;
            }
            String bypassText = tryBypassSingleSubagentResult(supervisor);
            if (bypassText != null) {
                return bypassText;
            }
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof ToolResultBlock toolResult) {
                    String toolName = toolResult.getName();
                    if (toolName != null && subAgentToolNames.contains(toolName)) {
                        String text = extractToolResultText(toolResult);
                        if (text != null) {
                            return stripSessionIdPrefix(text);
                        }
                    }
                }
            }
            if (isFromSubAgent(msg)) {
                return null;
            }
            return extractDirectText(msg);
        };
    }

    private void trackSubAgentTiming(Msg msg, String sessionId, Map<String, Long> subAgentStartTimes) {
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ToolUseBlock toolUse) {
                String toolName = toolUse.getName();
                if (toolName != null && subAgentToolNames.contains(toolName)) {
                    subAgentStartTimes.putIfAbsent(toolName, System.currentTimeMillis());
                }
            } else if (block instanceof ToolResultBlock toolResult) {
                String toolName = toolResult.getName();
                if (toolName != null && subAgentToolNames.contains(toolName)) {
                    String resultText = extractToolResultText(toolResult);
                    if (resultText != null && resultText.startsWith("{\"type\":")) {
                        continue;
                    }
                    Long startTime = subAgentStartTimes.remove(toolName);
                    if (startTime != null) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("SubAgent.{} completed, sessionId={}, elapsed={}ms", toolName, sessionId, elapsed);
                    }
                }
            }
        }
    }

    private boolean isFromSubAgent(Msg msg) {
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ToolResultBlock toolResult) {
                Map<String, Object> metadata = toolResult.getMetadata();
                if (metadata != null && metadata.containsKey("subagent_name")) {
                    return true;
                }
                String toolName = toolResult.getName();
                if (toolName != null && subAgentToolNames.contains(toolName)) {
                    return true;
                }
            }
        }
        String name = msg.getName();
        return name != null && subAgentNameToTool.containsKey(name);
    }

    private String extractDirectText(Msg msg) {
        String direct = msg.getTextContent();
        if (direct == null || direct.isEmpty()) {
            return null;
        }
        return stripSessionIdPrefix(direct);
    }

    private String stripSessionIdPrefix(String text) {
        return SseEventHelper.stripSessionIdPrefix(text);
    }

    private String tryBypassSingleSubagentResult(ReActAgent supervisor) {
        List<Msg> messages = supervisor.getMemory().getMessages();
        int lastUserIdx = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getRole() == MsgRole.USER) {
                lastUserIdx = i;
                break;
            }
        }
        if (lastUserIdx < 0) {
            return null;
        }

        int toolUseCount = 0;
        String singleSubagentText = null;
        for (int i = lastUserIdx + 1; i < messages.size(); i++) {
            Msg m = messages.get(i);
            for (ContentBlock block : m.getContent()) {
                if (block instanceof ToolUseBlock) {
                    toolUseCount++;
                } else if (block instanceof ToolResultBlock toolResult) {
                    String toolName = toolResult.getName();
                    if (toolName != null && subAgentToolNames.contains(toolName)) {
                        String text = extractToolResultText(toolResult);
                        if (text != null) {
                            if (singleSubagentText != null) {
                                return null;
                            }
                            singleSubagentText = text;
                        }
                    }
                }
            }
        }

        if (toolUseCount == 1 && singleSubagentText != null) {
            return stripSessionIdPrefix(singleSubagentText);
        }
        return null;
    }

    private String extractToolResultText(ToolResultBlock block) {
        List<ContentBlock> output = block.getOutput();
        if (output == null || output.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : output) {
            if (b instanceof TextBlock textBlock) {
                sb.append(textBlock.getText());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private record SupervisorSessionContext(ReActAgent supervisor, SessionManager sessionManager) {
    }

    /**
     * Pre-computed immutable metadata for a sub-agent.
     *
     * @description Captures all static properties of a sub-agent at construction time,
     *              avoiding repeated getter calls and SubAgent iteration on each request.
     */
    private record SubAgentMeta(
            String agentName,
            String toolName,
            String description,
            Integer maxMessageLength,
            Duration timeWindow,
            SubAgentProvider<ReActAgent> provider
    ) {
    }
}
