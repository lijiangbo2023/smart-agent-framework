package com.smart.agent.callback.chatbot;

import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import com.smart.agent.nacos.SystemConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DingTalk Stream client configuration.
 *
 * @description Initializes the DingTalk Stream long-connection client when
 *              dingtalk.stream.enabled=true. Reads appKey/appSecret from
 *              SystemConfigManager and registers the chatbot message callback handler.
 *              The connection is gracefully stopped via the Bean destroy method on shutdown.
 * @author Jiangbo Li
 * @date 2026-06-13
 * @version 1.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dingtalk.stream.enabled", havingValue = "true")
public class DingTalkStreamConfig {

    private final SystemConfigManager systemConfigManager;
    private final DingTalkChatbotHandler chatbotHandler;

    /**
     * Create and start the DingTalk Stream long-connection client.
     *
     * @description Retrieves DingTalk appKey/appSecret from SystemConfigManager to build
     *              AuthClientCredential, registers the chatbot message callback
     *              (topic=BOT_MESSAGE_TOPIC) to DingTalkChatbotHandler, and calls
     *              client.start() to establish the long connection. The Bean destroy
     *              method automatically calls stop() to release resources.
     * @return the started DingTalk Stream client instance
     * @throws Exception when appKey/appSecret are not configured or connection fails
     * @author Jiangbo Li
     * @date 2026-06-13
     */
    @Bean(destroyMethod = "stop")
    public OpenDingTalkClient openDingTalkStreamClient() {
        try {
            SystemConfigManager.DingTalkConfig dingtalk = systemConfigManager.getSystemConfig().getDingtalk();
            if (dingtalk == null) {
                log.warn("DingTalk config not loaded from Nacos, skipping Stream client initialization");
                return null;
            }
            String appKey = dingtalk.getAppKey();
            String appSecret = dingtalk.getAppSecret();
            if (appKey == null || appKey.isEmpty() || appSecret == null || appSecret.isEmpty()) {
                log.warn("DingTalk appKey/appSecret not configured, skipping Stream client initialization");
                return null;
            }

            OpenDingTalkClient client = OpenDingTalkStreamClientBuilder
                    .custom()
                    .credential(new AuthClientCredential(appKey, appSecret))
                    .registerCallbackListener(DingTalkStreamTopics.BOT_MESSAGE_TOPIC, chatbotHandler)
                    .build();
            client.start();
            log.info("DingTalk Stream client started, appKey={}, topic={}", maskAppKey(appKey), DingTalkStreamTopics.BOT_MESSAGE_TOPIC);
            return client;
        } catch (Exception e) {
            log.warn("Failed to start DingTalk Stream client, DingTalk features will be unavailable: {}", e.getMessage());
            return null;
        }
    }

    private String maskAppKey(String appKey) {
        if (appKey == null || appKey.length() <= 6) {
            return "***";
        }
        return appKey.substring(0, 3) + "***" + appKey.substring(appKey.length() - 3);
    }
}
