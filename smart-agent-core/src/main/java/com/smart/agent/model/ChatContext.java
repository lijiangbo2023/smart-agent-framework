package com.smart.agent.model;

import com.smart.agent.constant.enums.MessageChannel;

/**
 * 对话上下文
 *
 * @description 封装智能对话的上下文信息，包含会话ID、用户ID、用户消息、消息渠道、业务名称及会话类型等
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
