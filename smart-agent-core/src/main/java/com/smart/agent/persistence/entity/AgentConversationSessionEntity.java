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
 * Agent conversation session entity
 *
 * @description Maps to the agent_conversation_session_mapper table, maintaining the mapping between
 *              conversation IDs and sessions, supporting session association by user, conversation type
 *              and business name.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@TableName("agent_conversation_session_mapper")
public class AgentConversationSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Date gmtCreate;

    private Date gmtModified;

    private String userId;

    private String conversationId;

    private String currentSessionId;

    private String conversationType;

    private String businessName;
}
