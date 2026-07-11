package com.smart.agent.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/**
 * Redis distributed lock utilities.
 *
 * @description Provides atomic lock operations using Lua scripts to ensure consistency
 *              in distributed environments.
 * @author Jiangbo Li
 * @date 2026-06-18
 * @version 1.0
 */
public final class RedisLockUtils {

    private RedisLockUtils() {
    }

    /**
     * Lua script for atomic unlock: only delete the key if the stored value matches the caller's
     * lock value. This prevents a stale unlock from deleting a lock acquired by another thread
     * after the original lock's TTL expired.
     */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else return 0 end";

    private static final DefaultRedisScript<Long> UNLOCK_REDIS_SCRIPT =
            new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    /**
     * Atomically unlock a Redis key by comparing the stored value with the provided lock value.
     *
     * @param redisTemplate Redis template
     * @param key the lock key
     * @param lockValue the lock value to compare
     */
    public static void unlock(RedisTemplate<String, String> redisTemplate, String key, String lockValue) {
        if (lockValue == null) return;
        redisTemplate.execute(UNLOCK_REDIS_SCRIPT, List.of(key), lockValue);
    }
}
