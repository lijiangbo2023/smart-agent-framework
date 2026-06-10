package com.smart.agent.agent.provider;

import java.time.Duration;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.subagent.SubAgentProvider;

/**
 * 子Agent提供者接口
 *
 * @description 定义子Agent的基础契约，所有子Agent实现类须提供名称、工具名、描述、消息长度限制和时间窗口等信息，供Supervisor编排调度
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
public interface AbstractSubAgent extends SubAgentProvider<ReActAgent> {

    /**
     * 获取Agent名称
     *
     * @description 返回子Agent的唯一标识名称，用于会话管理和日志追踪
     * @return Agent名称
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    String getAgentName();

    /**
     * 获取工具名称
     *
     * @description 返回子Agent注册到Supervisor工具集时使用的工具名称
     * @return 工具名称
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    String getToolName();

    /**
     * 获取Agent描述
     *
     * @description 返回子Agent的能力描述，Supervisor根据此描述判断是否调用该子Agent
     * @return Agent描述信息
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    String getDescription();

    /**
     * 获取最大消息长度
     *
     * @description 返回子Agent会话中允许保留的最大消息条数，用于控制上下文长度
     * @return 最大消息条数
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    Integer getMaxMessageLength();

    /**
     * 获取时间窗口
     *
     * @description 返回子Agent会话消息的有效时间窗口，超出时间窗口的历史消息将被过滤
     * @return 时间窗口
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    Duration getTimeWindow();
}
