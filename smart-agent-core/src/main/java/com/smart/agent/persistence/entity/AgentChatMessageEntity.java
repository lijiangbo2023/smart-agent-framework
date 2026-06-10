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
 * Agent聊天消息实体
 *
 * @description 对应agent_chat_message表，记录Agent与用户的聊天消息，包含用户输入、Agent输出、
 *              会话信息、渠道、反馈等字段。
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@TableName("agent_chat_message")
public class AgentChatMessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Date gmtCreate;

    private Date gmtModified;

    private String sessionId;

    private String userId;

    private String businessName;

    private String userInput;

    private String agentOutput;

    private String channel;

    private Integer status;

    private Integer feedbackType;

    private String conversationId;

    private String conversationType;

    private String processQueryKey;

    private String feedbackComment;
}
