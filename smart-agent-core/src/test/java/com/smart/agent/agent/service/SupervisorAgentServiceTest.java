package com.smart.agent.agent.service;

import com.smart.agent.agent.provider.SubAgent;
import com.smart.agent.agent.session.SessionLockService;
import com.smart.agent.component.AgentChatComponent;
import com.smart.agent.constant.enums.MessageChannel;
import com.smart.agent.constant.enums.MessageStatus;
import com.smart.agent.model.ChatContext;
import com.smart.agent.model.ChatResult;
import com.smart.agent.nacos.AgentPromptManager;
import com.smart.agent.persistence.mapper.AgentSessionMapper;
import com.smart.agent.service.AgentChatMessageService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.OpenAIChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupervisorAgentServiceTest {

    private OpenAIChatModel model;
    private AgentSessionMapper sessionMapper;
    private AgentPromptManager promptManager;
    private AgentChatMessageService messageService;
    private AgentChatComponent chatComponent;
    private SessionLockService lockService;

    private static final String LOCK_VAL = "lock-val";

    @BeforeEach
    void setUp() {
        model = mock(OpenAIChatModel.class);
        sessionMapper = mock(AgentSessionMapper.class);
        promptManager = mock(AgentPromptManager.class);
        messageService = mock(AgentChatMessageService.class);
        chatComponent = mock(AgentChatComponent.class);
        lockService = mock(SessionLockService.class);

        when(promptManager.getPrompt(anyString())).thenReturn("test prompt");
    }

    private SupervisorAgentService createService(List<SubAgent> subAgents) {
        return new SupervisorAgentService(
                model, subAgents, sessionMapper,
                promptManager, messageService, chatComponent, lockService);
    }

    // ========== construction ==========

    @Test
    void constructor_noSubAgents() {
        assertDoesNotThrow(() -> createService(Collections.emptyList()));
    }

    @Test
    void constructor_withSubAgents() {
        SubAgent agent = createMockSubAgent("TestAgent", "test_tool", "Test description");
        SupervisorAgentService service = createService(List.of(agent));
        assertNotNull(service);
    }

    @Test
    void constructor_registersPrompt() {
        createService(Collections.emptyList());
        verify(promptManager).register(eq("Supervisor"), anyString());
    }

    // ========== chat() lock behavior ==========

    @Test
    void chat_lockAcquired() {
        when(lockService.tryLock("u1", "s1")).thenReturn(LOCK_VAL);
        SupervisorAgentService service = createService(Collections.emptyList());

        ChatResult expectedResult = new ChatResult(1L, "result");
        when(chatComponent.chat(any(ReActAgent.class), any(), any(ChatContext.class), anyString()))
                .thenReturn(expectedResult);

        service.chat(makeContext("u1", "s1", "hello"));

        verify(lockService).tryLock("u1", "s1");
        verify(lockService).unlock("u1", "s1", LOCK_VAL);
    }

    @Test
    void chat_lockNotAcquired_throwsRuntimeException() {
        when(lockService.tryLock("u1", "s1")).thenReturn(null);
        SupervisorAgentService service = createService(Collections.emptyList());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.chat(makeContext("u1", "s1", "hello")));

        assertTrue(ex.getMessage().contains("正在处理中"));
        verify(lockService, never()).unlock(anyString(), anyString(), anyString());
    }

    @Test
    void chat_lockReleasedOnException() {
        when(lockService.tryLock("u1", "s1")).thenReturn(LOCK_VAL);
        SupervisorAgentService service = createService(Collections.emptyList());

        when(chatComponent.chat(any(), any(), any(), anyString()))
                .thenThrow(new RuntimeException("agent error"));

        assertThrows(RuntimeException.class, () ->
                service.chat(makeContext("u1", "s1", "hello")));

        verify(lockService).unlock("u1", "s1", LOCK_VAL);
    }

    @Test
    void chat_delegatesToChatComponent() {
        when(lockService.tryLock(anyString(), anyString())).thenReturn(LOCK_VAL);
        SupervisorAgentService service = createService(Collections.emptyList());

        ChatResult expected = new ChatResult(1L, "response");
        when(chatComponent.chat(any(ReActAgent.class), any(), any(ChatContext.class), anyString()))
                .thenReturn(expected);

        ChatResult result = service.chat(makeContext("u1", "s1", "hello"));

        assertEquals(expected, result);
        verify(chatComponent).chat(any(ReActAgent.class), any(), any(ChatContext.class), anyString());
    }

    // ========== bypass logic ==========

    @Test
    void chat_bypassSingleSubagentResult() {
        when(lockService.tryLock(anyString(), anyString())).thenReturn(LOCK_VAL);
        SubAgent subAgent = createMockSubAgent("TestAgent", "test_tool", "desc");
        SupervisorAgentService service = createService(List.of(subAgent));

        // When chatComponent.chat() is called, populate the ACTUAL agent's memory
        // (the one created by buildSupervisorWithSession, not a mock)
        when(chatComponent.chat(any(ReActAgent.class), any(), any(ChatContext.class), anyString()))
                .thenAnswer(invocation -> {
                    ReActAgent actualAgent = invocation.getArgument(0);
                    Msg userMsg = Msg.builder().role(MsgRole.USER).textContent("hello").build();
                    ToolUseBlock toolUse = ToolUseBlock.builder()
                            .id("call_1").name("test_tool").input(java.util.Map.of()).build();
                    TextBlock resultText = TextBlock.builder().text("sub-agent output").build();
                    ToolResultBlock toolResult = ToolResultBlock.builder()
                            .id("call_1").name("test_tool").output(List.of(resultText)).build();
                    Msg assistantMsg = Msg.builder().role(MsgRole.ASSISTANT)
                            .content(List.of(toolUse, toolResult)).build();
                    actualAgent.getMemory().addMessage(userMsg);
                    actualAgent.getMemory().addMessage(assistantMsg);
                    return new ChatResult(1L, "original");
                });

        ChatResult result = service.chat(makeContext("u1", "s1", "hello"));

        // Bypass should return sub-agent text, not the original
        assertEquals("sub-agent output", result.responseText());
        verify(messageService).updateAgentOutput(1L, "sub-agent output", MessageStatus.SUCCESS);
        verify(lockService).unlock("u1", "s1", LOCK_VAL);
    }

    @Test
    void chat_noBypass_multipleToolCalls() {
        when(lockService.tryLock(anyString(), anyString())).thenReturn(LOCK_VAL);
        SubAgent subAgent = createMockSubAgent("TestAgent", "test_tool", "desc");
        SupervisorAgentService service = createService(List.of(subAgent));

        when(chatComponent.chat(any(ReActAgent.class), any(), any(ChatContext.class), anyString()))
                .thenAnswer(invocation -> {
                    ReActAgent actualAgent = invocation.getArgument(0);
                    Msg userMsg = Msg.builder().role(MsgRole.USER).textContent("hello").build();

                    ToolUseBlock toolUse1 = ToolUseBlock.builder()
                            .id("call_1").name("test_tool").input(java.util.Map.of()).build();
                    TextBlock text1 = TextBlock.builder().text("result 1").build();
                    ToolResultBlock toolResult1 = ToolResultBlock.builder()
                            .id("call_1").name("test_tool").output(List.of(text1)).build();

                    ToolUseBlock toolUse2 = ToolUseBlock.builder()
                            .id("call_2").name("test_tool").input(java.util.Map.of()).build();
                    TextBlock text2 = TextBlock.builder().text("result 2").build();
                    ToolResultBlock toolResult2 = ToolResultBlock.builder()
                            .id("call_2").name("test_tool").output(List.of(text2)).build();

                    Msg assistantMsg = Msg.builder().role(MsgRole.ASSISTANT)
                            .content(List.of(toolUse1, toolResult1, toolUse2, toolResult2)).build();
                    Msg supervisorReply = Msg.builder().role(MsgRole.ASSISTANT)
                            .textContent("supervisor summary").build();

                    actualAgent.getMemory().addMessage(userMsg);
                    actualAgent.getMemory().addMessage(assistantMsg);
                    actualAgent.getMemory().addMessage(supervisorReply);
                    return new ChatResult(1L, "supervisor summary");
                });

        ChatResult result = service.chat(makeContext("u1", "s1", "hello"));

        // Multiple tool calls → no bypass → return original
        assertEquals("supervisor summary", result.responseText());
        verify(lockService).unlock("u1", "s1", LOCK_VAL);
    }

    @Test
    void chat_noBypass_directResponse() {
        when(lockService.tryLock(anyString(), anyString())).thenReturn(LOCK_VAL);
        SupervisorAgentService service = createService(Collections.emptyList());

        when(chatComponent.chat(any(ReActAgent.class), any(), any(ChatContext.class), anyString()))
                .thenAnswer(invocation -> {
                    ReActAgent actualAgent = invocation.getArgument(0);
                    Msg userMsg = Msg.builder().role(MsgRole.USER).textContent("hello").build();
                    Msg reply = Msg.builder().role(MsgRole.ASSISTANT).textContent("direct reply").build();
                    actualAgent.getMemory().addMessage(userMsg);
                    actualAgent.getMemory().addMessage(reply);
                    return new ChatResult(1L, "direct reply");
                });

        ChatResult result = service.chat(makeContext("u1", "s1", "hello"));

        // No tool calls → no bypass → return original
        assertEquals("direct reply", result.responseText());
        verify(lockService).unlock("u1", "s1", LOCK_VAL);
    }

    // ========== helpers ==========

    private ChatContext makeContext(String userId, String sessionId, String message) {
        return new ChatContext(sessionId, userId, message, MessageChannel.HTTP, "test", null, null);
    }

    private SubAgent createMockSubAgent(String name, String toolName, String description) {
        return new SubAgent() {
            @Override
            public ReActAgent provide() {
                return ReActAgent.builder()
                        .name(name)
                        .model(model)
                        .memory(new InMemoryMemory())
                        .build();
            }

            @Override
            public String getAgentName() { return name; }

            @Override
            public String getToolName() { return toolName; }

            @Override
            public String getDescription() { return description; }

            @Override
            public Integer getMaxMessageLength() { return 20; }

            @Override
            public Duration getTimeWindow() { return Duration.ofMinutes(30); }
        };
    }
}
