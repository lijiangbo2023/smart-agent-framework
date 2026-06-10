package com.smart.agent.model;

import lombok.Data;

/**
 * 对话请求模型
 *
 * @description 封装客户端发起对话请求的参数，包含用户消息内容、用户ID和会话ID
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Data
public class ChatRequest {
    private String message;
    private String userId;
    private String sessionId;
}
