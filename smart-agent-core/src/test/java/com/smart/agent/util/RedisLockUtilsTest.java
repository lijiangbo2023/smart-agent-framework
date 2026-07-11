package com.smart.agent.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RedisLockUtils.
 */
class RedisLockUtilsTest {

    @Test
    void unlock_withNullLockValue_isNoOp() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);

        RedisLockUtils.unlock(redisTemplate, "key", null);

        verify(redisTemplate, never()).execute(any(), anyList(), any());
    }

    @Test
    void unlock_executesLuaScriptWithCorrectArgs() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);

        RedisLockUtils.unlock(redisTemplate, "my-lock-key", "my-lock-value");

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("my-lock-key")),
                eq("my-lock-value")
        );
    }

    @Test
    void unlock_multipleCallsEachExecuteScript() {
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);

        RedisLockUtils.unlock(redisTemplate, "key1", "val1");
        RedisLockUtils.unlock(redisTemplate, "key2", "val2");

        verify(redisTemplate).execute(any(DefaultRedisScript.class), eq(List.of("key1")), eq("val1"));
        verify(redisTemplate).execute(any(DefaultRedisScript.class), eq(List.of("key2")), eq("val2"));
    }
}
