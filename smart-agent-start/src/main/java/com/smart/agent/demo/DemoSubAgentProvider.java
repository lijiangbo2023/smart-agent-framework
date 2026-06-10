package com.smart.agent.demo;

import com.smart.agent.agent.provider.AbstractSubAgent;
import com.smart.agent.nacos.AgentPromptManager;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 示例子Agent提供者
 *
 * @description 演示用的子Agent提供者实现，提供一个通用智能助手Agent，具备查询天气、获取时间、数学计算和互联网搜索等能力
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Component
public class DemoSubAgentProvider implements AbstractSubAgent {

    private static final String AGENT_NAME = "DemoAgent";
    private static final String TOOL_NAME = "demo_assistant";
    private static final String DESCRIPTION = "通用智能助手，可以查询天气、获取时间、数学计算、搜索信息、IP查询、文本翻译、短链接生成、Unicode查询、随机笑话等";

    private static final String PROMPT = """
            你是一个友好的智能助手，擅长使用工具帮助用户解决问题。

            你拥有以下工具：
            - get_current_time: 获取当前日期和时间
            - calculator: 计算数学表达式
            - get_weather: 查询城市天气
            - web_search_summary: 搜索互联网信息
            - ip_lookup: 查询IP地址归属地
            - random_joke: 获取随机笑话
            - translate: 文本翻译（支持中英日韩法德等语言）
            - url_shorten: 生成短链接
            - unicode_lookup: 查询字符的Unicode编码信息

            工作原则：
            - 当用户的问题可以通过工具获取准确信息时，优先使用工具
            - 回答要简洁、准确、有帮助
            - 如果工具调用失败，用你的知识尝试回答
            - 始终使用中文回答
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
     * 创建并提供ReActAgent实例
     *
     * @description 构建一个配备DemoTools工具集的ReActAgent，使用InMemoryMemory作为记忆存储，最大迭代次数为5
     * @return 配置完成的ReActAgent实例
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
     * 获取Agent名称
     *
     * @description 返回当前子Agent的唯一标识名称
     * @return Agent名称
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public String getAgentName() {
        return AGENT_NAME;
    }

    /**
     * 获取工具名称
     *
     * @description 返回该Agent作为工具被Supervisor调用时的工具名称
     * @return 工具名称
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    /**
     * 获取Agent描述
     *
     * @description 返回该Agent的功能描述信息，供Supervisor进行路由决策时参考
     * @return Agent功能描述
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    /**
     * 获取最大消息长度
     *
     * @description 返回该Agent在记忆窗口内允许保留的最大消息条数
     * @return 最大消息条数
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public Integer getMaxMessageLength() {
        return 20;
    }

    /**
     * 获取时间窗口
     *
     * @description 返回该Agent记忆保留的时间窗口，超出此时间范围的历史消息将被清除
     * @return 时间窗口时长
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @Override
    public Duration getTimeWindow() {
        return Duration.ofMinutes(30);
    }
}
