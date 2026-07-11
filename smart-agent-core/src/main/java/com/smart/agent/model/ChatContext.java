package com.smart.agent.model;

import com.smart.agent.constant.enums.MessageChannel;

/**
 * Chat context.
 *
 * @description Encapsulates the context information of an intelligent conversation, including session ID, user ID, user message, message channel, business name, and conversation type
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
public record ChatContext(
        String sessionId,
        String userId,
        String userMessage,
        MessageChannel channel,
        String businessName,
        String conversationId,
        String conversationType
) {
}
