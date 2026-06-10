package com.smart.agent.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent提示词管理器
 *
 * @description 管理Agent提示词，支持通过Nacos实现热加载。注册Agent时从Nacos拉取提示词配置并监听变更，
 *              当Nacos不可用时自动降级使用本地兜底提示词。
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Component
public class AgentPromptManager {

    private static final String DATA_ID_SUFFIX = "-prompt";
    private static final long GET_CONFIG_TIMEOUT_MS = 5000L;

    @Value("${nacos.config.server-addr:127.0.0.1:8848}")
    private String serverAddr;

    @Value("${nacos.config.namespace:}")
    private String namespace;

    @Value("${nacos.config.group:smart-agent}")
    private String group;

    private ConfigService configService;

    private final Executor listenerExecutor = new ThreadPoolExecutor(
            1, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(128),
            new ThreadFactory() {
                private final AtomicInteger seq = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "nacos-prompt-listener-" + seq.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    private final ConcurrentMap<String, String> nacosPrompts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> fallbackPrompts = new ConcurrentHashMap<>();

    /**
     * 初始化Nacos配置服务
     *
     * @description 创建Nacos ConfigService实例，用于后续的提示词配置拉取和监听。
     *              初始化失败时仅打印警告日志，不会阻断应用启动。
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @PostConstruct
    public void init() {
        try {
            Properties properties = new Properties();
            properties.put("serverAddr", serverAddr);
            if (namespace != null && !namespace.isEmpty()) {
                properties.put("namespace", namespace);
            }
            configService = NacosFactory.createConfigService(properties);
            log.info("Nacos ConfigService initialized, serverAddr={}, group={}", serverAddr, group);
        } catch (Exception e) {
            log.warn("Failed to initialize Nacos ConfigService: {}. Prompt dynamic update disabled.", e.getMessage());
        }
    }

    /**
     * 注册Agent提示词
     *
     * @description 注册指定Agent的兜底提示词，并尝试从Nacos加载最新配置。
     *              同时添加Nacos配置监听器，实现提示词的动态热更新。
     * @param agentName Agent名称，作为Nacos配置的dataId前缀
     * @param fallbackPrompt 兜底提示词，当Nacos不可用时使用
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void register(String agentName, String fallbackPrompt) {
        fallbackPrompts.put(agentName, fallbackPrompt);

        if (configService == null) {
            log.warn("Nacos ConfigService not available, using fallback prompt for agent '{}'", agentName);
            return;
        }

        String dataId = agentName + DATA_ID_SUFFIX;
        try {
            String currentValue = configService.getConfig(dataId, group, GET_CONFIG_TIMEOUT_MS);
            if (currentValue != null && !currentValue.isBlank()) {
                nacosPrompts.put(agentName, currentValue);
                log.info("Loaded Nacos prompt for agent '{}', dataId='{}', length={}", agentName, dataId, currentValue.length());
            }

            configService.addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return listenerExecutor;
                }

                @Override
                public void receiveConfigInfo(String data) {
                    if (data != null && !data.isBlank()) {
                        nacosPrompts.put(agentName, data);
                        log.info("Nacos prompt updated for agent '{}', dataId='{}'", agentName, dataId);
                    } else {
                        nacosPrompts.remove(agentName);
                        log.warn("Nacos prompt cleared for agent '{}', dataId='{}', falling back to default", agentName, dataId);
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Failed to load Nacos prompt for agent '{}': {}, using fallback", agentName, e.getMessage());
        }
    }

    /**
     * 获取Agent提示词
     *
     * @description 优先返回Nacos中的提示词配置，若Nacos中无配置则返回兜底提示词。
     *              若Agent未注册则抛出IllegalStateException异常。
     * @param agentName Agent名称
     * @return 当前生效的提示词内容
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
