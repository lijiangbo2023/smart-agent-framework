package com.smart.agent.agent.session;

/**
 * Session lock service interface
 *
 * @description Prevents concurrent requests for the same user+session from overwriting each other's Session data.
 *              The framework provides two implementations:
 *              <ul>
 *                <li>{@code InMemorySessionLockService} — in-memory lock for single instance (default, no Redis required)</li>
 *                <li>{@code RedisSessionLockService} — Redis distributed lock (auto-activated when redis.url is configured)</li>
 *              </ul>
 * @author Jiangbo Li
 * @date 2026-06-16
 * @version 1.0
 */
public interface SessionLockService {

    /**
     * Attempts to acquire the session lock
     *
     * @param userId user ID
     * @param sessionId session ID
     * @return a non-null lock value if lock acquired successfully, null if the session is being
     *         processed by another request. The returned value MUST be passed to
     *         {@link #unlock(String, String, String)} for safe release.
     */
    String tryLock(String userId, String sessionId);

    /**
     * Releases the session lock using the lock value returned by {@link #tryLock}.
     *
     * @param userId user ID
     * @param sessionId session ID
     * @param lockValue the lock value returned by tryLock, ensures only the holder can release
     */
    void unlock(String userId, String sessionId, String lockValue);
}
