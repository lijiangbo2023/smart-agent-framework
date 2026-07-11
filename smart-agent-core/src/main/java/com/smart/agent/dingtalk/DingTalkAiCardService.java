package com.smart.agent.dingtalk;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.smart.agent.constant.CardCallbackConstants;
import com.smart.agent.model.CardDeliveryResult;
import com.smart.agent.nacos.SystemConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DingTalk AI card service.
 *
 * @description Provides DingTalk AI interactive card capabilities including creation,
 *              delivery, streaming updates, and card data updates.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Service
public class DingTalkAiCardService {

    private static final String CREATE_AND_DELIVER_URL =
            "https://api.dingtalk.com/v1.0/card/instances/createAndDeliver";
    private static final String STREAMING_UPDATE_URL =
            "https://api.dingtalk.com/v1.0/card/streaming";
    private static final String UPDATE_CARD_URL =
            "https://api.dingtalk.com/v1.0/card/instances";

    public static final String CONVERSATION_TYPE_SINGLE = "1";
    public static final String CONVERSATION_TYPE_GROUP = "2";
    public static final String DEFAULT_STREAMING_KEY = "content";

    private final SystemConfigManager systemConfigManager;
    private final DingTalkAccessTokenService accessTokenService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public DingTalkAiCardService(SystemConfigManager systemConfigManager,
                                  DingTalkAccessTokenService accessTokenService) {
        this.systemConfigManager = systemConfigManager;
        this.accessTokenService = accessTokenService;
    }

    private String getCardTemplateId() {
        SystemConfigManager.DingTalkConfig d = systemConfigManager.getSystemConfig().getDingtalk();
        return d != null ? d.getAiCardTemplateId() : null;
    }

    private String getRobotCode() {
        SystemConfigManager.DingTalkConfig d = systemConfigManager.getSystemConfig().getDingtalk();
        return d != null ? d.getRobotCode() : null;
    }

