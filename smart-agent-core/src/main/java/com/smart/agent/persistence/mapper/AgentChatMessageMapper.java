package com.smart.agent.persistence.mapper;

import com.smart.agent.persistence.entity.AgentChatMessageEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent聊天消息Mapper
 *
 * @description 基于MyBatis-Plus的Agent聊天消息数据访问接口，提供对agent_chat_message表的基础CRUD操作。
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Mapper
public interface AgentChatMessageMapper extends BaseMapper<AgentChatMessageEntity> {
}
