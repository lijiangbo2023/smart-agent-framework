package com.smart.agent.demo;

import com.smart.agent.agent.provider.SubAgent;
import com.smart.agent.nacos.AgentPromptManager;
import com.smart.agent.rag.RagService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Code Agent — specializes in code generation, review, debugging, and technical analysis.
 *
 * @description Provides a ReActAgent with tools for code review, formatting, explanation, and test generation.
 *              Registered as a Spring Component so it auto-registers with the Supervisor.
 * @author Jiangbo Li
 * @date 2025-07-12
 * @version 1.0
 */
@Component
public class CodeSubAgentProvider implements SubAgent {

    private static final String AGENT_NAME = "CodeAgent";
    private static final String TOOL_NAME = "code_assistant";
    private static final String DESCRIPTION = "Professional code assistant that can review code, generate unit tests, format code, explain code structure, and search technical documentation. Best for programming, software engineering, and code-related questions";

    private static final String PROMPT = """
            You are a professional software engineer and code assistant.

            You have the following tools:
            - knowledge_search: Search the knowledge base for technical documentation
            - code_review: Review code for bugs, performance, and style issues
            - format_code: Format and beautify code
            - explain_code: Analyze code structure and explain it
            - generate_unit_test: Generate unit test templates

            Working principles:
            - Always provide complete, working code solutions with proper error handling
            - Explain your reasoning before writing code
            - Use best practices and design patterns appropriate for the language
            - When reviewing code, be constructive and specific
            - Always respond in Chinese, but keep code, API names, and technical terms in English
            - Include import statements and dependencies when providing code
            """;

    private final OpenAIChatModel model;
    private final AgentPromptManager agentPromptManager;
    private final RagService ragService;

    public CodeSubAgentProvider(@Qualifier("subDefaultModel") OpenAIChatModel model,
                                 AgentPromptManager agentPromptManager,
                                 RagService ragService) {
        this.model = model;
        this.agentPromptManager = agentPromptManager;
        this.ragService = ragService;
        this.agentPromptManager.register(AGENT_NAME, PROMPT);
    }

    @Override
    public ReActAgent provide() {
        Toolkit toolkit = new Toolkit();
        toolkit.registration().tool(new CodeTools(ragService)).apply();

        return ReActAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(agentPromptManager.getPrompt(AGENT_NAME))
                .model(model)
                .memory(new InMemoryMemory())
                .toolkit(toolkit)
                .maxIters(8)
                .build();
    }

    @Override
    public String getAgentName() { return AGENT_NAME; }

    @Override
    public String getToolName() { return TOOL_NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public Integer getMaxMessageLength() { return 30; }

    @Override
    public Duration getTimeWindow() { return Duration.ofMinutes(60); }
}
