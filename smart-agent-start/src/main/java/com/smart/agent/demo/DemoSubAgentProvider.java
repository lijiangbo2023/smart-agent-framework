package com.smart.agent.demo;

import com.smart.agent.agent.provider.SubAgent;
import com.smart.agent.nacos.AgentPromptManager;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Demo sub-agent provider
 *
 * @description A demo sub-agent provider implementation that provides a general-purpose intelligent assistant
 *              with capabilities such as weather query, time retrieval, math calculation and internet search
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Component
public class DemoSubAgentProvider implements SubAgent {

    private static final String AGENT_NAME = "DemoAgent";
    private static final String TOOL_NAME = "demo_assistant";
    private static final String DESCRIPTION = "General-purpose intelligent assistant that can query weather, get current time, perform math calculations, search the internet, look up IP addresses, translate text, generate short URLs, query Unicode information, tell random jokes and more";

    private static final String PROMPT = """
            You are a friendly intelligent assistant skilled at using tools to help users solve problems.

            You have the following tools:
            - get_current_time: Get the current date and time
            - calculator: Evaluate math expressions
            - get_weather: Query city weather
            - web_search_summary: Search the internet
            - ip_lookup: Look up IP address geolocation
            - random_joke: Get a random joke
            - translate: Translate text (supports Chinese, English, Japanese, Korean, French, German, etc.)
            - url_shorten: Generate short URLs
            - unicode_lookup: Query Unicode encoding information for characters

            Working principles:
            - Prefer using tools when they can provide accurate information
            - Keep responses concise, accurate and helpful
            - If a tool call fails, try answering from your knowledge
            - Always respond in Chinese
            """;

    private final OpenAIChatModel model;
    private final AgentPromptManager agentPromptManager;

    public DemoSubAgentProvider(@Qualifier("subDefaultModel") OpenAIChatModel model,
                                 AgentPromptManager agentPromptManager) {
        this.model = model;
        this.agentPromptManager = agentPromptManager;
        this.agentPromptManager.register(AGENT_NAME, PROMPT);
    }

    /**
     * Create and provide a ReActAgent instance
     *
     * @description Builds a ReActAgent equipped with DemoTools toolkit, using InMemoryMemory
     *              as memory storage with a maximum of 5 iterations
     * @return fully configured ReActAgent instance
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public ReActAgent provide() {
        Toolkit toolkit = new Toolkit();
        toolkit.registration().tool(new DemoTools()).apply();

        return ReActAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(agentPromptManager.getPrompt(AGENT_NAME))
                .model(model)
                .memory(new InMemoryMemory())
                .toolkit(toolkit)
                .maxIters(5)
                .build();
    }

    /**
     * Get the agent name
     *
     * @description Returns the unique identifier name of this sub-agent
     * @return agent name
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public String getAgentName() {
        return AGENT_NAME;
    }

    /**
     * Get the tool name
     *
     * @description Returns the tool name used when this agent is invoked as a tool by the Supervisor
     * @return tool name
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    /**
     * Get the agent description
     *
     * @description Returns the functional description of this agent for the Supervisor's routing decisions
     * @return agent functional description
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    /**
     * Get the maximum message length
     *
     * @description Returns the maximum number of messages this agent is allowed to retain in its memory window
     * @return maximum number of messages
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public Integer getMaxMessageLength() {
        return 20;
    }

    /**
     * Get the time window
     *
     * @description Returns the memory retention time window for this agent; historical messages
     *              beyond this duration will be cleared
     * @return time window duration
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public Duration getTimeWindow() {
        return Duration.ofMinutes(30);
    }
}
