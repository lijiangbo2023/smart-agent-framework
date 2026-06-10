package com.smart.agent.dingtalk;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.smart.agent.nacos.SystemConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 钉钉访问令牌服务
 *
 * @description 负责钉钉access_token的获取与缓存管理，支持过期前自动刷新，保证令牌可用性
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Service
public class DingTalkAccessTokenService {

    private static final String GET_TOKEN_URL = "https://oapi.dingtalk.com/gettoken";
    private static final long REFRESH_AHEAD_MILLIS = 5 * 60 * 1000L;

    private final SystemConfigManager systemConfigManager;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final AtomicReference<TokenCache> cache = new AtomicReference<>();

    public DingTalkAccessTokenService(SystemConfigManager systemConfigManager) {
        this.systemConfigManager = systemConfigManager;
    }

    /**
     * 获取钉钉访问令牌
     *
     * @description 从缓存中获取access_token，若缓存为空或即将过期则自动刷新
     * @return 有效的钉钉access_token字符串
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public synchronized String getAccessToken() {
        TokenCache current = cache.get();
        long now = System.currentTimeMillis();
        if (current != null && current.expireAt - REFRESH_AHEAD_MILLIS > now) {
            return current.token;
        }
        return refresh();
    }

    private String refresh() {
        SystemConfigManager.DingTalkConfig dingtalk = systemConfigManager.getSystemConfig().getDingtalk();
        String appKey = dingtalk != null ? dingtalk.getAppKey() : null;
        String appSecret = dingtalk != null ? dingtalk.getAppSecret() : null;
        if (appKey == null || appKey.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            throw new IllegalStateException("DingTalk appKey/appSecret not configured");
        }
        try {
            String url = GET_TOKEN_URL
                    + "?appkey=" + URLEncoder.encode(appKey, StandardCharsets.UTF_8)
                    + "&appsecret=" + URLEncoder.encode(appSecret, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject body = JSON.parseObject(response.body());
            Integer errcode = body.getInteger("errcode");
            if (errcode == null || errcode != 0) {
                throw new IllegalStateException("Get access_token failed: " + response.body());
            }
            String token = body.getString("access_token");
            int expiresIn = body.getIntValue("expires_in");
            long expireAt = System.currentTimeMillis() + expiresIn * 1000L;
            cache.set(new TokenCache(token, expireAt));
            log.info("Refreshed DingTalk access_token, expiresIn={}s", expiresIn);
            return token;
        } catch (Exception e) {
            throw new IllegalStateException("DingTalk gettoken failed", e);
        }
    }

    private record TokenCache(String token, long expireAt) {
    }
}