    /**
     * Create and deliver an AI card.
     *
     * @description Creates a DingTalk AI interactive card based on the conversation type and
     *              delivers it to a single chat or group chat. Returns the card delivery result.
     * @param conversationType conversation type: 1 for single chat, 2 for group chat
     * @param openConversationId open conversation ID, used for group chat
     * @param senderUserId sender user ID
     * @param extraParams additional card parameters
     * @return card delivery result containing outTrackId and processQueryKey; null on failure
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public CardDeliveryResult createAndDeliver(String conversationType, String openConversationId,
                                                String senderUserId, Map<String, String> extraParams) {
        String cardTemplateId = getCardTemplateId();
        String robotCode = getRobotCode();
        if (cardTemplateId == null || robotCode == null) {
            log.warn("createAndDeliver skipped: cardTemplateId or robotCode not configured");
            return null;
        }

        boolean isSingleChat = CONVERSATION_TYPE_SINGLE.equals(conversationType);
        String outTrackId = UUID.randomUUID().toString().replace("-", "");

        Map<String, String> cardParamMap = new HashMap<>();
        cardParamMap.put("content", "正在思考...");
        cardParamMap.put(CardCallbackConstants.ParamKeys.OWNER_USER_ID, senderUserId);
        cardParamMap.put(CardCallbackConstants.ParamKeys.FEEDBACK_STATUS, CardCallbackConstants.Defaults.FEEDBACK_STATUS_NONE);
        cardParamMap.put("config", "{\"autoLayout\":true}");
        if (extraParams != null) {
            cardParamMap.putAll(extraParams);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("cardTemplateId", cardTemplateId);
        body.put("outTrackId", outTrackId);
        body.put("callbackType", "STREAM");
        body.put("cardData", Map.of("cardParamMap", cardParamMap));
        body.put("userIdType", 1);

        if (isSingleChat) {
            body.put("openSpaceId", "dtv1.card//IM_ROBOT." + senderUserId);
            body.put("imRobotOpenSpaceModel", Map.of("supportForward", true));
            body.put("imRobotOpenDeliverModel", Map.of("robotCode", robotCode, "spaceType", "IM_ROBOT"));
        } else {
            body.put("openSpaceId", "dtv1.card//IM_GROUP." + openConversationId);
            body.put("imGroupOpenSpaceModel", Map.of("supportForward", true));
            body.put("imGroupOpenDeliverModel", Map.of("robotCode", robotCode));
        }

        try {
            HttpResponse<String> resp = post(CREATE_AND_DELIVER_URL, body);
            if (resp.statusCode() >= 300) {
                log.warn("createAndDeliver non-2xx, status={}, body={}", resp.statusCode(), resp.body());
                return null;
            }

            String processQueryKey = null;
            try {
                JSONObject respJson = JSON.parseObject(resp.body());
                if (respJson != null && respJson.getBooleanValue("success")) {
                    JSONObject result = respJson.getJSONObject("result");
                    if (result != null) {
                        JSONArray deliverResults = result.getJSONArray("deliverResults");
                        if (deliverResults != null && !deliverResults.isEmpty()) {
                            processQueryKey = deliverResults.getJSONObject(0).getString("carrierId");
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("parse processQueryKey failed", e);
            }

            log.info("createAndDeliver ok, outTrackId={}, processQueryKey={}", outTrackId, processQueryKey);
            return new CardDeliveryResult(outTrackId, processQueryKey);
        } catch (Exception e) {
            log.error("createAndDeliver failed, outTrackId={}", outTrackId, e);
            return null;
        }
    }

    /**
     * Stream-update AI card content (default key).
     *
     * @description Performs a streaming content update on the AI card using the default content key.
     * @param outTrackId card external tracking ID
     * @param fullContent full updated content
     * @param isFinalize whether this is the final update
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void streamingUpdate(String outTrackId, String fullContent, boolean isFinalize) {
        streamingUpdate(outTrackId, DEFAULT_STREAMING_KEY, fullContent, isFinalize);
    }

    /**
     * Stream-update AI card content (specified key).
     *
     * @description Performs a streaming content update on the AI card using the specified key,
     *              supporting full replacement mode.
     * @param outTrackId card external tracking ID
     * @param key the key name corresponding to the updated content
     * @param fullContent full updated content
     * @param isFinalize whether this is the final update
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void streamingUpdate(String outTrackId, String key, String fullContent, boolean isFinalize) {
        if (outTrackId == null || outTrackId.isEmpty()) return;
        Map<String, Object> body = new HashMap<>();
        body.put("outTrackId", outTrackId);
        body.put("guid", UUID.randomUUID().toString());
        body.put("content", fullContent == null ? "" : fullContent);
        body.put("contentType", "ai_card");
        body.put("isFull", true);
        body.put("isFinalize", isFinalize);
        body.put("isError", false);
        body.put("key", key);

        try {
            HttpResponse<String> resp = put(STREAMING_UPDATE_URL, body);
            if (resp.statusCode() >= 300) {
                log.warn("streamingUpdate non-2xx, outTrackId={}, status={}", outTrackId, resp.statusCode());
            }
        } catch (Exception e) {
            log.error("streamingUpdate failed, outTrackId={}", outTrackId, e);
        }
    }

    /**
     * Update card public data by key.
     *
     * @description Incrementally updates the public data area of the AI card
     *              based on the specified key-value pairs.
     * @param outTrackId card external tracking ID
     * @param updates key-value pairs to update
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void updateCardDataByKey(String outTrackId, Map<String, String> updates) {
        if (outTrackId == null || updates == null || updates.isEmpty()) return;

        Map<String, Object> body = new HashMap<>();
        body.put("outTrackId", outTrackId);
        body.put("cardData", Map.of("cardParamMap", updates));
        body.put("cardUpdateOptions", Map.of("updateCardDataByKey", true, "updatePrivateDataByKey", true));

        try {
            put(UPDATE_CARD_URL, body);
        } catch (Exception e) {
            log.error("updateCardDataByKey failed, outTrackId={}", outTrackId, e);
        }
    }

    /**
     * Update card user private data by key.
     *
     * @description Incrementally updates the private data area of the specified user
     *              in the AI card based on the specified key-value pairs.
     * @param outTrackId card external tracking ID
     * @param userId target user ID
     * @param updates key-value pairs to update
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void updatePrivateDataByKey(String outTrackId, String userId, Map<String, String> updates) {
        if (outTrackId == null || userId == null || updates == null || updates.isEmpty()) return;

        Map<String, Object> body = new HashMap<>();
        body.put("outTrackId", outTrackId);
        body.put("privateData", Map.of(userId, Map.of("cardParamMap", updates)));
        body.put("cardUpdateOptions", Map.of("updateCardDataByKey", true, "updatePrivateDataByKey", true));

        try {
            put(UPDATE_CARD_URL, body);
        } catch (Exception e) {
            log.error("updatePrivateDataByKey failed, outTrackId={}, userId={}", outTrackId, userId, e);
        }
    }

    private HttpResponse<String> post(String url, Map<String, Object> body) throws Exception {
        return send(url, body, "POST");
    }

    private HttpResponse<String> put(String url, Map<String, Object> body) throws Exception {
        return send(url, body, "PUT");
    }

    private HttpResponse<String> send(String url, Map<String, Object> body, String method) throws Exception {
        String token = accessTokenService.getAccessToken();
        String json = JSON.toJSONString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("x-acs-dingtalk-access-token", token);
        HttpRequest request = switch (method) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(json)).build();
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(json)).build();
            default -> throw new IllegalArgumentException("unsupported method: " + method);
        };
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
