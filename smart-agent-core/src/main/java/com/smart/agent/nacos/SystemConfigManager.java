package com.smart.agent.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.smart.agent.util.JsonUtils;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 系统配置管理器
 *
 * @description 基于Nacos管理系统级配置，支持配置的动态加载和热更新。
 *              提供配置变更监听机制，当Nacos配置发生变化时通知所有已注册的监听器。
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Component
public class SystemConfigManager {

    private static final String DATA_ID = "system-config.json";
    private static final long GET_CONFIG_TIMEOUT_MS = 5000L;

    @Value("${nacos.config.server-addr:127.0.0.1:8848}")
    private String serverAddr;

    @Value("${nacos.config.namespace:}")
    private String namespace;

    @Value("${nacos.config.group:smart-agent}")
    private String group;

    @Getter
    private volatile SystemConfig systemConfig;

    private final List<Consumer<SystemConfig>> listeners = new CopyOnWriteArrayList<>();

    /**
     * 添加配置变更监听器
     *
     * @description 注册一个配置变更回调，当系统配置从Nacos更新时将触发该回调。
     *              监听器使用CopyOnWriteArrayList存储，线程安全。
     * @param listener 配置变更消费者回调
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void addConfigChangeListener(Consumer<SystemConfig> listener) {
        listeners.add(listener);
    }

    /**
     * 初始化系统配置
     *
     * @description 初始化兜底配置后连接Nacos加载最新系统配置，并注册配置变更监听器。
     *              若Nacos连接失败则使用兜底配置，不影响应用启动。
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    @PostConstruct
    public void init() {
        initFallback();

        try {
            Properties properties = new Properties();
            properties.put("serverAddr", serverAddr);
            if (namespace != null && !namespace.isEmpty()) {
                properties.put("namespace", namespace);
            }
            ConfigService configService = NacosFactory.createConfigService(properties);

            String currentValue = configService.getConfig(DATA_ID, group, GET_CONFIG_TIMEOUT_MS);
            if (currentValue != null && !currentValue.isBlank()) {
                applyConfig(currentValue);
            }

            configService.addListener(DATA_ID, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "nacos-system-config-listener");
                        t.setDaemon(true);
                        return t;
                    });
                }

                @Override
                public void receiveConfigInfo(String data) {
                    log.info("Received system config update from Nacos");
                    applyConfig(data);
                }
            });

            log.info("SystemConfigManager initialized from Nacos, serverAddr={}", serverAddr);
        } catch (Exception e) {
            log.warn("Failed to load system config from Nacos: {}. Using fallback config.", e.getMessage());
        }
    }

    private void initFallback() {
        DingTalkConfig robot = new DingTalkConfig();
        SystemConfig config = new SystemConfig();
        config.setDingtalk(robot);
        config.setPermissionUserIds(List.of());
        config.setAllowTalkUserIds(List.of());
        this.systemConfig = config;
    }

    private void applyConfig(String json) {
        JsonUtils.deserialize(json, SystemConfig.class).ifPresent(config -> {
            this.systemConfig = config;
            log.info("System config applied: {}", json);
            for (Consumer<SystemConfig> listener : listeners) {
                try {
                    listener.accept(config);
                } catch (Exception e) {
                    log.error("Config change listener error", e);
                }
            }
        });
    }

    /**
     * 系统配置
     *
     * @description 系统级配置数据模型，包含权限用户列表、允许对话用户列表、无权限提示文案及钉钉配置等。
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    @Data
    public static class SystemConfig {
        private List<String> permissionUserIds;
        private List<String> allowTalkUserIds;
        private String noPermissionText;
        private DingTalkConfig dingtalk;
    }

    /**
     * 钉钉配置
     *
     * @description 钉钉机器人相关配置，包含应用凭证、机器人编码和AI卡片模板ID。
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    @Data
    public static class DingTalkConfig {
        private String appKey;
        private String appSecret;
        private String robotCode;
        private String aiCardTemplateId;
    }
}
