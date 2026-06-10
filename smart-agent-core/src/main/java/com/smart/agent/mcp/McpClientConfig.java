package com.smart.agent.mcp;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP客户端配置类
 *
 * @description 负责创建和配置MCP（Model Context Protocol）客户端，通过SSE传输方式连接MCP服务端
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Configuration
public class McpClientConfig {

    @Value("${mcp.server.url:}")
    private String mcpServerUrl;

    @Value("${mcp.server.name:default-mcp}")
    private String mcpServerName;

    /**
     * 创建MCP客户端包装器
     *
     * @description 根据配置的MCP服务端地址和名称，通过SSE传输方式创建同步MCP客户端实例，仅在配置了mcp.server.url时生效
     * @return MCP客户端包装器实例
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Bean
    @ConditionalOnProperty(prefix = "mcp.server", name = "url")
    public McpClientWrapper mcpClientWrapper() {
        log.info("Creating MCP client, name={}, url={}", mcpServerName, mcpServerUrl);
        return McpClientBuilder.create(mcpServerName)
                .sseTransport(mcpServerUrl)
                .buildSync();
    }
}
