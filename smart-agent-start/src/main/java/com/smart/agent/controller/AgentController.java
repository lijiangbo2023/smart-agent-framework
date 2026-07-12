package com.smart.agent.controller;

import com.smart.agent.agent.service.SupervisorAgentService;
import com.smart.agent.constant.enums.MessageChannel;
import com.smart.agent.filter.ApiKeyAuthInterceptor;
import com.smart.agent.model.ChatContext;
import com.smart.agent.model.ChatRequest;
import com.smart.agent.model.ChatResult;
import com.smart.agent.model.ChatStreamResult;
import com.smart.agent.model.ServiceResponse;
import com.smart.agent.service.AgentChatMessageService;
import com.smart.agent.util.SensitiveUtils;
import com.smart.agent.util.SseEventHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Agent conversation controller
 *
 * @description Provides REST API endpoints for Agent conversations, including synchronous chat,
 *              streaming chat, history query, conversation detail query and like/dislike feedback
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent", description = "Agent conversation API")
public class AgentController {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;
    private static final String DEFAULT_BUSINESS = "smart-agent";

    private final SupervisorAgentService supervisorAgentService;
    private final AgentChatMessageService agentChatMessageService;
    private final ObjectMapper objectMapper;

    public AgentController(SupervisorAgentService supervisorAgentService,
                           AgentChatMessageService agentChatMessageService,
                           ObjectMapper objectMapper) {
        this.supervisorAgentService = supervisorAgentService;
        this.agentChatMessageService = agentChatMessageService;
        this.objectMapper = objectMapper;
    }

