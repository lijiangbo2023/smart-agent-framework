package com.smart.agent.persistence.mapper;

import com.smart.agent.persistence.entity.AgentSessionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent会话Mapper
 *
 * @description 基于MyBatis-Plus的Agent会话数据访问接口，提供对agent_session表的基础CRUD操作。
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSessionEntity> {
}
