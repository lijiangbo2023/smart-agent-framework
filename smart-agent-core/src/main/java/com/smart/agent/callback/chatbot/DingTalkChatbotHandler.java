package com.smart.agent.callback.chatbot;

import com.alibaba.fastjson2.JSONObject;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import com.smart.agent.agent.service.SupervisorAgentService;
import com.smart.agent.constant.enums.MessageChannel;
import com.smart.agent.dingtalk.DingTalkAiCardService;
import com.smart.agent.model.CardDeliveryResult;
import com.smart.agent.model.ChatContext;
import com.smart.agent.model.ChatStreamResult;
import com.smart.agent.util.NamedThreadFactory;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * DingTalk single-chat message handler.
 *
 * @description Receives DingTalk Stream-mode bot @-mention message callbacks, asynchronously
 *              triggers Agent conversations, and projects streaming replies via
 *              DingTalkReplyAdapter as DingTalk AI interactive cards.
 * @author Jiangbo Li
 * @date 2026-06-13
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DingTalkChatbotHandler implements OpenDingTalkCallbackListener<ChatbotMessage, JSONObject> {

    /**
     * Single-chat conversation type constant, corresponding to DingTalk conversationType=1.
     */
    public static final String CONVERSATION_TYPE_SINGLE = DingTalkAiCardService.CONVERSATION_TYPE_SINGLE;

    /**
     * Default business name, used for the business_name field in message persistence.
     */
    private static final String DEFAULT_BUSINESS_NAME = "default";

    /**
     * Message deduplication Redis key prefix.
     */
    private static final String MSG_DEDUP_KEY_PREFIX = "msg:dedup:";

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    /**
     * Async processing thread pool: prevents blocking the DingTalk Stream listener thread.
     */
    private final ExecutorService chatExecutor = new ThreadPoolExecutor(
            4, 16, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(256),
            new NamedThreadFactory("dingtalk-chatbot-handler"),
            new ThreadPoolExecutor.CallerRunsPolicy());

    private final SupervisorAgentService supervisorAgentService;
    private final DingTalkAiCardService aiCardService;
    private final DingTalkReplyAdapter replyAdapter;

    /**
     * Handle DingTalk chatbot single-chat message callback.
     *
     * @description Extracts sender, message content, and conversation info from ChatbotMessage.
     *              When Redis is configured, deduplicates by message ID (SETNX with 5-min TTL)
     *              to prevent duplicate replies caused by DingTalk Stream re-delivery.
     *              The conversation is dispatched asynchronously to SupervisorAgentService,
     *              and the user receives streaming replies via AI cards.
     *              Returns an empty object immediately to acknowledge receipt without blocking
     *              the Stream listener thread.
     * @param message the message object received by the DingTalk chatbot
     * @return DingTalk Stream callback ACK response (empty object; DingTalk only checks for
     *         the absence of exceptions to consider it successful)
     * @author Jiangbo Li
     * @date 2026-06-13
     */
    @Override
    public JSONObject execute(ChatbotMessage message) {
        try {
            if (message == null) {
                log.warn("Received null ChatbotMessage");
                return new JSONObject();
            }

            String senderStaffId = message.getSenderStaffId();
            String conversationId = message.getConversationId();
            String conversationType = message.getConversationType();
            String userText = extractUserText(message);

            log.info("DingTalk chatbot message received, senderStaffId={}, conversationType={}, conversationId={}, text=[{}]",
                    senderStaffId, conversationType, conversationId, userText);

            // Message deduplication: prevent duplicate replies from DingTalk Stream re-delivery
            String msgId = message.getMsgId();
            if (redisTemplate != null && msgId != null && !msgId.isEmpty()) {
                try {
                    String dedupKey = MSG_DEDUP_KEY_PREFIX + msgId;
                    Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", 5, TimeUnit.MINUTES);
                    if (!Boolean.TRUE.equals(isNew)) {
                        log.info("Duplicate message ignored, msgId={}", msgId);
                        return new JSONObject();
                    }
                } catch (Exception e) {
                    log.warn("Message dedup check failed, proceed: {}", e.getMessage());
                }
            }

            if (senderStaffId == null || senderStaffId.isEmpty()) {
                log.warn("Missing senderStaffId, ignore message");
                return new JSONObject();
            }
            if (userText == null || userText.isEmpty()) {
                log.info("Empty user text, ignore message, senderStaffId={}", senderStaffId);
                return new JSONObject();
            }

            // Only handle single-chat; group chat is handled by other handlers (current scope)
            if (!CONVERSATION_TYPE_SINGLE.equals(conversationType)) {
                log.info("Non-single-chat message, skip in this handler, conversationType={}", conversationType);
                return new JSONObject();
            }

            // Asynchronously trigger Agent conversation to avoid blocking the Stream thread
            final String finalText = userText;
            chatExecutor.execute(() -> dispatchChat(senderStaffId, conversationId, conversationType, finalText));
        } catch (Exception e) {
            log.error("DingTalkChatbotHandler execute error", e);
        }
        return new JSONObject();
    }

    private void dispatchChat(String userId, String conversationId, String conversationType, String userText) {
        try {
            // Use conversationId as sessionId to ensure multi-turn context continuity within the same chat
            String sessionId = conversationId;
            ChatContext context = new ChatContext(sessionId, userId, userText,
                    MessageChannel.DINGTALK, DEFAULT_BUSINESS_NAME,
                    conversationId, conversationType);
            ChatStreamResult streamResult = supervisorAgentService.chatStream(context);

            // Map event stream to a "latest accumulated text" stream; latestTextRef is updated in doOnNext by AgentChatComponent
            Flux<String> textStream = streamResult.eventStream()
                    .map(event -> {
                        String t = streamResult.latestTextRef().get();
                        return t == null ? "" : t;
                    })
                    .filter(s -> !s.isEmpty())
                    .distinctUntilChanged();

            replyAdapter.sendStreamReply(userId, conversationId, conversationType, textStream);
        } catch (Exception e) {
            log.error("dispatchChat error, userId={}, conversationId={}", userId, conversationId, e);
            sendErrorCard(userId, conversationId, conversationType, e.getMessage());
        }
    }

    private void sendErrorCard(String userId, String conversationId, String conversationType, String errorMsg) {
        try {
            CardDeliveryResult delivery = aiCardService.createAndDeliver(
                    conversationType, conversationId, userId, Map.of());
            if (delivery != null) {
                // Do not expose raw exception messages to users — they may contain internal details
                String text = "处理失败，请稍后重试";
                aiCardService.streamingUpdate(delivery.outTrackId(), text, true);
            }
        } catch (Exception e) {
            log.error("sendErrorCard failed, userId={}", userId, e);
        }
    }

    /**
     * Extract plain text from the message (trimming whitespace after the @bot prefix).
     *
     * @description DingTalk text-type message content is stored in text.content, which includes
     *              the user's actual input after the @bot mention. This method trims leading
     *              and trailing whitespace to remove the common space after @.
     */
    private String extractUserText(ChatbotMessage message) {
        MessageContent text = message.getText();
        if (text == null) {
            return null;
        }
        String content = text.getContent();
        if (content == null) {
            return null;
        }
        return content.trim();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down DingTalkChatbotHandler executor");
        chatExecutor.shutdown();
        try {
            if (!chatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                chatExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            chatExecutor.shutdownNow();
        }
    }
}
