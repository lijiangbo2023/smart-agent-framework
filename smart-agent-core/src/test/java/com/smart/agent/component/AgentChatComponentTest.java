package com.smart.agent.component;

import com.smart.agent.constant.enums.MessageChannel;
import com.smart.agent.constant.enums.MessageStatus;
import com.smart.agent.model.ChatContext;
import com.smart.agent.model.ChatResult;
import com.smart.agent.model.ChatStreamResult;
import com.smart.agent.service.AgentChatMessageService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentChatComponentTest {

    private AgentChatMessageService messageService;
    private AgentChatComponent component;
    private ReActAgent agent;
    private SessionManager sessionManager;
    private ChatContext context;

    @BeforeEach
    void setUp() {
        messageService = mock(AgentChatMessageService.class);
        component = new AgentChatComponent(messageService, 0);
        agent = mock(ReActAgent.class);
        sessionManager = mock(SessionManager.class);
        context = new ChatContext("s1", "u1", "hello",
                MessageChannel.HTTP, "test", null, null);
    }

    // ========== chat() ==========

    @Test
    void chat_happyPath() {
        when(messageService.saveUserInput(anyString(), anyString(), anyString(),
                any(), anyString(), any(), any())).thenReturn(1L);

        Msg response = Msg.builder().role(MsgRole.ASSISTANT).textContent("hi there").build();
        when(agent.call(any(Msg.class))).thenReturn(Mono.just(response));

        ChatResult result = component.chat(agent, sessionManager, context, "fallback");

        assertEquals(1L, result.messageId());
        assertEquals("hi there", result.responseText());
        verify(messageService).updateAgentOutput(1L, "hi there", MessageStatus.SUCCESS);
        verify(sessionManager).saveSession();
    }

    @Test
    void chat_agentThrows_returnsFallback() {
        when(messageService.saveUserInput(anyString(), anyString(), anyString(),
                any(), anyString(), any(), any())).thenReturn(2L);
        when(agent.call(any(Msg.class))).thenReturn(Mono.error(new RuntimeException("LLM error")));

        ChatResult result = component.chat(agent, sessionManager, context, "fallback text");

        assertEquals(2L, result.messageId());
        assertEquals("fallback text", result.responseText());
        verify(messageService).updateAgentOutput(2L, null, MessageStatus.ERROR);
    }

    @Test
    void chat_nullResponse_returnsFallback() {
        when(messageService.saveUserInput(anyString(), anyString(), anyString(),
                any(), anyString(), any(), any())).thenReturn(3L);
        when(agent.call(any(Msg.class))).thenReturn(Mono.justOrEmpty(null));

        ChatResult result = component.chat(agent, sessionManager, context, "no response");

        assertEquals(3L, result.messageId());
        assertEquals("no response", result.responseText());
        verify(messageService).updateAgentOutput(3L, "no response", MessageStatus.SUCCESS);
    }

    @Test
    void chat_savesUserInputFirst() {
        when(messageService.saveUserInput(anyString(), anyString(), anyString(),
                any(), anyString(), any(), any())).thenReturn(10L);
        Msg response = Msg.builder().role(MsgRole.ASSISTANT).textContent("ok").build();
        when(agent.call(any(Msg.class))).thenReturn(Mono.just(response));

        component.chat(agent, sessionManager, context, "fallback");

        var inOrder = inOrder(messageService);
        inOrder.verify(messageService).saveUserInput(
                eq("s1"), eq("u1"), eq("hello"),
                eq(MessageChannel.HTTP), eq("test"), isNull(), isNull());
        inOrder.verify(messageService).updateAgentOutput(eq(10L), eq("ok"), eq(MessageStatus.SUCCESS));
    }

    // ========== chatStream() ==========

    @Test
    void chatStream_happyPath() {
        when(messageService.saveUserInput(anyString(), anyString(), anyString(),
                any(), anyString(), any(), any())).thenReturn(5L);

        Msg msg = Msg.builder().role(MsgRole.ASSISTANT).textContent("streaming text").build();
        Event event = new Event(EventType.AGENT_RESULT, msg, true);
        when(agent.stream(anyList(), any(StreamOptions.class))).thenReturn(Flux.just(event));

        ChatStreamResult result = component.chatStream(agent, sessionManager, context,
                e -> e.getMessage() != null ? e.getMessage().getTextContent() : null);

        assertEquals(5L, result.messageId());
        assertNotNull(result.eventStream());

        StepVerifier.create(result.eventStream())
                .expectNext(event)
                .verifyComplete();

        verify(sessionManager, timeout(1000)).saveSession();
        verify(messageService, timeout(1000)).updateAgentOutput(eq(5L), anyString(), eq(MessageStatus.SUCCESS));
    }

    @Test
    void chatStream_errorPath() {
        when(messageService.saveUserInput(anyString(), anyString(), anyString(),
                any(), anyString(), any(), any())).thenReturn(6L);

        RuntimeException error = new RuntimeException("stream error");
        when(agent.stream(anyList(), any(StreamOptions.class))).thenReturn(Flux.error(error));

        ChatStreamResult result = component.chatStream(agent, sessionManager, context,
                e -> "text");

        StepVerifier.create(result.eventStream())
                .expectError(RuntimeException.class)
                .verify();

        verify(messageService, timeout(1000)).updateAgentOutput(eq(6L), anyString(), eq(MessageStatus.ERROR));
    }

    @Test
    void chatStream_emptyStream() {
        when(messageService.saveUserInput(anyString(), anyString(), anyString(),
                any(), anyString(), any(), any())).thenReturn(7L);

        when(agent.stream(anyList(), any(StreamOptions.class))).thenReturn(Flux.empty());

        ChatStreamResult result = component.chatStream(agent, sessionManager, context,
                e -> "text");

        StepVerifier.create(result.eventStream())
                .verifyComplete();

        verify(sessionManager, timeout(1000)).saveSession();
        verify(messageService, timeout(1000)).updateAgentOutput(eq(7L), eq(""), eq(MessageStatus.SUCCESS));
    }
}
