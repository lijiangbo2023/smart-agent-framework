package com.smart.agent.callback.chatbot;

import com.smart.agent.dingtalk.DingTalkAiCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * DingTalk AI card streaming reply adapter.
 *
 * Simplified version following the reference project pattern:
 * - Card created BEFORE LLM stream starts (immediate "处理中..." feedback)
 * - Event stream subscribed directly, only AGENT_RESULT text goes to card
 * - Template auto-manages pending→writing→done via streamingUpdate lifecycle
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DingTalkReplyAdapter {

    private static final long MIN_UPDATE_INTERVAL_MS = 300L;
    private static final String EMPTY_RESPONSE_TEXT = "（没有返回有效回复）";
    private static final String ERROR_TEXT = "处理失败，请稍后重试";

    private final DingTalkAiCardService aiCardService;

    /**
     * Stream agent output into a pre-created card.
     * Card template auto-manages state transitions based on streamingUpdate calls.
     */
    public void streamIntoCard(String outTrackId, Flux<String> textStream,
                                AtomicReference<String> latestTextRef) {
        AtomicLong lastUpdateAt = new AtomicLong(0L);

        textStream
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        text -> {
                            if (text == null || text.isEmpty()) return;
                            latestTextRef.set(text);
                            long now = System.currentTimeMillis();
                            long last = lastUpdateAt.get();
                            if (now - last >= MIN_UPDATE_INTERVAL_MS
                                    && lastUpdateAt.compareAndSet(last, now)) {
                                aiCardService.streamingUpdate(outTrackId, text, false);
                            }
                        },
                        error -> {
                            log.error("Stream error, outTrackId={}", outTrackId, error);
                            aiCardService.streamingUpdate(outTrackId, ERROR_TEXT, true);
                        },
                        () -> {
                            String finalText = latestTextRef.get();
                            if (finalText == null || finalText.isEmpty()) {
                                finalText = EMPTY_RESPONSE_TEXT;
                            }
                            aiCardService.streamingUpdate(outTrackId, finalText, true);
                            log.info("Stream reply completed, outTrackId={}, length={}", outTrackId, finalText.length());
                        }
                );
    }
}
