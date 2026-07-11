package com.smart.agent.constant;

/**
 * Agent constants.
 *
 * @description Defines global constants used in the Agent framework, including session management and model configuration constants
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
public class AgentConstants {

    public static final String SESSION_COMPOSITE_KEY_SEPARATOR = "::";

    /**
     * Model constants.
     *
     * @description Defines model name constants used by the Agent, including the default model, fast model, and lightweight model
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    public static class ModelConstants {
        public static final String DEFAULT_MODEL = "qwen3.6-plus";
        public static final String FAST_MODEL = "qwen3.5-plus";
        public static final String LIGHT_MODEL = "qwen3.5-27b";
    }
}
