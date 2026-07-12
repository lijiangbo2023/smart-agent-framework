package com.smart.agent.callback.chatbot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.smart.agent.constant.CardCallbackConstants;
import com.smart.agent.service.AgentChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DingTalk AI card button callback handler.
 *
 * @description Handles card interaction callbacks (like/dislike, regenerate, delete memory)
 *              received via Stream topic /v1.0/card/instances/callback.
 *              Callback response uses userPrivateData to update per-user card state.
 * @author Jiangbo Li
 * @date 2025-07-12
 * @version 1.0
 */
@Slf4j
@Component
public class DingTalkCardCallbackHandler implements OpenDingTalkCallbackListener<JSONObject, JSONObject> {

    private final AgentChatMessageService agentChatMessageService;

    public DingTalkCardCallbackHandler(AgentChatMessageService agentChatMessageService) {
        this.agentChatMessageService = agentChatMessageService;
    }

    @Override
    public JSONObject execute(JSONObject request) {
        if (request == null) {
            log.warn("Received null card callback request");
            return new JSONObject();
        }

        try {
            String outTrackId = request.getString("outTrackId");
            String userId = request.getString("userId");
            String content = request.getString("content");

            log.info("Card callback: type={}, outTrackId={}, userId={}", request.getString("type"), outTrackId, userId);

            // Parse actionIds from content
            List<String> actionIds = parseActionIds(content);
            if (actionIds == null || actionIds.isEmpty()) {
                log.debug("No actionIds in card callback, outTrackId={}", outTrackId);
                return new JSONObject();
            }

            // Extract params from cardPrivateData
            Map<String, Object> params = extractParams(content);

            log.info("Card callback actions: outTrackId={}, userId={}, actionIds={}, params={}",
                    outTrackId, userId, actionIds, params != null ? params.keySet() : "null");

            // Handle like/dislike
            if (actionIds.contains("sys_action_like_component")) {
                return handleFeedback(userId, params, outTrackId);
            }

            log.debug("Unhandled card actionIds: {}", actionIds);
        } catch (Exception e) {
            log.error("Card callback execution failed", e);
        }
        return new JSONObject();
    }

    @SuppressWarnings("unchecked")
    private List<String> parseActionIds(String content) {
        if (content == null || content.isEmpty()) return null;
        try {
            JSONObject parsed = JSON.parseObject(content);
            if (parsed == null) return null;
            JSONObject cardPrivateData = parsed.getJSONObject("cardPrivateData");
            if (cardPrivateData == null) return null;
            return cardPrivateData.getList("actionIds", String.class);
        } catch (Exception e) {
            log.debug("Failed to parse card callback actionIds: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParams(String content) {
        if (content == null || content.isEmpty()) return null;
        try {
            JSONObject parsed = JSON.parseObject(content);
            if (parsed == null) return null;
            JSONObject cardPrivateData = parsed.getJSONObject("cardPrivateData");
            if (cardPrivateData == null) return null;
            return cardPrivateData.getObject("params", Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private JSONObject handleFeedback(String clickerUserId, Map<String, Object> params, String outTrackId) {
        if (params == null) return new JSONObject();

        // Extract like/dislike value
        String variableValue = getStringParam(params, "variableValue");
        String currentValue = getStringParam(params, "variableKey");
        String variableKey = currentValue != null ? currentValue : "node_ocmrh9p463a_likeOrDislike";

        if (variableValue == null) return new JSONObject();

        // Normalize: dingtalk callback sends "like" or "dislike", or "none" to cancel
        String action = variableValue;
        String currentStatus = getStringParam(params, CardCallbackConstants.ParamKeys.FEEDBACK_STATUS);
        if (currentStatus == null) currentStatus = CardCallbackConstants.Defaults.FEEDBACK_STATUS_NONE;

        Long messageId = getLongParam(params, CardCallbackConstants.ParamKeys.MESSAGE_ID);

        try {
            String newStatus = agentChatMessageService.updateFeedback(messageId, action, currentStatus);
            log.info("Feedback processed: messageId={}, action={}, current={}, new={}", messageId, action, currentStatus, newStatus);

            // Build response with updated private data
            JSONObject response = new JSONObject();
            JSONObject userPrivateData = new JSONObject();
            JSONObject cardParamMap = new JSONObject();
            cardParamMap.put(CardCallbackConstants.ParamKeys.FEEDBACK_STATUS, newStatus);
            cardParamMap.put(variableKey, newStatus);  // update the card template variable
            userPrivateData.put("cardParamMap", cardParamMap);
            response.put("userPrivateData", userPrivateData);
            return response;
        } catch (Exception e) {
            log.error("handleFeedback failed: messageId={}, action={}", messageId, action, e);
            return new JSONObject();
        }
    }

    private String getStringParam(Map<String, Object> params, String key) {
        if (params == null || !params.containsKey(key)) return null;
        return String.valueOf(params.get(key));
    }

    private Long getLongParam(Map<String, Object> params, String key) {
        if (params == null || !params.containsKey(key)) return null;
        try {
            return Long.parseLong(String.valueOf(params.get(key)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
