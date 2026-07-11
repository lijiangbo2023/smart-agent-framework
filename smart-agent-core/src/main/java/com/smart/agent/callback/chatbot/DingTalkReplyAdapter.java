package com.smart.agent.callback.chatbot;

import com.smart.agent.dingtalk.DingTalkAiCardService;
import com.smart.agent.model.CardDeliveryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DingTalk AI card streaming reply adapter.
 *
 * @description Projects the Agent's streaming text output as a DingTalk AI interactive card.
 *              Creates and delivers the card on the first token, updates card content in a
 *              throttled manner for subsequent tokens, and performs finalization on stream
 *              completion or error.
 * @author Jiangbo Li
 * @date 2026-06-13
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DingTalkReplyAdapter {

    /**
     * Streaming update throttle interval (ms) to avoid DingTalk Open API rate-limiting
     * caused by high-frequency calls.
     */
    private static final long STREAM_UPDATE_THROTTLE_MILLIS = 500L;

    /**
     * Fallback text displayed when the stream produces no content.
     */
    private static final String EMPTY_RESPONSE_TEXT = "Agent did not return a valid reply";

    /**
     * Error fallback text prefix.
     */
    private static final String ERROR_TEXT_PREFIX = "Processing failed: ";

    private final DingTalkAiCardService aiCardService;

    /**
     * Project a streaming response to a DingTalk AI card.
     *
     * @description Subscribes to the response text stream. On receiving the first non-empty text,
     *              creates and delivers an AI card. Subsequent updates are pushed at the throttled
     *              interval. On stream completion, a final update is sent. On error, an error
     *              message is displayed.
     * @param userId sender user ID
     * @param conversationId DingTalk conversation ID (used for group chat)
     * @param conversationType conversation type: "1" for single chat, "2" for group chat
     * @param textStream accumulated latest full-text stream; each element is the complete content
     * @return processQueryKey from the card delivery result, used to correlate the message with
     *         the card; returns a null reference if no card has been created yet
     * @author Jiangbo Li
     * @date 2026-06-13
     */
    public AtomicReference<String> sendStreamReply(String userId, String conversationId, String conversationType,
                                                    Flux<String> textStream) {
        StreamReplyContext ctx = new StreamReplyContext(userId, conversationId, conversationType);

        textStream
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        text -> handleNextText(ctx, text),
                        error -> handleError(ctx, error),
                        () -> handleComplete(ctx)
                );

        return ctx.processQueryKeyRef;
    }

    private void handleNextText(StreamReplyContext ctx, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ctx.latestText.set(text);

        // Create and deliver the card on first content received
        if (ctx.cardCreated.compareAndSet(false, true)) {
            CardDeliveryResult delivery = aiCardService.createAndDeliver(
                    ctx.conversationType, ctx.conversationId, ctx.userId, Map.of());
            if (delivery == null) {
                log.warn("createAndDeliver returned null, userId={}, conversationId={}", ctx.userId, ctx.conversationId);
                return;
            }
            ctx.outTrackId = delivery.outTrackId();
            ctx.processQueryKeyRef.set(delivery.processQueryKey());
            log.info("AI card delivered, userId={}, outTrackId={}, processQueryKey={}",
                    ctx.userId, ctx.outTrackId, delivery.processQueryKey());
            // Push the first screen content immediately after delivery
            pushStreamingUpdate(ctx, text, false);
            return;
        }

        if (ctx.outTrackId == null) {
            return;
        }

        // Throttle: only push when the interval since last update exceeds the threshold
        long now = System.currentTimeMillis();
        long lastTs = ctx.lastUpdateTs.get();
        if (now - lastTs < STREAM_UPDATE_THROTTLE_MILLIS) {
            return;
        }
        if (!ctx.lastUpdateTs.compareAndSet(lastTs, now)) {
            return;
        }
        pushStreamingUpdate(ctx, text, false);
    }

    private void handleComplete(StreamReplyContext ctx) {
        if (ctx.outTrackId == null) {
            // Empty stream: send a fallback card notification
            if (ctx.cardCreated.compareAndSet(false, true)) {
                CardDeliveryResult delivery = aiCardService.createAndDeliver(
                        ctx.conversationType, ctx.conversationId, ctx.userId, Map.of());
                if (delivery != null) {
                    ctx.outTrackId = delivery.outTrackId();
                    ctx.processQueryKeyRef.set(delivery.processQueryKey());
                    pushStreamingUpdate(ctx, EMPTY_RESPONSE_TEXT, true);
                }
            }
            return;
        }
        String finalText = ctx.latestText.get();
        if (finalText == null || finalText.isEmpty()) {
            finalText = EMPTY_RESPONSE_TEXT;
        }
        pushStreamingUpdate(ctx, finalText, true);
        log.info("Stream reply completed, userId={}, outTrackId={}, length={}",
                ctx.userId, ctx.outTrackId, finalText.length());
    }

    private void handleError(StreamReplyContext ctx, Throwable error) {
        log.error("Stream reply error, userId={}, outTrackId={}", ctx.userId, ctx.outTrackId, error);
        String errorText = ERROR_TEXT_PREFIX + (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());

        // Create a card to carry the error message if not already created
        if (ctx.cardCreated.compareAndSet(false, true)) {
            CardDeliveryResult delivery = aiCardService.createAndDeliver(
                    ctx.conversationType, ctx.conversationId, ctx.userId, Map.of());
            if (delivery != null) {
                ctx.outTrackId = delivery.outTrackId();
                ctx.processQueryKeyRef.set(delivery.processQueryKey());
            }
        }

        if (ctx.outTrackId != null) {
            pushStreamingUpdate(ctx, errorText, true);
        }
    }

    private void pushStreamingUpdate(StreamReplyContext ctx, String fullContent, boolean finalize) {
        try {
            aiCardService.streamingUpdate(ctx.outTrackId, fullContent, finalize);
        } catch (Exception e) {
            log.warn("streamingUpdate failed, outTrackId={}, finalize={}", ctx.outTrackId, finalize, e);
        }
    }

    /**
     * Streaming reply context.
     *
     * @description Holds mutable state for a single reply cycle, including card identifiers,
     *              latest text, and throttle timestamps.
     */
    private static final class StreamReplyContext {
        final String userId;
        final String conversationId;
        final String conversationType;
        final java.util.concurrent.atomic.AtomicBoolean cardCreated = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicLong lastUpdateTs = new java.util.concurrent.atomic.AtomicLong(0L);
        final AtomicReference<String> latestText = new AtomicReference<>("");
        final AtomicReference<String> processQueryKeyRef = new AtomicReference<>();
        volatile String outTrackId;

        StreamReplyContext(String userId, String conversationId, String conversationType) {
            this.userId = userId;
            this.conversationId = conversationId;
            this.conversationType = conversationType;
        }
    }
}