    /**
     * Synchronous chat
     *
     * @description Receives a user message and synchronously returns the Agent's reply,
     *              with sensitive information masked
     * @param request chat request containing user ID, session ID and message content
     * @return response containing the Agent's reply text
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @PostMapping("/chat")
    @Operation(summary = "Synchronous chat")
    public ServiceResponse<String> chat(HttpServletRequest httpRequest,
                                        @Valid @RequestBody ChatRequest request) {
        validateUserIdOwnership(httpRequest, request.getUserId());
        log.info("Chat request, userId={}, sessionId={}", request.getUserId(), request.getSessionId());
        ChatContext context = new ChatContext(request.getSessionId(), request.getUserId(), request.getMessage(),
                MessageChannel.HTTP, DEFAULT_BUSINESS, null, null);
        ChatResult result = supervisorAgentService.chat(context);
        return ServiceResponse.success(SensitiveUtils.mask(result.responseText()));
    }

    /**
     * Streaming chat
     *
     * @description Receives a user message and streams the Agent's reply via SSE (Server-Sent Events),
     *              supporting real-time push of conversation events
     * @param request chat request containing user ID, session ID and message content
     * @return SSE emitter for streaming conversation results
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Streaming chat")
    public SseEmitter chatStream(HttpServletRequest httpRequest,
                                  @Valid @RequestBody ChatRequest request) {
        validateUserIdOwnership(httpRequest, request.getUserId());
        log.info("Stream chat request, userId={}, sessionId={}", request.getUserId(), request.getSessionId());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        ChatContext context = new ChatContext(request.getSessionId(), request.getUserId(), request.getMessage(),
                MessageChannel.HTTP, DEFAULT_BUSINESS, null, null);
        ChatStreamResult streamResult = supervisorAgentService.chatStream(context);
        streamResult.eventStream()
                .subscribe(
                        event -> SseEventHelper.sendEvent(emitter, event, objectMapper),
                        error -> {
                            log.error("Stream error: {}", error.getMessage());
                            emitter.completeWithError(error);
                        },
                        emitter::complete
                );

        emitter.onTimeout(() -> log.warn("SSE timeout for userId: {}", request.getUserId()));
        emitter.onError(error -> log.error("SSE error for userId: {}", request.getUserId(), error));

        return emitter;
    }

    /**
     * Get user conversation history
     *
     * @description Queries all history records for the given user ID, ordered by creation time descending
     * @param userId user ID
     * @return response containing a list of conversation records, each including message ID, session ID,
     *         user input, agent output, status, feedback type and creation time
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @GetMapping("/history/{userId}")
    @Operation(summary = "Get user conversation history")
    public ServiceResponse<Map<String, Object>> getHistory(
            HttpServletRequest httpRequest,
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9_\\-.:@]+", message = "userId contains invalid characters") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateUserIdOwnership(httpRequest, userId);
        Map<String, Object> result = agentChatMessageService.getHistoryPage(userId, page, size);
        return ServiceResponse.success(result);
    }

    /**
     * Get conversation details
     *
     * @description Queries all conversation messages under the given user ID and session ID,
     *              ordered by creation time ascending
     * @param userId user ID
     * @param sessionId session ID
     * @return response containing a list of conversation details, each including message ID, user input,
     *         agent output, status, feedback type and creation time
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @GetMapping("/conversation/{userId}/{sessionId}")
    @Operation(summary = "Get conversation details")
    public ServiceResponse<List<Map<String, Object>>> getConversation(
            HttpServletRequest httpRequest,
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9_\\-.:@]+", message = "userId contains invalid characters") String userId,
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9_\\-.:@]+", message = "sessionId contains invalid characters") String sessionId) {
        validateUserIdOwnership(httpRequest, userId);
        List<Map<String, Object>> result = agentChatMessageService.getConversationMessages(userId, sessionId);
        return ServiceResponse.success(result);
    }

    /**
     * Like/dislike feedback
     *
     * @description Performs a like or dislike action on the specified message, supporting feedback status toggle
     * @param request request body containing messageId, action and currentStatus
     * @return response containing the updated feedback status
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @PostMapping("/feedback")
    @Operation(summary = "Like/dislike feedback")
    public ServiceResponse<String> feedback(HttpServletRequest httpRequest,
                                             @Valid @RequestBody FeedbackRequest request) {
        // Validate that the authenticated user owns this message
        String authenticatedUserId = (String) httpRequest.getAttribute(ApiKeyAuthInterceptor.AUTHENTICATED_USER_ID_ATTR);
        if (authenticatedUserId != null) {
            String ownerUserId = agentChatMessageService.getMessageOwner(request.getMessageId());
            if (ownerUserId == null || !ownerUserId.equals(authenticatedUserId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this message");
            }
        }
        String currentStatus = request.getCurrentStatus() != null ? request.getCurrentStatus() : "none";
        String newStatus = agentChatMessageService.updateFeedback(request.getMessageId(), request.getAction(), currentStatus);
        return ServiceResponse.success(newStatus);
    }

    /**
     * Delete all messages under a session.
     */
    @DeleteMapping("/conversation/{userId}/{sessionId}")
    @Operation(summary = "Delete conversation")
    public ServiceResponse<String> deleteConversation(
            HttpServletRequest httpRequest,
            @PathVariable String userId,
            @PathVariable String sessionId) {
        validateUserIdOwnership(httpRequest, userId);
        int deleted = agentChatMessageService.deleteBySessionId(userId, sessionId);
        log.info("Deleted {} messages for userId={}, sessionId={}", deleted, userId, sessionId);
        return ServiceResponse.success("deleted " + deleted + " messages");
    }

    /**
     * Feedback request model
     *
     * @description Encapsulates the parameters for a like/dislike feedback request
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    @Data
    public static class FeedbackRequest {
        @NotNull(message = "messageId is required")
        private Long messageId;
        @NotBlank(message = "action is required")
        @Pattern(regexp = "like|dislike", message = "action must be 'like' or 'dislike'")
        private String action;
        private String currentStatus;
    }

    /**
     * Validates that the authenticated user (from X-User-Id header) matches the target userId.
     * Skips validation when no authenticated user is present (e.g., auth disabled in local dev).
     */
    private void validateUserIdOwnership(HttpServletRequest httpRequest, String targetUserId) {
        String authenticatedUserId = (String) httpRequest.getAttribute(ApiKeyAuthInterceptor.AUTHENTICATED_USER_ID_ATTR);
        if (authenticatedUserId != null && !authenticatedUserId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: userId mismatch");
        }
    }
}
