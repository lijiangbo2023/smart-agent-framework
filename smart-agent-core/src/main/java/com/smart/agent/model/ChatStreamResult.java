package com.smart.agent.model;

import io.agentscope.core.agent.Event;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Streaming chat result.
 *
 * @description Encapsulates the response result of a streaming intelligent conversation, containing the message ID, event stream, and latest text reference, supporting SSE-based real-time response delivery
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
public record ChatStreamResult(Long messageId, Flux<Event> eventStream, AtomicReference<String> latestTextRef) {
}
