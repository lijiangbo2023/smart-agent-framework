package com.smart.agent.service;

import com.smart.agent.constant.enums.FeedbackType;
import com.smart.agent.constant.enums.MessageChannel;
import com.smart.agent.constant.enums.MessageStatus;
import com.smart.agent.persistence.entity.AgentChatMessageEntity;
import com.smart.agent.persistence.mapper.AgentChatMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 智能体聊天消息服务
 *
 * @description 管理智能体聊天消息的数据库操作，包括用户输入保存、智能体输出更新、反馈管理等功能
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Service
public class AgentChatMessageService {

    private final AgentChatMessageMapper agentChatMessageMapper;

    public AgentChatMessageService(AgentChatMessageMapper agentChatMessageMapper) {
        this.agentChatMessageMapper = agentChatMessageMapper;
    }

    /**
     * 保存用户输入消息
     *
     * @description 将用户的聊天输入持久化到数据库，初始状态为处理中
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param userInput 用户输入内容
     * @param channel 消息渠道
     * @param businessName 业务名称
     * @param conversationId 会话ID
     * @param conversationType 会话类型
     * @return 消息记录ID，保存失败时返回null
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public Long saveUserInput(String sessionId, String userId, String userInput,
                              MessageChannel channel, String businessName,
                              String conversationId, String conversationType) {
        try {
            AgentChatMessageEntity entity = AgentChatMessageEntity.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .userInput(userInput)
                    .channel(channel.getCode())
                    .businessName(businessName)
                    .conversationId(conversationId)
                    .conversationType(conversationType)
                    .status(MessageStatus.PROCESSING.getCode())
                    .feedbackType(FeedbackType.NONE.getCode())
                    .build();
            agentChatMessageMapper.insert(entity);
            return entity.getId();
        } catch (Exception e) {
            log.error("saveUserInput failed, sessionId={}, userId={}", sessionId, userId, e);
            return null;
        }
    }

    /**
     * 更新智能体输出
     *
     * @description 更新指定消息记录的智能体回复内容和处理状态
     * @param messageId 消息记录ID
     * @param agentOutput 智能体输出内容
     * @param status 消息处理状态
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void updateAgentOutput(Long messageId, String agentOutput, MessageStatus status) {
        if (messageId == null) {
            return;
        }
        try {
            AgentChatMessageEntity entity = new AgentChatMessageEntity();
            entity.setId(messageId);
            entity.setAgentOutput(agentOutput);
            entity.setStatus(status.getCode());
            agentChatMessageMapper.updateById(entity);
        } catch (Exception e) {
            log.error("updateAgentOutput failed, messageId={}", messageId, e);
        }
    }

    /**
     * 更新消息反馈
     *
     * @description 更新消息的用户反馈状态，支持点赞/点踩的切换操作（再次点击相同操作则取消）
     * @param messageId 消息记录ID
     * @param action 反馈动作（like/dislike）
     * @param currentStatus 当前反馈状态
     * @return 更新后的反馈状态
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public String updateFeedback(Long messageId, String action, String currentStatus) {
        String newStatus = action.equals(currentStatus) ? "none" : action;

        if (messageId == null) {
            return newStatus;
        }

        try {
            FeedbackType feedbackType = switch (newStatus) {
                case "like" -> FeedbackType.LIKE;
                case "dislike" -> FeedbackType.DISLIKE;
                default -> FeedbackType.NONE;
            };

            AgentChatMessageEntity entity = new AgentChatMessageEntity();
            entity.setId(messageId);
            entity.setFeedbackType(feedbackType.getCode());
            agentChatMessageMapper.updateById(entity);
            return newStatus;
        } catch (Exception e) {
            log.error("updateFeedback failed, messageId={}", messageId, e);
            throw new RuntimeException("updateFeedback DB error", e);
        }
    }

    /**
     * 更新反馈评论
     *
     * @description 更新指定消息记录的用户反馈评论内容
     * @param messageId 消息记录ID
     * @param feedbackComment 反馈评论内容
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void updateFeedbackComment(Long messageId, String feedbackComment) {
        if (messageId == null) {
            return;
        }
        try {
            AgentChatMessageEntity entity = new AgentChatMessageEntity();
            entity.setId(messageId);
            entity.setFeedbackComment(feedbackComment);
            agentChatMessageMapper.updateById(entity);
        } catch (Exception e) {
            log.error("updateFeedbackComment failed, messageId={}", messageId, e);
            throw new RuntimeException("updateFeedbackComment DB error", e);
        }
    }

    /**
     * 更新流程查询键
     *
     * @description 更新指定消息记录的流程查询键，用于关联业务流程
     * @param messageId 消息记录ID
     * @param processQueryKey 流程查询键
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void updateProcessQueryKey(Long messageId, String processQueryKey) {
        if (messageId == null || processQueryKey == null) {
            return;
        }
        try {
            AgentChatMessageEntity entity = new AgentChatMessageEntity();
            entity.setId(messageId);
            entity.setProcessQueryKey(processQueryKey);
            agentChatMessageMapper.updateById(entity);
        } catch (Exception e) {
            log.error("updateProcessQueryKey failed, messageId={}, processQueryKey={}", messageId, processQueryKey, e);
        }
    }
}
