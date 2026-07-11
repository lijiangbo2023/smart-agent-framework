package com.smart.agent.agent.config;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent model configuration class.
 *
 * @description Configure various LLM model beans, including default, fast, sub-agent, and lightweight models
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Configuration
public class AgentModelConfig {

    @Value("${llm.api-key}")
    private String llmApiKey;

    @Value("${llm.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String llmBaseUrl;

    @Value("${llm.endpoint-path:/chat/completions}")
    private String llmEndpointPath;

    @Value("${llm.sub-api-key:${llm.api-key}}")
    private String subLlmApiKey;

    @Value("${llm.model:qwen-max}")
    private String defaultModelName;

    @Value("${llm.fast-model:qwen-turbo}")
    private String fastModelName;

    @Value("${llm.light-model:qwen-turbo}")
    private String lightModelName;

    /**
     * Create default model.
     *
     * @description Default LLM model used by the main agent, supports deep reasoning
     * @return default model instance
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Bean
    public OpenAIChatModel defaultModel() {
        return OpenAIChatModel.builder()
                .apiKey(llmApiKey)
                .baseUrl(llmBaseUrl)
                .endpointPath(llmEndpointPath)
                .modelName(defaultModelName)
                .build();
    }

    /**
     * Create fast model.
     *
     * @description Fast response model used by the supervisor, with deep reasoning disabled and web search enabled
     * @return fast model instance
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Bean
    public OpenAIChatModel fastModel() {
        GenerateOptions noThinkingOptions = GenerateOptions.builder()
                .additionalBodyParam("enable_thinking", false)
                .additionalBodyParam("enable_search", true)
                .build();

        return OpenAIChatModel.builder()
                .apiKey(llmApiKey)
                .baseUrl(llmBaseUrl)
                .endpointPath(llmEndpointPath)
                .modelName(fastModelName)
                .generateOptions(noThinkingOptions)
                .build();
    }

    /**
     * Create sub-agent default model.
     *
     * @description Default model used by sub-agents, supports independent API key configuration
     * @return sub-agent default model instance
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Bean
    public OpenAIChatModel subDefaultModel() {
        GenerateOptions noThinkingOptions = GenerateOptions.builder()
                .additionalBodyParam("enable_thinking", false)
                .additionalBodyParam("enable_search", true)
                .build();

        return OpenAIChatModel.builder()
                .apiKey(subLlmApiKey)
                .baseUrl(llmBaseUrl)
                .endpointPath(llmEndpointPath)
                .modelName(defaultModelName)
                .generateOptions(noThinkingOptions)
                .build();
    }

    /**
     * Create sub-agent fast model.
     *
     * @description Fast response model used by sub-agents
     * @return sub-agent fast model instance
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Bean
    public OpenAIChatModel subFastModel() {
        GenerateOptions noThinkingOptions = GenerateOptions.builder()
                .additionalBodyParam("enable_thinking", false)
                .additionalBodyParam("enable_search", true)
                .build();

        return OpenAIChatModel.builder()
                .apiKey(subLlmApiKey)
                .baseUrl(llmBaseUrl)
                .endpointPath(llmEndpointPath)
                .modelName(fastModelName)
                .generateOptions(noThinkingOptions)
                .build();
    }

    /**
     * Create lightweight model.
     *
     * @description Small model for lightweight tasks such as message intent classification
     * @return lightweight model instance
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Bean
    public OpenAIChatModel lightModel() {
        GenerateOptions noThinkingOptions = GenerateOptions.builder()
                .additionalBodyParam("enable_thinking", false)
                .build();

        return OpenAIChatModel.builder()
                .apiKey(llmApiKey)
                .baseUrl(llmBaseUrl)
                .endpointPath(llmEndpointPath)
                .modelName(lightModelName)
                .generateOptions(noThinkingOptions)
                .build();
    }
}
