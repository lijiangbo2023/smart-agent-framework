package com.smart.agent.component;

import com.smart.agent.constant.enums.MessageStatus;
import com.smart.agent.model.ChatContext;
import com.smart.agent.model.ChatResult;
import com.smart.agent.model.ChatStreamResult;
import com.smart.agent.service.AgentChatMessageService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Agent对话组件
 *
 * @description 封装Agent对话的核心逻辑，支持同步对话和流式对话两种模式，负责消息持久化和会话管理
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Component
public class AgentChatComponent {

    private final AgentChatMessageService agentChatMessageService;

    /**
     * 构造Agent对话组件
     *
     * @description 通过构造器注入对话消息服务依赖
     * @param agentChatMessageService 对话消息服务，用于持久化用户输入和Agent输出
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public AgentChatComponent(AgentChatMessageService agentChatMessageService) {
        this.agentChatMessageService = agentChatMessageService;
    }

    /**
     * 同步对话
     *
     * @description 向Agent发送用户消息并同步等待回复，保存用户输入和Agent输出到数据库，异常时返回兜底文本
     * @param agent ReAct Agent实例
     * @param sessionManager 会话管理器，用于对话完成后保存会话状态
     * @param context 对话上下文，包含用户ID、会话ID、消息内容等信息
     * @param errorFallbackText 异常情况下的兜底回复文本
     * @return 包含消息ID和回复文本的对话结果
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public ChatResult chat(ReActAgent agent, SessionManager sessionManager,
                           ChatContext context, String errorFallbackText) {
        log.info("Chat request, userId={}, query=[{}]", context.userId(), context.userMessage());
        Long messageId = agentChatMessageService.saveUserInput(
                context.sessionId(), context.userId(), context.userMessage(),
                context.channel(), context.businessName(),
                context.conversationId(), context.conversationType());
        try {
            Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(context.userMessage()).build();
            long startTime = System.currentTimeMillis();
            Msg response = agent.call(userMsg).block();
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Agent call completed, userId={}, sessionId={}, elapsed={}ms", context.userId(), context.sessionId(), elapsed);
            sessionManager.saveSession();

            String responseText = (response == null) ? errorFallbackText : response.getTextContent();
            agentChatMessageService.updateAgentOutput(messageId, responseText, MessageStatus.SUCCESS);
            return new ChatResult(messageId, responseText);
        } catch (Exception e) {
            log.error("Agent chat error, userId={}, sessionId={}", context.userId(), context.sessionId(), e);
            agentChatMessageService.updateAgentOutput(messageId, null, MessageStatus.ERROR);
            return new ChatResult(messageId, errorFallbackText);
        }
    }

    /**
     * 流式对话
     *
     * @description 向Agent发送用户消息并以流式方式接收回复事件，支持实时推送思考过程和执行动作，流结束后自动保存会话和消息记录
     * @param agent ReAct Agent实例
     * @param sessionManager 会话管理器，用于流完成后保存会话状态
     * @param context 对话上下文，包含用户ID、会话ID、消息内容等信息
     * @param textExtractor 事件文本提取函数，从流式事件中提取文本内容
     * @return 包含消息ID、事件流和最新文本引用的流式对话结果
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public ChatStreamResult chatStream(ReActAgent agent, SessionManager sessionManager,
                                       ChatContext context,
                                       Function<Event, String> textExtractor) {
        log.info("Stream chat request, userId={}, sessionId={}, query=[{}]", context.userId(), context.sessionId(), context.userMessage());
        Long messageId = agentChatMessageService.saveUserInput(
                context.sessionId(), context.userId(), context.userMessage(),
                context.channel(), context.businessName(),
                context.conversationId(), context.conversationType());
        AtomicReference<String> latestText = new AtomicReference<>("");

        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(context.userMessage()).build();
        StreamOptions streamOptions = StreamOptions.builder()
                .includeReasoningChunk(true)
                .includeActingChunk(true)
                .build();

        long startTime = System.currentTimeMillis();
        Flux<Event> eventStream = agent
                .stream(List.of(userMsg), streamOptions)
                .doOnNext(event -> {
                    String text = textExtractor.apply(event);
                    if (text != null && !text.isEmpty()) {
                        latestText.set(text);
                    }
                })
                .doOnComplete(() -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    sessionManager.saveSession();
                    log.info("Stream completed, userId={}, sessionId={}, elapsed={}ms", context.userId(), context.sessionId(), elapsed);
                    agentChatMessageService.updateAgentOutput(messageId, latestText.get(), MessageStatus.SUCCESS);
                })
                .doOnError(error -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.error("Stream error, userId={}, sessionId={}, elapsed={}ms", context.userId(), context.sessionId(), elapsed, error);
                    agentChatMessageService.updateAgentOutput(messageId, latestText.get(), MessageStatus.ERROR);
                });

        return new ChatStreamResult(messageId, eventStream, latestText);
    }
}
