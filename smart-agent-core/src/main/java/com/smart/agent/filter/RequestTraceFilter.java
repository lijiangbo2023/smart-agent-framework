package com.smart.agent.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Request trace filter.
 *
 * @description Servlet filter that generates or extracts a traceId for each HTTP request and places it
 *              in the MDC. Supports request elapsed-time logging.
 *              Client-provided traceIds are validated against a strict alphanumeric pattern to prevent
 *              HTTP response header injection and log injection attacks.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Order(1)
@Component
@Slf4j
public class RequestTraceFilter implements Filter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("access");

    /** Whitelist pattern: only alphanumeric, dash, underscore, dot. Max 64 chars. */
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[a-zA-Z0-9\\-_.]{1,64}");

    /**
     * Initialize the filter.
     *
     * @description Filter initialization callback, logs initialization event
     * @param filterConfig filter configuration object
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("RequestTraceFilter initialized");
    }

    /**
     * Execute filter logic.
     *
     * @description Extract or auto-generate a traceId from the request header, set it in the MDC and
     *              response header, and log the access request
     * @param servletRequest request object
     * @param servletResponse response object
     * @param filterChain filter chain
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId != null && !traceId.isEmpty() && TRACE_ID_PATTERN.matcher(traceId).matches()) {
            // Client-provided traceId is valid, use it
        } else {
            // Generate a new traceId (client header was missing, empty, or contained unsafe chars)
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            ACCESS_LOG.info("{} {} {} {}ms {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsed,
                    traceId);
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
