package com.smart.agent.persistence.mapper;

import com.smart.agent.persistence.entity.AgentSessionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Agent session Mapper
 *
 * @description MyBatis-Plus based Agent session data access interface, providing basic CRUD operations
 *              on the agent_session table.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSessionEntity> {

    /**
     * Atomically insert or update session data
     *
     * @description Uses INSERT ON DUPLICATE KEY UPDATE to implement atomic upsert, avoiding concurrent race conditions
     * @param userId user ID
     * @param agentName agent name
     * @param sessionId session ID
     * @param dataKey data key
     * @param dataValue data value
     * @return number of affected rows
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    int upsertSession(@Param("userId") String userId,
                      @Param("agentName") String agentName,
                      @Param("sessionId") String sessionId,
                      @Param("dataKey") String dataKey,
                      @Param("dataValue") String dataValue);

    /**
     * Delete expired session records
     *
     * @description Cleans up session data before the specified date for periodic data cleanup
     * @param cutoffDate cutoff date; records earlier than this date will be deleted
     * @return number of affected rows
     * @author Jiangbo Li
     * @date 2026-06-16
     */
    int deleteExpiredRecords(@Param("cutoffDate") java.util.Date cutoffDate);
}
