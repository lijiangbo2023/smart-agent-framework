package com.smart.agent.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Agent会话实体
 *
 * @description 对应agent_session表，存储Agent会话的键值数据，
 *              支持按用户、Agent名称和会话ID进行数据隔离。
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@TableName("agent_session")
public class AgentSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Date gmtCreate;

    private Date gmtModified;

    private String userId;

    private String agentName;

    private String sessionId;

    private String dataKey;

    private String dataValue;
}
