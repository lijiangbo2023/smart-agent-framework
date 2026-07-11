package com.smart.agent.dingtalk;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.smart.agent.nacos.SystemConfigManager;
import com.smart.agent.util.RedisLockUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * DingTalk access token service.
 *
 * @description Responsible for obtaining and caching DingTalk access tokens with automatic
 *              pre-expiry refresh to ensure token availability. When Redis is configured,
 *              tokens are shared across instances via Redis and refresh operations use a
 *              Redis distributed lock to guarantee single-point refresh, avoiding DingTalk
 *              API rate-limiting caused by concurrent refresh in multi-instance deployments.
 *              Falls back to local in-memory cache when Redis is not configured.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Service
public class DingTalkAccessTokenService {

    private static final String GET_TOKEN_URL = "https://oapi.dingtalk.com/gettoken";
    private static final String REDIS_TOKEN_KEY = "dingtalk:access_token";
    private static final String REDIS_LOCK_KEY = "dingtalk:token:refresh:lock";
    private static final long REFRESH_AHEAD_MILLIS = 5 * 60 * 1000L;

    /** Max number of retry attempts when waiting for another instance to refresh the token. */
    private static final int LOCK_WAIT_MAX_RETRIES = 3;
    private static final long LOCK_WAIT_BASE_MILLIS = 500L;

    private final SystemConfigManager systemConfigManager;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile TokenCache cache;

    public DingTalkAccessTokenService(SystemConfigManager systemConfigManager) {
        this.systemConfigManager = systemConfigManager;
    }

    /**
     * Get DingTalk access token.
     *
     * @description Prioritizes reading the token from Redis (shared across instances); falls back
     *              to local in-memory cache on Redis miss. If both caches are empty or the token
     *              is about to expire, calls the DingTalk API to refresh and writes the new token
     *              to both Redis and local cache. In multi-instance environments, the refresh
     *              operation is protected by a Redis distributed lock so that only one instance
     *              performs the refresh globally.
     *              Uses double-checked locking: fast path reads without synchronization.
     * @return a valid DingTalk access token string
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public String getAccessToken() {
        // Fast path: try Redis first (no lock)
        if (redisTemplate != null) {
            try {
                String token = redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
                if (token != null && !token.isEmpty()) {
                    return token;
                }
            } catch (Exception e) {
                log.warn("Redis get token failed: {}", e.getMessage());
            }
        }

        // Fast path: check local cache (no lock)
        TokenCache current = cache;
        long now = System.currentTimeMillis();
        if (current != null && current.expireAt - REFRESH_AHEAD_MILLIS > now) {
            return current.token;
        }

        // Slow path: refresh with lock
        return refresh();
    }

    private synchronized String refresh() {
        // Double-check: another thread may have refreshed while we waited for the lock
        if (redisTemplate != null) {
            try {
                String token = redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
                if (token != null && !token.isEmpty()) {
                    return token;
                }
            } catch (Exception e) {
                log.warn("Redis get token (double-check) failed: {}", e.getMessage());
            }
        }
        TokenCache current = cache;
        long now = System.currentTimeMillis();
        if (current != null && current.expireAt - REFRESH_AHEAD_MILLIS > now) {
            return current.token;
        }

        // Try distributed lock if Redis available
        String lockValue = null;
        if (redisTemplate != null) {
            lockValue = UUID.randomUUID().toString();
            try {
                Boolean locked = redisTemplate.opsForValue().setIfAbsent(REDIS_LOCK_KEY, lockValue, 10, TimeUnit.SECONDS);
                if (!Boolean.TRUE.equals(locked)) {
                    // Another instance is refreshing, exponential backoff wait
                    String token = waitForTokenFromRedis();
                    if (token != null) {
                        return token;
                    }
                }
            } catch (Exception e) {
                log.warn("Redis lock failed, proceed with local refresh: {}", e.getMessage());
                lockValue = null;
            }
        }

        try {
            SystemConfigManager.DingTalkConfig dingtalk = systemConfigManager.getSystemConfig().getDingtalk();
            String appKey = dingtalk != null ? dingtalk.getAppKey() : null;
            String appSecret = dingtalk != null ? dingtalk.getAppSecret() : null;
            if (appKey == null || appKey.isEmpty() || appSecret == null || appSecret.isEmpty()) {
                throw new IllegalStateException("DingTalk appKey/appSecret not configured");
            }
            String url = GET_TOKEN_URL
                    + "?appkey=" + URLEncoder.encode(appKey, StandardCharsets.UTF_8)
                    + "&appsecret=" + URLEncoder.encode(appSecret, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject body = JSON.parseObject(response.body());
            Integer errcode = body.getInteger("errcode");
            if (errcode == null || errcode != 0) {
                String errmsg = body.getString("errmsg");
                throw new IllegalStateException("Get access_token failed, errcode=" + errcode + ", errmsg=" + errmsg);
            }
            String token = body.getString("access_token");
            int expiresIn = body.getIntValue("expires_in");
            long expireAt = System.currentTimeMillis() + expiresIn * 1000L;

            // Save to local cache
            cache = new TokenCache(token, expireAt);

            // Save to Redis
            if (redisTemplate != null) {
                try {
                    long redisTtlSeconds = expiresIn - 300; // 5 minutes buffer
                    if (redisTtlSeconds > 0) {
                        redisTemplate.opsForValue().set(REDIS_TOKEN_KEY, token, redisTtlSeconds, TimeUnit.SECONDS);
                    }
                } catch (Exception e) {
                    log.warn("Redis set token failed: {}", e.getMessage());
                }
            }

            log.info("Refreshed DingTalk access_token, expiresIn={}s", expiresIn);
            return token;
        } catch (Exception e) {
            throw new IllegalStateException("DingTalk gettoken failed", e);
        } finally {
            // Release lock atomically via shared utility
            if (redisTemplate != null && lockValue != null) {
                try {
                    RedisLockUtils.unlock(redisTemplate, REDIS_LOCK_KEY, lockValue);
                } catch (Exception e) {
                    log.warn("Redis unlock failed: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Wait for another instance to finish refreshing and read the token from Redis.
     * Uses exponential backoff: 500ms, 1000ms, 2000ms.
     *
     * @return the token if available, null if all retries exhausted
     */
    private String waitForTokenFromRedis() {
        for (int attempt = 0; attempt < LOCK_WAIT_MAX_RETRIES; attempt++) {
            long waitMillis = LOCK_WAIT_BASE_MILLIS * (1L << attempt); // 500, 1000, 2000
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
            try {
                String token = redisTemplate.opsForValue().get(REDIS_TOKEN_KEY);
                if (token != null && !token.isEmpty()) {
                    return token;
                }
            } catch (Exception e) {
                log.warn("Redis get token (wait retry {}) failed: {}", attempt, e.getMessage());
            }
        }
        return null;
    }

    private record TokenCache(String token, long expireAt) {
    }
}
