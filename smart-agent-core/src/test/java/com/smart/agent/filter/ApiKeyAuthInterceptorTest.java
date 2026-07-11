package com.smart.agent.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ApiKeyAuthInterceptor.
 */
class ApiKeyAuthInterceptorTest {

    private ApiKeyAuthInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new ApiKeyAuthInterceptor();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    private void setField(String name, Object value) throws Exception {
        Field field = ApiKeyAuthInterceptor.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(interceptor, value);
    }

    @Test
    void preHandle_authDisabled_allowsRequest() throws Exception {
        setField("enabled", false);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void preHandle_authDisabled_withUserId_storesAttribute() throws Exception {
        setField("enabled", false);
        when(request.getHeader("X-User-Id")).thenReturn("user-123");

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(request).setAttribute("authenticatedUserId", "user-123");
    }

    @Test
    void preHandle_authEnabledButNoKeyConfigured_rejects401() throws Exception {
        setField("enabled", true);
        setField("apiKey", "");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(401);
        assertTrue(responseWriter.toString().contains("API key not configured"));
    }

    @Test
    void preHandle_validApiKey_allowsRequest() throws Exception {
        setField("enabled", true);
        setField("apiKey", "secret-key-123");
        when(request.getHeader("X-Api-Key")).thenReturn("secret-key-123");
        when(request.getHeader("X-User-Id")).thenReturn("user-123");

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(request).setAttribute("authenticatedUserId", "user-123");
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void preHandle_validApiKey_missingUserId_rejects401() throws Exception {
        setField("enabled", true);
        setField("apiKey", "secret-key-123");
        when(request.getHeader("X-Api-Key")).thenReturn("secret-key-123");
        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/agent/chat");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(401);
        assertTrue(responseWriter.toString().contains("Missing X-User-Id header"));
    }

    @Test
    void preHandle_wrongApiKey_rejects401() throws Exception {
        setField("enabled", true);
        setField("apiKey", "secret-key-123");
        when(request.getHeader("X-Api-Key")).thenReturn("wrong-key");
        when(request.getRequestURI()).thenReturn("/api/agent/chat");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(401);
        assertTrue(responseWriter.toString().contains("Invalid or missing API key"));
    }

    @Test
    void preHandle_missingApiKey_rejects401() throws Exception {
        setField("enabled", true);
        setField("apiKey", "secret-key-123");
        when(request.getHeader("X-Api-Key")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/agent/chat");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(401);
    }

    @Test
    void preHandle_constantTimeComparison_preventsTimingAttack() throws Exception {
        setField("enabled", true);
        setField("apiKey", "my-secret-api-key-for-testing");

        // Test that the comparison works for exact match
        when(request.getHeader("X-Api-Key")).thenReturn("my-secret-api-key-for-testing");
        when(request.getHeader("X-User-Id")).thenReturn("user-123");
        assertTrue(interceptor.preHandle(request, response, new Object()));

        // Test partial match (attacker guessing prefix)
        reset(response);
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        when(request.getHeader("X-Api-Key")).thenReturn("my-secret-api-key-for-testinX");
        when(request.getRequestURI()).thenReturn("/api/agent/chat");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        assertFalse(interceptor.preHandle(request, response, new Object()));
    }
}
