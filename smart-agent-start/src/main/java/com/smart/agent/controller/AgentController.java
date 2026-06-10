package com.smart.agent.controller;

import com.smart.agent.agent.service.SupervisorAgentService;
import com.smart.agent.constant.enums.MessageChannel;
import com.smart.agent.model.ChatRequest;
import com.smart.agent.model.ChatResult;
import com.smart.agent.model.ChatStreamResult;
import com.smart.agent.model.ServiceResponse;
import com.smart.agent.service.AgentChatMessageService;
import com.smart.agent.service.AgentConversationSessionService;
import com.smart.agent.persistence.entity.AgentChatMessageEntity;
import com.smart.agent.persistence.mapper.AgentChatMessageMapper;
import com.smart.agent.util.SensitiveUtils;
import com.smart.agent.util.SseEventHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent对话控制器
 *
 * @description 提供Agent对话相关的REST API接口，包括同步对话、流式对话、历史记录查询、会话详情查询和赞踩反馈功能
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent", description = "Agent 对话接口")
@CrossOrigin(origins = "*")
public class AgentController {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;
    private static final String DEFAULT_BUSINESS = "smart-agent";

    private final SupervisorAgentService supervisorAgentService;
    private final AgentChatMessageService agentChatMessageService;
    private final AgentConversationSessionService conversationSessionService;
    private final AgentChatMessageMapper agentChatMessageMapper;
    private final ObjectMapper objectMapper;

    public AgentController(SupervisorAgentService supervisorAgentService,
                           AgentChatMessageService agentChatMessageService,
                           AgentConversationSessionService conversationSessionService,
                           AgentChatMessageMapper agentChatMessageMapper,
                           ObjectMapper objectMapper) {
        this.supervisorAgentService = supervisorAgentService;
        this.agentChatMessageService = agentChatMessageService;
        this.conversationSessionService = conversationSessionService;
        this.agentChatMessageMapper = agentChatMessageMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 同步对话
     *
     * @description 接收用户消息并同步返回Agent的回复结果，回复内容经过敏感信息脱敏处理
     * @param request 对话请求，包含用户ID、会话ID和消息内容
     * @return 包含Agent回复文本的响应结果
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @PostMapping("/chat")
    @Operation(summary = "同步对话")
    public ServiceResponse<String> chat(@RequestBody ChatRequest request) {
        log.info("Chat request, userId={}, sessionId={}", request.getUserId(), request.getSessionId());
        ChatResult result = supervisorAgentService.chat(
                request.getUserId(), request.getSessionId(), request.getMessage(),
                MessageChannel.HTTP, DEFAULT_BUSINESS, null, null);
        return ServiceResponse.success(SensitiveUtils.mask(result.responseText()));
    }

    /**
     * 流式对话
     *
     * @description 接收用户消息并通过SSE（Server-Sent Events）流式返回Agent的回复，支持实时推送对话事件
     * @param request 对话请求，包含用户ID、会话ID和消息内容
     * @return SSE事件发射器，用于流式推送对话结果
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        log.info("Stream chat request, userId={}, sessionId={}", request.getUserId(), request.getSessionId());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        ChatStreamResult streamResult = supervisorAgentService.chatStream(
                request.getUserId(), request.getSessionId(), request.getMessage(),
                MessageChannel.HTTP, DEFAULT_BUSINESS, null, null);
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
     * 获取用户历史对话列表
     *
     * @description 根据用户ID查询其所有历史对话记录，按创建时间降序排列
     * @param userId 用户ID
     * @return 包含对话记录列表的响应结果，每条记录包含消息ID、会话ID、用户输入、Agent输出、状态、反馈类型和创建时间等信息
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @GetMapping("/history/{userId}")
    @Operation(summary = "获取用户历史对话列表")
    public ServiceResponse<List<Map<String, Object>>> getHistory(@PathVariable String userId) {
        LambdaQueryWrapper<AgentChatMessageEntity> wrapper = new LambdaQueryWrapper<AgentChatMessageEntity>()
                .eq(AgentChatMessageEntity::getUserId, userId)
                .orderByDesc(AgentChatMessageEntity::getGmtCreate);
        List<AgentChatMessageEntity> messages = agentChatMessageMapper.selectList(wrapper);

        List<Map<String, Object>> result = messages.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("sessionId", m.getSessionId());
            map.put("userInput", m.getUserInput());
            map.put("agentOutput", m.getAgentOutput());
            map.put("status", m.getStatus());
            map.put("feedbackType", m.getFeedbackType());
            map.put("gmtCreate", m.getGmtCreate());
            map.put("conversationId", m.getConversationId());
            return map;
        }).toList();

        return ServiceResponse.success(result);
    }

    /**
     * 获取指定会话的对话详情
     *
     * @description 根据用户ID和会话ID查询该会话下的所有对话消息，按创建时间升序排列
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 包含对话详情列表的响应结果，每条记录包含消息ID、用户输入、Agent输出、状态、反馈类型和创建时间等信息
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @GetMapping("/conversation/{userId}/{sessionId}")
    @Operation(summary = "获取指定会话的对话详情")
    public ServiceResponse<List<Map<String, Object>>> getConversation(@PathVariable String userId,
                                                                       @PathVariable String sessionId) {
        LambdaQueryWrapper<AgentChatMessageEntity> wrapper = new LambdaQueryWrapper<AgentChatMessageEntity>()
                .eq(AgentChatMessageEntity::getUserId, userId)
                .eq(AgentChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(AgentChatMessageEntity::getGmtCreate);
        List<AgentChatMessageEntity> messages = agentChatMessageMapper.selectList(wrapper);

        List<Map<String, Object>> result = messages.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("userInput", m.getUserInput());
            map.put("agentOutput", m.getAgentOutput());
            map.put("status", m.getStatus());
            map.put("feedbackType", m.getFeedbackType());
            map.put("gmtCreate", m.getGmtCreate());
            return map;
        }).toList();

        return ServiceResponse.success(result);
    }

    /**
     * 赞踩反馈
     *
     * @description 对指定消息进行赞或踩的反馈操作，支持切换反馈状态
     * @param body 请求体，包含messageId（消息ID）、action（反馈动作）和currentStatus（当前反馈状态）
     * @return 包含更新后反馈状态的响应结果
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @PostMapping("/feedback")
    @Operation(summary = "赞踩反馈")
    public ServiceResponse<String> feedback(@RequestBody Map<String, Object> body) {
        Long messageId = Long.valueOf(body.get("messageId").toString());
        String action = (String) body.get("action");
        String currentStatus = (String) body.getOrDefault("currentStatus", "none");
        String newStatus = agentChatMessageService.updateFeedback(messageId, action, currentStatus);
        return ServiceResponse.success(newStatus);
    }
}
