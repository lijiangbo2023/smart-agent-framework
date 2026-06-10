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

/**
 * 请求链路追踪过滤器
 *
 * @description Servlet过滤器，为每个HTTP请求生成或提取traceId并放入MDC，支持请求耗时日志记录
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

    /**
     * 初始化过滤器
     *
     * @description 过滤器初始化回调，记录初始化日志
     * @param filterConfig 过滤器配置对象
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("RequestTraceFilter initialized");
    }

    /**
     * 执行过滤逻辑
     *
     * @description 从请求头提取或自动生成traceId，设置到MDC和响应头中，并记录请求访问日志
     * @param servletRequest 请求对象
     * @param servletResponse 响应对象
     * @param filterChain 过滤器链
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
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

    /**
     * 销毁过滤器
     *
     * @description 过滤器销毁回调，执行资源清理
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public void destroy() {
    }
}
