package com.smart.agent.component;

import com.smart.agent.constant.enums.MessageStatus;
import com.smart.agent.exception.SessionBusyException;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Agent chat component.
 *
 * @description Encapsulates the core logic for agent conversations, supporting both synchronous
 *              and streaming modes. Handles message persistence and session management.
 *              A semaphore limits concurrent synchronous chat executions to prevent Servlet
 *              thread pool exhaustion from long-running agent calls.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Component
public class AgentChatComponent {

    private final AgentChatMessageService agentChatMessageService;

    /**
     * Limits concurrent synchronous chat executions. Each synchronous chat blocks a Servlet
     * thread for up to 10 minutes (agent timeout), so without this guard, a burst of requests
     * could exhaust the Tomcat thread pool (default 200). Set to 0 to disable limiting.
     */
    private final Semaphore concurrentChatPermits;

    /**
     * Construct the agent chat component.
     *
     * @description Inject the chat message service dependency via constructor
     * @param agentChatMessageService chat message service for persisting user input and agent output
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public AgentChatComponent(AgentChatMessageService agentChatMessageService,
                              @Value("${chat.max-concurrent-sync:20}") int maxConcurrentSync) {
        this.agentChatMessageService = agentChatMessageService;
        this.concurrentChatPermits = maxConcurrentSync > 0 ? new Semaphore(maxConcurrentSync) : null;
    }

    /**
     * Synchronous chat.
     *
     * @description Send a user message to the agent and synchronously wait for a reply.
     *              Persists user input and agent output to the database.
     *              Returns fallback text on error.
     * @param agent ReAct agent instance
     * @param sessionManager session manager for saving session state after conversation completes
     * @param context chat context containing user ID, session ID, message content, etc.
     * @param errorFallbackText fallback reply text for error cases
     * @return chat result containing message ID and reply text
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public ChatResult chat(ReActAgent agent, SessionManager sessionManager,
                           ChatContext context, String errorFallbackText) {
        if (concurrentChatPermits != null && !concurrentChatPermits.tryAcquire()) {
            log.warn("Concurrent chat limit reached, rejecting request for userId={}", context.userId());
            throw new SessionBusyException("Server is busy, please try again later");
        }
        try {
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
                sessionManager.saveSession();
                agentChatMessageService.updateAgentOutput(messageId, null, MessageStatus.ERROR);
                return new ChatResult(messageId, errorFallbackText);
            }
        } finally {
            if (concurrentChatPermits != null) {
                concurrentChatPermits.release();
            }
        }
    }

    /**
     * Streaming chat.
     *
     * @description Send a user message to the agent and receive reply events as a stream.
     *              Supports real-time push of reasoning process and action execution.
     *              Automatically saves session and message records when the stream completes.
     * @param agent ReAct agent instance
     * @param sessionManager session manager for saving session state after stream completes
     * @param context chat context containing user ID, session ID, message content, etc.
     * @param textExtractor function that extracts text content from stream events
     * @return streaming chat result containing message ID, event stream, and latest text reference
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
                    log.info("Stream completed, userId={}, sessionId={}, elapsed={}ms", context.userId(), context.sessionId(), elapsed);
                    agentChatMessageService.updateAgentOutput(messageId, latestText.get(), MessageStatus.SUCCESS);
                })
                .doOnError(error -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.error("Stream error, userId={}, sessionId={}, elapsed={}ms", context.userId(), context.sessionId(), elapsed, error);
                    agentChatMessageService.updateAgentOutput(messageId, latestText.get(), MessageStatus.ERROR);
                })
                .doFinally(signal -> {
                    try {
                        sessionManager.saveSession();
                    } catch (Exception e) {
                        log.warn("Session save on stream {} failed, userId={}, sessionId={}",
                                signal, context.userId(), context.sessionId(), e);
                    }
                });

        return new ChatStreamResult(messageId, eventStream, latestText);
    }
}
