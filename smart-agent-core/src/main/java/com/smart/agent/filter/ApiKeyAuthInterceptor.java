package com.smart.agent.filter;

import com.smart.agent.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Authentication interceptor supporting both API Key and JWT Bearer Token.
 *
 * @description Validates requests via X-Api-Key header or Authorization Bearer token.
 *              Extracts userId from JWT or X-User-Id header for downstream ownership validation.
 * @author Jiangbo Li
 * @date 2026-06-18
 * @version 2.0
 */
@Slf4j
@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String AUTH_HEADER = "Authorization";
    public static final String AUTHENTICATED_USER_ID_ATTR = "authenticatedUserId";

    @Value("${auth.api-key.enabled:false}")
    private boolean enabled;

    @Value("${auth.api-key.value:}")
    private String apiKey;

    @Autowired(required = false)
    private JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // Try JWT Bearer token first
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith("Bearer ") && jwtUtils != null) {
            String token = authHeader.substring(7);
            if (jwtUtils.validateToken(token)) {
                String userId = jwtUtils.getUserIdFromToken(token);
                request.setAttribute(AUTHENTICATED_USER_ID_ATTR, userId);
                return true;
            }
        }

        // Try X-User-Id header (simple mode, for dev or when auth is disabled)
        String userId = request.getHeader(USER_ID_HEADER);

        if (!enabled) {
            if (userId != null && !userId.isEmpty()) {
                request.setAttribute(AUTHENTICATED_USER_ID_ATTR, userId);
            }
            return true;
        }

        // Auth enabled: validate API key
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("Auth enabled but auth.api-key.value is not set, rejecting request");
            writeUnauthorizedResponse(response, "API key not configured");
            return false;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (!constantTimeEquals(apiKey, providedKey)) {
            log.warn("Unauthorized API access attempt, uri={}", request.getRequestURI());
            writeUnauthorizedResponse(response, "Invalid or missing API key");
            return false;
        }

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
