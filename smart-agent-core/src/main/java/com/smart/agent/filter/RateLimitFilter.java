package com.smart.agent.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * API rate limiting filter.
 *
 * @description Implements API rate limiting using a Redis fixed-window counter with Lua-based atomic
 *              increment-and-expire. Each client IP is allowed a configurable number of requests per minute.
 *              Only activated when redis.url is configured; no rate limiting is applied otherwise.
 *              Rate limiting only applies to /api/agent/chat and /api/agent/chat/stream endpoints;
 *              other endpoints are unaffected.
 *
 *              When behind a reverse proxy, set {@code ratelimit.trusted-proxy-header=true} to trust
 *              the X-Forwarded-For header. Without this setting, only {@code X-Real-IP} and the remote
 *              address are used to prevent spoofing.
 * @author Jiangbo Li
 * @date 2026-06-16
 * @version 1.0
 */
@Slf4j
@Order(2)
@Component
@ConditionalOnProperty(prefix = "redis", name = "host")
public class RateLimitFilter implements Filter {

    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:";

    /**
     * Lua script for atomic increment-and-expire: increments the counter and sets TTL in one
     * atomic operation, avoiding the non-atomic INCR+EXPIRE race condition.
     */
    private static final String RATE_LIMIT_SCRIPT =
            "local current = redis.call('incr', KEYS[1]) " +
            "if current == 1 then redis.call('expire', KEYS[1], ARGV[1]) end " +
            "return current";

    private static final DefaultRedisScript<Long> RATE_LIMIT_REDIS_SCRIPT =
            new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);

    @Value("${ratelimit.requests-per-minute:30}")
    private int maxRequestsPerMinute;

    /** When true, trust the first IP in X-Forwarded-For (set when behind a known reverse proxy). */
    @Value("${ratelimit.trusted-proxy-header:false}")
    private boolean trustForwardedFor;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {
        if (redisTemplate == null) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String path = request.getRequestURI();

        if (!path.startsWith("/api/")) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        String clientIp = extractClientIp(request);
        long windowIndex = System.currentTimeMillis() / 60000;
        String key = RATE_LIMIT_KEY_PREFIX + clientIp + ":" + windowIndex;

        try {
            Long count = redisTemplate.execute(RATE_LIMIT_REDIS_SCRIPT, List.of(key), "60");

            if (count != null && count > maxRequestsPerMinute) {
                HttpServletResponse response = (HttpServletResponse) servletResponse;
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":-1,\"msg\":\"Too many requests, please try again later\",\"ts\":"
                        + (System.currentTimeMillis() / 1000L) + ",\"data\":null}");
                log.warn("Rate limit exceeded for IP={}, count={}", clientIp, count);
                return;
            }
        } catch (Exception e) {
            log.warn("Rate limit check failed, allow request: {}", e.getMessage());
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private String extractClientIp(HttpServletRequest request) {
        // Only trust X-Forwarded-For when explicitly configured behind a known reverse proxy
        if (trustForwardedFor) {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        String ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
