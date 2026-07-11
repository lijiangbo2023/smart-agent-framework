package com.smart.agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Chat request model.
 *
 * @description Encapsulates the parameters of a chat request initiated by the client, including the user message content, user ID, and session ID.
 *              Input validation is enforced via Jakarta Bean Validation annotations.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Data
public class ChatRequest {

    @NotBlank(message = "message is required")
    @Size(max = 10000, message = "message must not exceed 10000 characters")
    private String message;

    @NotBlank(message = "userId is required")
    @Size(max = 128, message = "userId must not exceed 128 characters")
    private String userId;

    @NotBlank(message = "sessionId is required")
    @Size(max = 128, message = "sessionId must not exceed 128 characters")
    private String sessionId;
}
