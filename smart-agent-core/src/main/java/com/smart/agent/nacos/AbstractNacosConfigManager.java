package com.smart.agent.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.smart.agent.util.NamedThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for Nacos-based configuration managers.
 *
 * @description Provides shared Nacos ConfigService initialization and listener management.
 *              Subclasses implement specific configuration loading and update logic.
 * @author Jiangbo Li
 * @date 2026-06-18
 * @version 1.0
 */
@Slf4j
public abstract class AbstractNacosConfigManager {

    private static final long GET_CONFIG_TIMEOUT_MS = 5000L;

    @Value("${nacos.config.server-addr:127.0.0.1:8848}")
    protected String serverAddr;

    @Value("${nacos.config.namespace:}")
    protected String namespace;

    @Value("${nacos.config.group:smart-agent}")
    protected String group;

    protected ConfigService configService;

    protected final Executor listenerExecutor = new ThreadPoolExecutor(
            1, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(128),
            new NamedThreadFactory(getListenerThreadPrefix()),
            new ThreadPoolExecutor.CallerRunsPolicy());

    /**
     * Subclasses provide the thread name prefix for the listener executor.
     */
    protected abstract String getListenerThreadPrefix();

    /**
     * Initialize the Nacos ConfigService. Safe to call multiple times; only initializes once.
     *
     * @return true if ConfigService was successfully initialized
     */
    protected boolean initConfigService() {
        if (configService != null) return true;
        try {
            Properties properties = new Properties();
            properties.put("serverAddr", serverAddr);
            if (namespace != null && !namespace.isEmpty()) {
                properties.put("namespace", namespace);
            }
            configService = NacosFactory.createConfigService(properties);
            log.info("{}: Nacos ConfigService initialized, serverAddr={}, group={}",
                    getClass().getSimpleName(), serverAddr, group);
            return true;
        } catch (Exception e) {
            log.warn("{}: Failed to initialize Nacos ConfigService: {}", getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    /**
     * Load configuration from Nacos and register a change listener.
     *
     * @param dataId Nacos dataId to watch
     * @param onUpdate callback invoked when the configuration changes
     */
    protected void loadAndWatch(String dataId, ConfigUpdateCallback onUpdate) {
        if (!initConfigService()) {
            log.warn("{}: Nacos not available, dynamic update disabled for dataId={}", getClass().getSimpleName(), dataId);
            return;
        }
        try {
            String currentValue = configService.getConfig(dataId, group, GET_CONFIG_TIMEOUT_MS);
            if (currentValue != null && !currentValue.isBlank()) {
                onUpdate.onUpdate(currentValue);
            }

            configService.addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return listenerExecutor;
                }

                @Override
                public void receiveConfigInfo(String data) {
                    onUpdate.onUpdate(data);
                }
            });
        } catch (Exception e) {
            log.warn("{}: Failed to load/watch Nacos config dataId={}: {}",
                    getClass().getSimpleName(), dataId, e.getMessage());
        }
    }

    @FunctionalInterface
    protected interface ConfigUpdateCallback {
        void onUpdate(String data);
    }
}
