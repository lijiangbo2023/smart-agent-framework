package com.smart.agent.agent.service;

import com.smart.agent.agent.provider.AbstractSubAgent;
import com.smart.agent.agent.session.DatabaseSession;
import com.smart.agent.component.AgentChatComponent;
import com.smart.agent.constant.enums.MessageChannel;
import com.smart.agent.constant.enums.MessageStatus;
import com.smart.agent.model.ChatContext;
import com.smart.agent.model.ChatResult;
import com.smart.agent.model.ChatStreamResult;
import com.smart.agent.nacos.AgentPromptManager;
import com.smart.agent.persistence.mapper.AgentSessionMapper;
import com.smart.agent.service.AgentChatMessageService;

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

import reactor.core.publisher.Mono;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Supervisor Agent服务
 *
 * @description 核心Supervisor Agent服务，负责协调和编排多个子Agent。接收用户消息后构建带会话上下文的Supervisor Agent，将请求分发至合适的子Agent处理，并汇总返回结果。支持同步和流式两种对话模式
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
    private static final String SESSION_ID_PREFIX_PATTERN = "session_id: [\\w-]+\\n\\n";
    private static final String ERROR_FALLBACK_TEXT = "Agent未返回有效回复";

    private final OpenAIChatModel supervisorModel;
    private final List<AbstractSubAgent> subAgents;
    private final AgentSessionMapper agentSessionMapper;
    private final AgentPromptManager agentPromptManager;
    private final AgentChatMessageService agentChatMessageService;
    private final AgentChatComponent agentChatComponent;
    private final Set<String> subAgentToolNames;
    private final Map<String, String> subAgentNameToTool;

    public SupervisorAgentService(@Qualifier("fastModel") OpenAIChatModel supervisorModel,
                                  List<AbstractSubAgent> subAgents,
                                  AgentSessionMapper agentSessionMapper,
                                  AgentPromptManager agentPromptManager,
                                  AgentChatMessageService agentChatMessageService,
                                  AgentChatComponent agentChatComponent) {
        this.supervisorModel = supervisorModel;
        this.subAgents = subAgents;
        this.agentSessionMapper = agentSessionMapper;
        this.agentPromptManager = agentPromptManager;
        this.agentChatMessageService = agentChatMessageService;
        this.agentChatComponent = agentChatComponent;
        this.agentPromptManager.register(AGENT_NAME, DEFAULT_SUPERVISOR_PROMPT);

        Set<String> tools = new HashSet<>();
        Map<String, String> nameToTool = new HashMap<>();
        for (AbstractSubAgent agent : subAgents) {
            tools.add(agent.getToolName());
            nameToTool.put(agent.getAgentName(), agent.getToolName());
        }
        this.subAgentToolNames = Set.copyOf(tools);
        this.subAgentNameToTool = Map.copyOf(nameToTool);
        log.info("SupervisorAgentService initialized with sub-agents: {}", this.subAgentNameToTool);
    }

    private SupervisorSessionContext buildSupervisorWithSession(String userId, String sessionId) {
        String compositeKey = DatabaseSession.buildSessionKey(userId, sessionId);

        DatabaseSession supervisorSession = new DatabaseSession(agentSessionMapper, AGENT_NAME, MAX_MESSAGES, TIME_WINDOW);

        Toolkit supervisorToolkit = new Toolkit();

        for (AbstractSubAgent agent : subAgents) {
            DatabaseSession session = new DatabaseSession(
                    agentSessionMapper, agent.getAgentName(),
                    agent.getMaxMessageLength(), agent.getTimeWindow(), compositeKey);
            @SuppressWarnings("unchecked")
            SubAgentProvider<ReActAgent> original = (SubAgentProvider<ReActAgent>) agent;
            supervisorToolkit.registration()
                    .subAgent(new LazySubAgentProvider(original, session, compositeKey, agent.getAgentName()),
                            SubAgentConfig.builder()
                                    .toolName(agent.getToolName())
                                    .description(agent.getDescription())
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
                .hook(new SubAgentBypassStopHook())
                .maxIters(10)
                .toolExecutionConfig(ExecutionConfig.builder()
                        .timeout(Duration.ofMinutes(10L))
                        .maxAttempts(1)
                        .build())
                .build();

        SessionManager sessionManager = SessionManager.forSessionId(compositeKey)
                .withSession(supervisorSession)
                .addComponent(supervisor);

        sessionManager.loadIfExists();

        return new SupervisorSessionContext(supervisor, sessionManager);
    }

    private static class LazySubAgentProvider implements SubAgentProvider<ReActAgent> {
        private final SubAgentProvider<ReActAgent> original;
        private final io.agentscope.core.session.Session session;
        private final String compositeKey;
        private final String agentName;
        private final AtomicBoolean firstCall = new AtomicBoolean(true);

        LazySubAgentProvider(SubAgentProvider<ReActAgent> original, io.agentscope.core.session.Session session,
                             String compositeKey, String agentName) {
            this.original = original;
            this.session = session;
            this.compositeKey = compositeKey;
            this.agentName = agentName;
        }

        @Override
        public ReActAgent provide() {
            if (firstCall.getAndSet(false)) {
                return ReActAgent.builder()
                        .name(agentName)
                        .memory(new InMemoryMemory())
                        .toolkit(new Toolkit())
                        .build();
            }
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
     * 同步对话
     *
     * @description 构建Supervisor Agent及其会话上下文，执行同步对话并返回结果。当仅有单个子Agent被调用时，直接透传子Agent的原始结果以避免Supervisor改写
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param userMessage 用户消息文本
     * @param channel 消息渠道
     * @param businessName 业务名称
     * @param conversationId 会话ID
     * @param conversationType 会话类型
     * @return 对话结果，包含消息ID和回复文本
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public ChatResult chat(String userId, String sessionId, String userMessage,
                           MessageChannel channel, String businessName,
                           String conversationId, String conversationType) {
        log.info("Supervisor received query, userId={}, sessionId={}", userId, sessionId);
        long startTime = System.currentTimeMillis();
        SupervisorSessionContext ctx = buildSupervisorWithSession(userId, sessionId);
        ChatContext chatContext = new ChatContext(sessionId, userId, userMessage, channel, businessName, conversationId, conversationType);

        ChatResult result = agentChatComponent.chat(ctx.supervisor(), ctx.sessionManager(), chatContext, ERROR_FALLBACK_TEXT);
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Supervisor completed, userId={}, sessionId={}, elapsed={}ms", userId, sessionId, elapsed);

        String bypassText = tryBypassSingleSubagentResult(ctx.supervisor());
        if (bypassText != null) {
            agentChatMessageService.updateAgentOutput(result.messageId(), bypassText, MessageStatus.SUCCESS);
            return new ChatResult(result.messageId(), bypassText);
        }
        return result;
    }

    /**
     * 流式对话
     *
     * @description 构建Supervisor Agent及其会话上下文，执行流式对话。通过自定义文本提取器处理子Agent的流式事件，实现实时输出
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param userMessage 用户消息文本
     * @param channel 消息渠道
     * @param businessName 业务名称
     * @param conversationId 会话ID
     * @param conversationType 会话类型
     * @return 流式对话结果
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public ChatStreamResult chatStream(String userId, String sessionId, String userMessage,
                                       MessageChannel channel, String businessName,
                                       String conversationId, String conversationType) {
        SupervisorSessionContext ctx = buildSupervisorWithSession(userId, sessionId);
        ChatContext chatContext = new ChatContext(sessionId, userId, userMessage, channel, businessName, conversationId, conversationType);
        return agentChatComponent.chatStream(ctx.supervisor(), ctx.sessionManager(), chatContext,
                buildStreamTextExtractor(ctx.supervisor(), sessionId));
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
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replaceFirst(SESSION_ID_PREFIX_PATTERN, "");
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
}
