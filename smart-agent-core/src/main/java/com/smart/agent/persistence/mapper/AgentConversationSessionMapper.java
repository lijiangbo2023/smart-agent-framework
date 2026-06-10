package com.smart.agent.persistence.mapper;

import com.smart.agent.persistence.entity.AgentConversationSessionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Agent会话映射Mapper
 *
 * @description 基于MyBatis-Plus的会话映射数据访问接口，提供对agent_conversation_session_mapper表的
 *              CRUD操作，并扩展了insertOrIgnore和upsert方法。
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Mapper
public interface AgentConversationSessionMapper extends BaseMapper<AgentConversationSessionEntity> {

    /**
     * 插入会话映射记录（忽略冲突）
     *
     * @description 插入一条会话与Session的映射记录，若记录已存在则忽略，不抛出异常。
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param currentSessionId 当前Session ID
     * @param conversationType 会话类型
     * @param businessName 业务名称
     * @return 受影响的行数
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    int insertOrIgnore(@Param("userId") String userId,
                       @Param("conversationId") String conversationId,
                       @Param("currentSessionId") String currentSessionId,
                       @Param("conversationType") String conversationType,
                       @Param("businessName") String businessName);

    /**
     * 插入或更新会话映射记录
     *
     * @description 插入一条会话与Session的映射记录，若记录已存在则更新currentSessionId等字段。
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param currentSessionId 当前Session ID
     * @param conversationType 会话类型
     * @param businessName 业务名称
     * @return 受影响的行数
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    int upsert(@Param("userId") String userId,
               @Param("conversationId") String conversationId,
               @Param("currentSessionId") String currentSessionId,
               @Param("conversationType") String conversationType,
               @Param("businessName") String businessName);
}
