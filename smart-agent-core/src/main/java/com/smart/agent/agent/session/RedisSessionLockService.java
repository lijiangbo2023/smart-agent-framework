package com.smart.agent.agent.session;

import com.smart.agent.util.RedisLockUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis distributed session lock implementation
 *
 * @description Uses Redis SETNX + TTL and Lua-based atomic unlock to implement cross-instance
 *              distributed session locking. Auto-activated when redis.url is configured,
 *              replacing InMemorySessionLockService. Locks expire automatically via TTL.
 * @author Jiangbo Li
 * @date 2026-06-16
 * @version 1.0
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "redis", name = "host")
public class RedisSessionLockService implements SessionLockService {

    private static final String LOCK_KEY_PREFIX = "session:lock:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisSessionLockService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String tryLock(String userId, String sessionId) {
        String key = LOCK_KEY_PREFIX + userId + "::" + sessionId;
        String value = UUID.randomUUID().toString();
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, LOCK_TTL);
            if (Boolean.TRUE.equals(result)) {
                return value;
            }
            return null;
        } catch (Exception e) {
            log.warn("Redis lock tryLock failed, failing closed: userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getMessage());
            return null;
        }
    }

    @Override
    public void unlock(String userId, String sessionId, String lockValue) {
        if (lockValue == null) return;
        String key = LOCK_KEY_PREFIX + userId + "::" + sessionId;
        try {
            RedisLockUtils.unlock(redisTemplate, key, lockValue);
        } catch (Exception e) {
            log.warn("Redis lock unlock failed, key={}: {}", key, e.getMessage());
        }
    }
}
