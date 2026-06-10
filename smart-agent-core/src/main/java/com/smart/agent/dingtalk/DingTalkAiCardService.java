package com.smart.agent.dingtalk;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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
 * 钉钉AI卡片服务
 *
 * @description 提供钉钉AI互动卡片的创建、投放、流式更新以及卡片数据更新等功能
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
     * 创建并投放AI卡片
     *
     * @description 根据会话类型创建钉钉AI互动卡片并投放至单聊或群聊，返回卡片投放结果
     * @param conversationType 会话类型，1为单聊，2为群聊
     * @param openConversationId 开放会话ID，群聊时使用
     * @param senderUserId 发送者用户ID
     * @param extraParams 额外的卡片参数
     * @return 卡片投放结果，包含outTrackId和processQueryKey；投放失败返回null
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
     * 流式更新AI卡片内容（默认key）
     *
     * @description 使用默认的content键对AI卡片进行流式内容更新
     * @param outTrackId 卡片外部跟踪ID
     * @param fullContent 完整的更新内容
     * @param isFinalize 是否为最终更新
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public void streamingUpdate(String outTrackId, String fullContent, boolean isFinalize) {
        streamingUpdate(outTrackId, DEFAULT_STREAMING_KEY, fullContent, isFinalize);
    }

    /**
     * 流式更新AI卡片内容（指定key）
     *
     * @description 使用指定的key对AI卡片进行流式内容更新，支持全量替换模式
     * @param outTrackId 卡片外部跟踪ID
     * @param key 更新内容对应的键名
     * @param fullContent 完整的更新内容
     * @param isFinalize 是否为最终更新
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
     * 按键更新卡片公共数据
     *
     * @description 根据指定的键值对增量更新AI卡片的公共数据区域
     * @param outTrackId 卡片外部跟踪ID
     * @param updates 需要更新的键值对
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
     * 按键更新卡片用户私有数据
     *
     * @description 根据指定的键值对增量更新AI卡片中指定用户的私有数据区域
     * @param outTrackId 卡片外部跟踪ID
     * @param userId 目标用户ID
     * @param updates 需要更新的键值对
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
