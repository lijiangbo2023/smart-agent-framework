package com.smart.agent.model;

import io.agentscope.core.agent.Event;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式对话结果
 *
 * @description 封装流式智能对话的响应结果，包含消息ID、事件流和最新文本引用，支持SSE方式的实时响应推送
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
public record ChatStreamResult(Long messageId, Flux<Event> eventStream, AtomicReference<String> latestTextRef) {
}
