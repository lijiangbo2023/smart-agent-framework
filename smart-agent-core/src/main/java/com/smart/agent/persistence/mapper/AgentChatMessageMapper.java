package com.smart.agent.persistence.mapper;

import com.smart.agent.persistence.entity.AgentChatMessageEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent chat message Mapper
 *
 * @description MyBatis-Plus based Agent chat message data access interface, providing basic CRUD operations
 *              on the agent_chat_message table.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Mapper
public interface AgentChatMessageMapper extends BaseMapper<AgentChatMessageEntity> {
}
