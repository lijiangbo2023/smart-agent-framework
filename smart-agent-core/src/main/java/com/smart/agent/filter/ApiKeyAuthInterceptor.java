package com.smart.agent.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * API key authentication interceptor.
 *
 * @description Validates the {@code X-Api-Key} header against the configured API key.
 *              When auth is disabled (default for development), all requests are allowed.
 *              Enable via {@code auth.api-key.enabled=true} and set {@code auth.api-key.value}.
 *              Uses constant-time comparison to prevent timing attacks.
 *              Also extracts the {@code X-User-Id} header and stores it as a request attribute
 *              ({@code authenticatedUserId}) for downstream ownership validation. When auth is
 *              enabled, the header is required; when disabled, it is optional.
 * @author Jiangbo Li
 * @date 2026-06-18
 * @version 1.0
 */
@Slf4j
@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String USER_ID_HEADER = "X-User-Id";
    public static final String AUTHENTICATED_USER_ID_ATTR = "authenticatedUserId";

    @Value("${auth.api-key.enabled:false}")
    private boolean enabled;

    @Value("${auth.api-key.value:}")
    private String apiKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // Extract X-User-Id header and store as request attribute for downstream validation
        String userId = request.getHeader(USER_ID_HEADER);

        if (!enabled) {
            // Auth disabled: userId is optional, store if present
            if (userId != null && !userId.isEmpty()) {
                request.setAttribute(AUTHENTICATED_USER_ID_ATTR, userId);
            }
            return true;
        }

        // Auth enabled: validate API key first
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("Auth enabled but auth.api-key.value is not set, rejecting request");
            writeUnauthorizedResponse(response, "API key not configured");
            return false;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (!constantTimeEquals(apiKey, providedKey)) {
            log.warn("Unauthorized API access attempt, uri={}, remoteAddr={}",
                    request.getRequestURI(), request.getRemoteAddr());
            writeUnauthorizedResponse(response, "Invalid or missing API key");
            return false;
        }

        // Auth enabled: X-User-Id is required
        if (userId == null || userId.isEmpty()) {
            log.warn("Missing X-User-Id header, uri={}", request.getRequestURI());
            writeUnauthorizedResponse(response, "Missing X-User-Id header");
            return false;
        }
        request.setAttribute(AUTHENTICATED_USER_ID_ATTR, userId);
        return true;
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
