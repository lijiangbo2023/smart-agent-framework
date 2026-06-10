package com.smart.agent.agent.config;

import com.smart.agent.constant.AgentConstants;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 模型配置类
 *
 * @description 配置各类 LLM 模型 Bean，包括默认模型、快速模型、子 Agent 模型和轻量模型
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

    /**
     * 创建默认模型
     *
     * @description 主 Agent 使用的默认 LLM 模型，支持深度思考
     * @return OpenAIChatModel 默认模型实例
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Bean
    public OpenAIChatModel defaultModel() {
        return OpenAIChatModel.builder()
                .apiKey(llmApiKey)
                .baseUrl(llmBaseUrl)
                .endpointPath(llmEndpointPath)
                .modelName(AgentConstants.ModelConstants.DEFAULT_MODEL)
                .build();
    }

    /**
     * 创建快速模型
     *
     * @description Supervisor 使用的快速响应模型，关闭深度思考并启用联网搜索
     * @return OpenAIChatModel 快速模型实例
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
                .modelName(AgentConstants.ModelConstants.FAST_MODEL)
                .generateOptions(noThinkingOptions)
                .build();
    }

    /**
     * 创建子 Agent 默认模型
     *
     * @description 子 Agent 使用的默认模型，可配置独立的 API Key
     * @return OpenAIChatModel 子 Agent 默认模型实例
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
                .modelName(AgentConstants.ModelConstants.DEFAULT_MODEL)
                .generateOptions(noThinkingOptions)
                .build();
    }

    /**
     * 创建子 Agent 快速模型
     *
     * @description 子 Agent 使用的快速响应模型
     * @return OpenAIChatModel 子 Agent 快速模型实例
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
                .modelName(AgentConstants.ModelConstants.FAST_MODEL)
                .generateOptions(noThinkingOptions)
                .build();
    }

    /**
     * 创建轻量模型
     *
     * @description 用于消息意图分类等轻量任务的小模型
     * @return OpenAIChatModel 轻量模型实例
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
                .modelName(AgentConstants.ModelConstants.LIGHT_MODEL)
                .generateOptions(noThinkingOptions)
                .build();
    }
}
