package com.smart.agent.service;

import com.smart.agent.constant.enums.FeedbackType;
import com.smart.agent.persistence.entity.AgentChatMessageEntity;
import com.smart.agent.persistence.mapper.AgentChatMessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentChatMessageServiceTest {

    private AgentChatMessageMapper mapper;
    private AgentChatMessageService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AgentChatMessageMapper.class);
        service = new AgentChatMessageService(mapper);
    }

    // ========== updateFeedback: toggle logic ==========

    @Test
    void updateFeedback_like_fromNone() {
        when(mapper.updateById(any(AgentChatMessageEntity.class))).thenReturn(1);
        String result = service.updateFeedback(1L, "like", "none");
        assertEquals("like", result);
        verifyFeedbackType(FeedbackType.LIKE);
    }

    @Test
    void updateFeedback_dislike_fromNone() {
        when(mapper.updateById(any(AgentChatMessageEntity.class))).thenReturn(1);
        String result = service.updateFeedback(1L, "dislike", "none");
        assertEquals("dislike", result);
        verifyFeedbackType(FeedbackType.DISLIKE);
    }

    @Test
    void updateFeedback_like_fromLike_togglesOff() {
        when(mapper.updateById(any(AgentChatMessageEntity.class))).thenReturn(1);
        String result = service.updateFeedback(1L, "like", "like");
        assertEquals("none", result);
        verifyFeedbackType(FeedbackType.NONE);
    }

    @Test
    void updateFeedback_dislike_fromDislike_togglesOff() {
        when(mapper.updateById(any(AgentChatMessageEntity.class))).thenReturn(1);
        String result = service.updateFeedback(1L, "dislike", "dislike");
        assertEquals("none", result);
        verifyFeedbackType(FeedbackType.NONE);
    }

    @Test
    void updateFeedback_like_fromDislike_switchesToLike() {
        when(mapper.updateById(any(AgentChatMessageEntity.class))).thenReturn(1);
        String result = service.updateFeedback(1L, "like", "dislike");
        assertEquals("like", result);
        verifyFeedbackType(FeedbackType.LIKE);
    }

    @Test
    void updateFeedback_dislike_fromLike_switchesToDislike() {
        when(mapper.updateById(any(AgentChatMessageEntity.class))).thenReturn(1);
        String result = service.updateFeedback(1L, "dislike", "like");
        assertEquals("dislike", result);
        verifyFeedbackType(FeedbackType.DISLIKE);
    }

    // ========== updateFeedback: null messageId ==========

    @Test
    void updateFeedback_nullMessageId_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateFeedback(null, "like", "none"));
        verify(mapper, never()).updateById(any(AgentChatMessageEntity.class));
    }

    // ========== updateFeedback: DB error ==========

    @Test
    void updateFeedback_dbError_throwsRuntime() {
        when(mapper.updateById(any(AgentChatMessageEntity.class))).thenThrow(new RuntimeException("DB down"));
        assertThrows(RuntimeException.class, () -> service.updateFeedback(1L, "like", "none"));
    }

    // ========== updateAgentOutput ==========

    @Test
    void updateAgentOutput_nullMessageId_noDbCall() {
        service.updateAgentOutput(null, "output", com.smart.agent.constant.enums.MessageStatus.SUCCESS);
        verify(mapper, never()).updateById(any(AgentChatMessageEntity.class));
    }

    @Test
    void updateAgentOutput_dbError_noException() {
        when(mapper.updateById(any(AgentChatMessageEntity.class))).thenThrow(new RuntimeException("DB down"));
        assertDoesNotThrow(() ->
                service.updateAgentOutput(1L, "output", com.smart.agent.constant.enums.MessageStatus.SUCCESS));
    }

    // ========== updateFeedbackComment ==========

    @Test
    void updateFeedbackComment_nullMessageId_noDbCall() {
        service.updateFeedbackComment(null, "great");
        verify(mapper, never()).updateById(any(AgentChatMessageEntity.class));
    }

    @Test
    void updateFeedbackComment_dbError_throwsRuntime() {
        when(mapper.updateById(any(AgentChatMessageEntity.class))).thenThrow(new RuntimeException("DB down"));
        assertThrows(RuntimeException.class, () -> service.updateFeedbackComment(1L, "great"));
    }

    // ========== updateProcessQueryKey ==========

    @Test
    void updateProcessQueryKey_nullMessageId_noDbCall() {
        service.updateProcessQueryKey(null, "key");
        verify(mapper, never()).updateById(any(AgentChatMessageEntity.class));
    }

    @Test
    void updateProcessQueryKey_nullKey_noDbCall() {
        service.updateProcessQueryKey(1L, null);
        verify(mapper, never()).updateById(any(AgentChatMessageEntity.class));
    }

    // ========== helpers ==========

    private void verifyFeedbackType(FeedbackType expected) {
        ArgumentCaptor<AgentChatMessageEntity> captor = ArgumentCaptor.forClass(AgentChatMessageEntity.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(expected.getCode(), captor.getValue().getFeedbackType());
    }
}
