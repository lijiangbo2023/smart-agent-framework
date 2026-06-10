package com.smart.agent.service;

import com.smart.agent.persistence.entity.AgentConversationSessionEntity;
import com.smart.agent.persistence.mapper.AgentConversationSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 智能体会话-对话映射服务
 *
 * @description 管理会话与对话之间的映射关系，支持会话的解析创建、重新绑定和会话类型查询
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Service
public class AgentConversationSessionService {

    private final AgentConversationSessionMapper agentConversationSessionMapper;

    public AgentConversationSessionService(AgentConversationSessionMapper agentConversationSessionMapper) {
        this.agentConversationSessionMapper = agentConversationSessionMapper;
    }

    /**
     * 解析或创建会话
     *
     * @description 根据用户ID和对话ID查找已有会话，若不存在则创建新会话并返回会话ID，异常时回退为临时会话ID
     * @param userId 用户ID
     * @param conversationId 对话ID
     * @param conversationType 对话类型
     * @param businessName 业务名称
     * @return 已有或新创建的会话ID
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public String resolveOrCreate(String userId, String conversationId, String conversationType, String businessName) {
        try {
            LambdaQueryWrapper<AgentConversationSessionEntity> wrapper =
                    new LambdaQueryWrapper<AgentConversationSessionEntity>()
                            .eq(AgentConversationSessionEntity::getUserId, userId)
                            .eq(AgentConversationSessionEntity::getConversationId, conversationId)
                            .eq(businessName != null, AgentConversationSessionEntity::getBusinessName, businessName)
                            .orderByDesc(AgentConversationSessionEntity::getGmtModified)
                            .last("LIMIT 1");
            AgentConversationSessionEntity existing = agentConversationSessionMapper.selectOne(wrapper);
            if (existing != null) {
                return existing.getCurrentSessionId();
            }

            String newSessionId = generateSessionId();
            agentConversationSessionMapper.insertOrIgnore(userId, conversationId, newSessionId, conversationType, businessName);
            return newSessionId;
        } catch (Exception e) {
            String tempSessionId = generateSessionId();
            log.error("resolveOrCreate failed, fallback to temp sessionId. userId={}, conversationId={}",
                    userId, conversationId, e);
            return tempSessionId;
        }
    }

    /**
     * 重新绑定会话
     *
     * @description 为指定用户和对话生成新的会话ID并更新绑定关系，用于重置对话上下文
     * @param userId 用户ID
     * @param conversationId 对话ID
     * @param businessName 业务名称
     * @return 新生成的会话ID
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public String rebind(String userId, String conversationId, String businessName) {
        String newSessionId = generateSessionId();
        try {
            agentConversationSessionMapper.upsert(userId, conversationId, newSessionId, null, businessName);
            log.info("rebind ok: userId={}, conversationId={}, businessName={}, newSessionId={}",
                    userId, conversationId, businessName, newSessionId);
            return newSessionId;
        } catch (Exception e) {
            log.error("rebind failed, fallback to in-memory sessionId. userId={}, conversationId={}",
                    userId, conversationId, e);
            return newSessionId;
        }
    }

    /**
     * 解析对话类型
     *
     * @description 根据用户ID和对话ID查询对话类型，查询失败或不存在时默认返回群聊类型
     * @param userId 用户ID
     * @param conversationId 对话ID
     * @return 对话类型编码，默认为"2"（群聊）
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public String resolveConversationType(String userId, String conversationId) {
        try {
            LambdaQueryWrapper<AgentConversationSessionEntity> wrapper =
                    new LambdaQueryWrapper<AgentConversationSessionEntity>()
                            .eq(AgentConversationSessionEntity::getUserId, userId)
                            .eq(AgentConversationSessionEntity::getConversationId, conversationId)
                            .select(AgentConversationSessionEntity::getConversationType)
                            .orderByDesc(AgentConversationSessionEntity::getGmtModified)
                            .last("LIMIT 1");
            AgentConversationSessionEntity entity = agentConversationSessionMapper.selectOne(wrapper);
            String type = entity != null ? entity.getConversationType() : null;
            if (type == null || type.isEmpty()) {
                return "2";
            }
            return type;
        } catch (Exception e) {
            log.error("resolveConversationType failed, fallback to GROUP. userId={}, conversationId={}",
                    userId, conversationId, e);
            return "2";
        }
    }

    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
