package com.smart.agent.constant;

/**
 * Agent常量类
 *
 * @description 定义Agent框架中使用的全局常量，包括会话管理和模型配置相关的常量
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
public class AgentConstants {

    public static final String SESSION_COMPOSITE_KEY_SEPARATOR = "::";

    /**
     * 模型常量类
     *
     * @description 定义Agent使用的各类模型名称常量，包括默认模型、快速模型和轻量模型
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
