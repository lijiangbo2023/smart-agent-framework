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
 * Agent chat message entity
 *
 * @description Maps to the agent_chat_message table, recording chat messages between Agent and users,
 *              including user input, agent output, session info, channel, feedback and other fields.
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
