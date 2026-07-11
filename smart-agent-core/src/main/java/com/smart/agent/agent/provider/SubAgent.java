package com.smart.agent.agent.provider;

import java.time.Duration;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.subagent.SubAgentProvider;

/**
 * Sub-agent definition interface.
 *
 * @description Defines the contract for sub-agents in the Supervisor pattern. All sub-agent
 *              implementations must provide name, tool name, description, message length limit,
 *              and time window information for Supervisor orchestration and scheduling.
 * @author Jiangbo Li
 * @date 2026-07-11
 * @version 1.0
 */
public interface SubAgent extends SubAgentProvider<ReActAgent> {

    /**
     * Get the agent name.
     *
     * @return agent name used for session management and log tracing
     */
    String getAgentName();

    /**
     * Get the tool name used when registering with the Supervisor's toolkit.
     *
     * @return tool name
     */
    String getToolName();

    /**
     * Get the agent description for Supervisor routing decisions.
     *
     * @return agent description
     */
    String getDescription();

    /**
     * Get the maximum number of messages allowed in the sub-agent's session.
     *
     * @return maximum number of messages
     */
    Integer getMaxMessageLength();

    /**
     * Get the time window for session message validity.
     *
     * @return time window duration
     */
    Duration getTimeWindow();
}
