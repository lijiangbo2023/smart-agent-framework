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
 * Agent会话映射实体
 *
 * @description 对应agent_conversation_session_mapper表，维护会话ID与Session的映射关系，
 *              支持按用户、会话类型和业务名称进行会话关联。
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
