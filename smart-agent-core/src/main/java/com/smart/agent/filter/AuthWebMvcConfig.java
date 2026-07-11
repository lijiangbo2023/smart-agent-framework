package com.smart.agent.filter;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the API key authentication interceptor for all /api/** endpoints.
 *
 * @author Jiangbo Li
 * @date 2026-06-18
 * @version 1.0
 */
@Configuration
public class AuthWebMvcConfig implements WebMvcConfigurer {

    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;

    public AuthWebMvcConfig(ApiKeyAuthInterceptor apiKeyAuthInterceptor) {
        this.apiKeyAuthInterceptor = apiKeyAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyAuthInterceptor)
                .addPathPatterns("/api/**");
    }
}
