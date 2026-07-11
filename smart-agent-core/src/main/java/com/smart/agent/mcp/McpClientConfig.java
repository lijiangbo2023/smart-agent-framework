package com.smart.agent.mcp;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP client configuration.
 *
 * @description Responsible for creating and configuring the MCP (Model Context Protocol) client,
 *              connecting to the MCP server via SSE transport.
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
     * Create the MCP client wrapper.
     *
     * @description Creates a synchronous MCP client instance via SSE transport using the
     *              configured server address and name. Only active when mcp.server.url is set.
     * @return MCP client wrapper instance
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
