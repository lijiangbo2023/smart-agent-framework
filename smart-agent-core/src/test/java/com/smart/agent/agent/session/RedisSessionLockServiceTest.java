package com.smart.agent.agent.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RedisSessionLockService.
 */
class RedisSessionLockServiceTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RedisSessionLockService lockService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lockService = new RedisSessionLockService(redisTemplate);
    }

    @Test
    void tryLock_setIfAbsentTrue_returnsLockValue() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        String result = lockService.tryLock("user1", "session1");

        assertNotNull(result, "Should return lock value on success");
        verify(valueOps).setIfAbsent(eq("session:lock:user1::session1"), anyString(), any(Duration.class));
    }

    @Test
    void tryLock_setIfAbsentFalse_returnsNull() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        String result = lockService.tryLock("user1", "session1");

        assertNull(result, "Should return null when lock is held by another");
    }

    @Test
    void tryLock_redisException_returnsNull_failClosed() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        String result = lockService.tryLock("user1", "session1");

        assertNull(result, "Should fail closed (return null) on Redis error");
    }

    @Test
    void unlock_withNullLockValue_isNoOp() {
        assertDoesNotThrow(() -> lockService.unlock("user1", "session1", null));
        verify(redisTemplate, never()).execute(any(), anyList(), any());
    }

    @Test
    void unlock_executesLuaScript() {
        lockService.unlock("user1", "session1", "my-lock-value");

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("session:lock:user1::session1")),
                eq("my-lock-value")
        );
    }

    @Test
    void unlock_redisException_doesNotThrow() {
        when(redisTemplate.execute(any(), anyList(), any()))
                .thenThrow(new RuntimeException("Redis error"));

        assertDoesNotThrow(() -> lockService.unlock("user1", "session1", "lock-value"));
    }
}
