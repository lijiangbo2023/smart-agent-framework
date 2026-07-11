package com.smart.agent.agent.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session lock implementation (default)
 *
 * @description Uses ConcurrentHashMap.compute() for atomic lock operations, suitable for single-node
 *              deployment. Auto-activated when Redis is not configured. For multi-instance deployment,
 *              configure redis.url to enable RedisSessionLockService.
 * @author Jiangbo Li
 * @date 2026-06-16
 * @version 1.0
 */
@Slf4j
@Service
@ConditionalOnMissingBean(RedisSessionLockService.class)
public class InMemorySessionLockService implements SessionLockService {

    private static final long DEFAULT_LOCK_TIMEOUT_MS = 10 * 60 * 1000L;

    private final ConcurrentHashMap<String, LockEntry> activeSessions = new ConcurrentHashMap<>();

    @Override
    public String tryLock(String userId, String sessionId) {
        String key = buildKey(userId, sessionId);
        String newLockValue = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        LockEntry result = activeSessions.compute(key, (k, existing) -> {
            if (existing == null || now > existing.expireAt) {
                return new LockEntry(now + DEFAULT_LOCK_TIMEOUT_MS, newLockValue);
            }
            return existing;
        });

        if (result != null && newLockValue.equals(result.lockValue)) {
            return newLockValue;
        }
        return null;
    }

    @Override
    public void unlock(String userId, String sessionId, String lockValue) {
        if (lockValue == null) return;
        String key = buildKey(userId, sessionId);
        activeSessions.computeIfPresent(key, (k, existing) -> {
            if (lockValue.equals(existing.lockValue)) {
                return null;
            }
            return existing;
        });
    }

    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredLocks() {
        long now = System.currentTimeMillis();
        int before = activeSessions.size();
        activeSessions.entrySet().removeIf(e -> now > e.getValue().expireAt);
        int removed = before - activeSessions.size();
        if (removed > 0) {
            log.info("Cleaned up {} expired session locks", removed);
        }
    }

    private String buildKey(String userId, String sessionId) {
        return userId + "::" + sessionId;
    }

    private record LockEntry(long expireAt, String lockValue) {}
}
