package com.smart.agent.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for domain exceptions.
 */
class DomainExceptionTest {

    @Test
    void sessionBusyException_carriesMessage() {
        SessionBusyException ex = new SessionBusyException("该会话正在处理中");
        assertEquals("该会话正在处理中", ex.getMessage());
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void rateLimitExceededException_carriesMessage() {
        RateLimitExceededException ex = new RateLimitExceededException("Too many requests");
        assertEquals("Too many requests", ex.getMessage());
        assertInstanceOf(RuntimeException.class, ex);
    }
}
