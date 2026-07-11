package com.smart.agent.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Global CORS configuration.
 *
 * @description Controls cross-origin policy via configuration properties. In local profile,
 *              all origins are allowed by default. In non-local profiles, origins must be
 *              explicitly configured via {@code cors.allowed-origins}; the wildcard '*' is
 *              rejected in production to prevent CSRF attacks.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    private final Environment springEnvironment;

    public CorsConfig(Environment springEnvironment) {
        this.springEnvironment = springEnvironment;
    }

    /**
     * Validate CORS configuration security on startup.
     *
     * @description In non-local profiles, the wildcard '*' is rejected and the application
     *              will fail to start if cors.allowed-origins is not explicitly configured.
     * @author Jiangbo Li
     * @date 2026-06-18
     */
    @jakarta.annotation.PostConstruct
    public void validate() {
        boolean isLocal = Arrays.asList(springEnvironment.getActiveProfiles()).contains("local");
        if (!isLocal) {
            if (allowedOrigins == null || allowedOrigins.isBlank()) {
                log.warn("cors.allowed-origins is not configured in profile [{}]. "
                        + "CORS will be disabled — only same-origin requests are allowed.",
                        String.join(",", springEnvironment.getActiveProfiles()));
            } else if (allowedOrigins.contains("*")) {
                throw new IllegalStateException(
                        "CORS wildcard '*' is not allowed in non-local profile ["
                        + String.join(",", springEnvironment.getActiveProfiles())
                        + "]. Configure specific origins via cors.allowed-origins.");
            }
        }
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                boolean isLocal = Arrays.asList(springEnvironment.getActiveProfiles()).contains("local");

                if (allowedOrigins == null || allowedOrigins.isBlank()) {
                    if (isLocal) {
                        registry.addMapping("/api/**")
                                .allowedOriginPatterns("*")
                                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                                .allowedHeaders("*")
                                .allowCredentials(false)
                                .maxAge(3600);
                    }
                    // Non-local with no config: no CORS mappings → same-origin only
                    return;
                }

                registry.addMapping("/api/**")
                        .allowedOriginPatterns(allowedOrigins.split(","))
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(!allowedOrigins.contains("*"))
                        .maxAge(3600);
            }
        };
    }
}
