package com.smart.agent.nacos;

import com.smart.agent.util.JsonUtils;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * System configuration manager.
 *
 * @description Manages system-level configuration via Nacos with support for dynamic loading
 *              and hot-reload. Provides a configuration change listener mechanism that notifies
 *              all registered listeners when Nacos configuration changes.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Component
public class SystemConfigManager extends AbstractNacosConfigManager {

    private static final String DATA_ID = "system-config.json";

    @Getter
    private volatile SystemConfig systemConfig;

    private final List<Consumer<SystemConfig>> listeners = new CopyOnWriteArrayList<>();

    @Override
    protected String getListenerThreadPrefix() {
        return "nacos-system-config-listener";
    }

    /**
     * Add a configuration change listener.
     *
     * @description Registers a configuration change callback that is triggered when the system
     *              configuration is updated from Nacos. Listeners are stored in a
     *              CopyOnWriteArrayList for thread safety.
     * @param listener configuration change consumer callback
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void addConfigChangeListener(Consumer<SystemConfig> listener) {
        listeners.add(listener);
    }

    @PostConstruct
    public void init() {
        initFallback();
        loadAndWatch(DATA_ID, this::applyConfig);

        if (configService != null) {
            log.info("SystemConfigManager initialized from Nacos, serverAddr={}", serverAddr);
        } else {
            log.warn("SystemConfigManager initialized with fallback config (Nacos unavailable)");
        }
    }

    private void initFallback() {
        DingTalkConfig robot = new DingTalkConfig();
        SystemConfig config = new SystemConfig();
        config.setDingtalk(robot);
        this.systemConfig = config;
    }

    private void applyConfig(String json) {
        if (json == null || json.isBlank()) return;
        JsonUtils.deserialize(json, SystemConfig.class).ifPresent(config -> {
            this.systemConfig = config;
            log.info("System config applied from Nacos");
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
     * System configuration.
     */
    @Data
    public static class SystemConfig {
        private DingTalkConfig dingtalk;
    }

    /**
     * DingTalk configuration.
     */
    @Data
    public static class DingTalkConfig {
        private String appKey;
        private String appSecret;
        private String robotCode;
        private String aiCardTemplateId;
    }
}
