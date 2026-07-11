package com.smart.agent.nacos;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Agent prompt manager.
 *
 * @description Manages Agent prompts with hot-reload support via Nacos. On Agent registration,
 *              pulls prompt configuration from Nacos and listens for changes. Falls back to
 *              local default prompts when Nacos is unavailable.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Component
public class AgentPromptManager extends AbstractNacosConfigManager {

    private static final String DATA_ID_SUFFIX = "-prompt";

    private final ConcurrentMap<String, String> nacosPrompts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> fallbackPrompts = new ConcurrentHashMap<>();

    @Override
    protected String getListenerThreadPrefix() {
        return "nacos-prompt-listener";
    }

    @PostConstruct
    public void init() {
        initConfigService();
    }

    /**
     * Register an Agent prompt.
     *
     * @description Registers the fallback prompt for the specified Agent and attempts to load
     *              the latest configuration from Nacos. Also adds a Nacos configuration listener
     *              to enable dynamic hot-reload of prompts.
     * @param agentName Agent name, used as the Nacos dataId prefix
     * @param fallbackPrompt fallback prompt, used when Nacos is unavailable
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void register(String agentName, String fallbackPrompt) {
        fallbackPrompts.put(agentName, fallbackPrompt);

        String dataId = agentName + DATA_ID_SUFFIX;
        loadAndWatch(dataId, data -> {
            if (data != null && !data.isBlank()) {
                nacosPrompts.put(agentName, data);
                log.info("Nacos prompt updated for agent '{}', dataId='{}'", agentName, dataId);
            } else {
                nacosPrompts.remove(agentName);
                log.warn("Nacos prompt cleared for agent '{}', dataId='{}', falling back to default", agentName, dataId);
            }
        });

        if (configService == null) {
            log.warn("Nacos ConfigService not available, using fallback prompt for agent '{}'", agentName);
        } else {
            String nacosPrompt = nacosPrompts.get(agentName);
            if (nacosPrompt != null) {
                log.info("Loaded Nacos prompt for agent '{}', length={}", agentName, nacosPrompt.length());
            }
        }
    }

    /**
     * Get an Agent prompt.
     *
     * @description Returns the Nacos prompt configuration if available; otherwise returns the
     *              fallback prompt. Throws IllegalStateException if the Agent is not registered.
     * @param agentName Agent name
     * @return the currently active prompt content
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public String getPrompt(String agentName) {
        String nacosPrompt = nacosPrompts.get(agentName);
        if (nacosPrompt != null) {
            return nacosPrompt;
        }
        String fallback = fallbackPrompts.get(agentName);
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("Agent '" + agentName + "' not registered in AgentPromptManager. Call register() first.");
    }
}
