package com.smart.agent.service;

import com.smart.agent.util.SensitiveUtils;
import com.smart.agent.constant.enums.FeedbackType;
import com.smart.agent.constant.enums.MessageChannel;
import com.smart.agent.constant.enums.MessageStatus;
import com.smart.agent.persistence.entity.AgentChatMessageEntity;
import com.smart.agent.persistence.mapper.AgentChatMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent chat message service
 *
 * @description Manages database operations for agent chat messages, including saving user input,
 *              updating agent output, managing feedback and other features
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
     * Save user input message
     *
     * @description Persists user chat input to the database with initial processing status
     * @param sessionId session ID
     * @param userId user ID
     * @param userInput user input content
     * @param channel message channel
     * @param businessName business name
     * @param conversationId conversation ID
     * @param conversationType conversation type
     * @return message record ID, or null if saving fails
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public Long saveUserInput(String sessionId, String userId, String userInput,
                              MessageChannel channel, String businessName,
                              String conversationId, String conversationType) {
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
    }

    /**
     * Update agent output
     *
     * @description Updates the agent reply content and processing status for the specified message record
     * @param messageId message record ID
     * @param agentOutput agent output content
     * @param status message processing status
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
     * Update message feedback
     *
     * @description Updates the user feedback status for a message, supporting like/dislike toggle
     *              (clicking the same action again cancels it)
     * @param messageId message record ID
     * @param action feedback action (like/dislike)
     * @param currentStatus current feedback status
     * @return updated feedback status
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public String updateFeedback(Long messageId, String action, String currentStatus) {
        if (messageId == null) {
            throw new IllegalArgumentException("messageId is required");
        }
        String newStatus = action.equals(currentStatus) ? "none" : action;

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
     * Update feedback comment
     *
     * @description Updates the user feedback comment content for the specified message record
     * @param messageId message record ID
     * @param feedbackComment feedback comment content
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
     * Update process query key
     *
     * @description Updates the process query key for the specified message record, used to correlate business processes
     * @param messageId message record ID
     * @param processQueryKey process query key
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

    /**
     * Paginated query of user conversation history
     *
     * @description Returns paginated results in descending order by creation time,
     *              with agentOutput masked for sensitive information
     * @param userId user ID
     * @param page page number (starting from 1)
     * @param size page size
     * @return result Map containing records, total, page, size and pages.
     *         Each record contains: id, sessionId, userInput, agentOutput (masked), status,
     *         feedbackType, gmtCreate, conversationId
     * @author Jiangbo Li
     * @date 2026-06-16
     */
    public Map<String, Object> getHistoryPage(String userId, int page, int size) {
        LambdaQueryWrapper<AgentChatMessageEntity> wrapper = new LambdaQueryWrapper<AgentChatMessageEntity>()
                .eq(AgentChatMessageEntity::getUserId, userId)
                .orderByDesc(AgentChatMessageEntity::getGmtCreate);
        IPage<AgentChatMessageEntity> pageResult = agentChatMessageMapper.selectPage(
                new Page<>(page, size), wrapper);

        List<Map<String, Object>> records = pageResult.getRecords().stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("sessionId", m.getSessionId());
            map.put("userInput", m.getUserInput());
            map.put("agentOutput", m.getAgentOutput() != null ? SensitiveUtils.mask(m.getAgentOutput()) : null);
            map.put("status", m.getStatus());
            map.put("feedbackType", m.getFeedbackType());
            map.put("gmtCreate", m.getGmtCreate());
            map.put("conversationId", m.getConversationId());
            return map;
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("pages", pageResult.getPages());
        return result;
    }

    /**
     * Get all messages for a given session
     *
     * @description Returns all messages in the session in ascending order by creation time,
     *              with agentOutput masked for sensitive information
     * @param userId user ID
     * @param sessionId session ID
     * @return list of messages (converted to Map, containing id, userInput, agentOutput, status,
     *         feedbackType, gmtCreate)
     * @author Jiangbo Li
     * @date 2026-06-16
     */
    public List<Map<String, Object>> getConversationMessages(String userId, String sessionId) {
        LambdaQueryWrapper<AgentChatMessageEntity> wrapper = new LambdaQueryWrapper<AgentChatMessageEntity>()
                .eq(AgentChatMessageEntity::getUserId, userId)
                .eq(AgentChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(AgentChatMessageEntity::getGmtCreate);
        return agentChatMessageMapper.selectList(wrapper).stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("userInput", m.getUserInput());
            map.put("agentOutput", m.getAgentOutput() != null ? SensitiveUtils.mask(m.getAgentOutput()) : null);
            map.put("status", m.getStatus());
            map.put("feedbackType", m.getFeedbackType());
            map.put("gmtCreate", m.getGmtCreate());
            return map;
        }).toList();
    }

    /**
     * Get the userId that owns a specific message.
     *
     * @description Queries the userId associated with the given messageId for ownership validation
     * @param messageId message record ID
     * @return the userId that owns the message, or null if not found
     * @author Jiangbo Li
     * @date 2026-07-11
     */
    public String getMessageOwner(Long messageId) {
        if (messageId == null) {
            return null;
        }
        AgentChatMessageEntity entity = agentChatMessageMapper.selectById(messageId);
        return entity != null ? entity.getUserId() : null;
    }

    /**
     * Clean up expired message records (with LIMIT to avoid long-running transactions)
     *
     * @description Deletes up to {@code limit} message records earlier than the specified date
     * @param cutoffDate cutoff date
     * @param limit maximum number of records to delete per call
     * @return number of deleted records
     * @author Jiangbo Li
     * @date 2026-06-16
     */
    public int cleanupExpiredMessages(Date cutoffDate, int limit) {
        try {
            LambdaQueryWrapper<AgentChatMessageEntity> wrapper = new LambdaQueryWrapper<AgentChatMessageEntity>()
                    .lt(AgentChatMessageEntity::getGmtCreate, cutoffDate)
                    .last("LIMIT " + limit);
            return agentChatMessageMapper.delete(wrapper);
        } catch (Exception e) {
            log.error("cleanupExpiredMessages failed", e);
            return 0;
        }
    }
}
