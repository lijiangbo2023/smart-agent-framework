package com.smart.agent.persistence.mapper;

import com.smart.agent.persistence.entity.AgentConversationSessionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Agent conversation session Mapper
 *
 * @description MyBatis-Plus based conversation session data access interface, providing CRUD operations
 *              on the agent_conversation_session_mapper table, with extended insertOrIgnore and upsert methods.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Mapper
public interface AgentConversationSessionMapper extends BaseMapper<AgentConversationSessionEntity> {

    /**
     * Insert a conversation session mapping record (ignore on conflict)
     *
     * @description Inserts a conversation-to-session mapping record; if the record already exists,
     *              it is silently ignored without throwing an exception.
     * @param userId user ID
     * @param conversationId conversation ID
     * @param currentSessionId current session ID
     * @param conversationType conversation type
     * @param businessName business name
     * @return number of affected rows
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    int insertOrIgnore(@Param("userId") String userId,
                       @Param("conversationId") String conversationId,
                       @Param("currentSessionId") String currentSessionId,
                       @Param("conversationType") String conversationType,
                       @Param("businessName") String businessName);

    /**
     * Insert or update a conversation session mapping record
     *
     * @description Inserts a conversation-to-session mapping record; if the record already exists,
     *              updates the currentSessionId and related fields.
     * @param userId user ID
     * @param conversationId conversation ID
     * @param currentSessionId current session ID
     * @param conversationType conversation type
     * @param businessName business name
     * @return number of affected rows
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    int upsert(@Param("userId") String userId,
               @Param("conversationId") String conversationId,
               @Param("currentSessionId") String currentSessionId,
               @Param("conversationType") String conversationType,
               @Param("businessName") String businessName);
}
