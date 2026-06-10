package com.smart.agent.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * SSE事件推送工具类
 *
 * @description 封装Server-Sent Events事件的构建与发送逻辑，支持从智能体事件中提取文本内容和代理名称，实现流式响应推送
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
public final class SseEventHelper {

    private static final String SESSION_ID_PREFIX_PATTERN = "session_id: [\\w-]+\\n\\n";

    private SseEventHelper() {
    }

    /**
     * 发送SSE事件
     *
     * @description 将智能体事件转换为SSE数据并推送给客户端，自动对文本内容进行敏感信息脱敏处理
     * @param emitter SSE发射器
     * @param event 智能体事件
     * @param objectMapper JSON序列化器
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static void sendEvent(SseEmitter emitter, Event event, ObjectMapper objectMapper) {
        try {
            if (event.getMessage() == null) {
                return;
            }
            String textContent = extractTextContent(event);
            String agentName = extractAgentName(event);

            if (textContent.isEmpty() && agentName == null) {
                return;
            }

            String data = objectMapper.writeValueAsString(new StreamChunk(
                    event.getType().name(),
                    SensitiveUtils.mask(textContent),
                    event.isLast(),
                    agentName
            ));
            emitter.send(SseEmitter.event()
                    .name(event.getType().name().toLowerCase())
                    .data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("Failed to send SSE event: {}", e.getMessage());
            emitter.completeWithError(e);
        }
    }

    /**
     * 提取文本内容
     *
     * @description 从智能体事件中提取文本内容，优先获取直接文本，其次从工具结果块中拼接文本
     * @param event 智能体事件
     * @return 提取到的文本内容，无文本时返回空字符串
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static String extractTextContent(Event event) {
        Msg message = event.getMessage();
        String directText = message.getTextContent();
        if (directText != null && !directText.isEmpty()) {
            return stripSessionIdPrefix(directText);
        }

        StringBuilder textFromToolResults = new StringBuilder();
        for (ContentBlock block : message.getContent()) {
            if (block instanceof ToolResultBlock toolResult) {
                for (ContentBlock outputBlock : toolResult.getOutput()) {
                    if (outputBlock instanceof TextBlock textBlock) {
                        if (!textFromToolResults.isEmpty()) {
                            textFromToolResults.append("\n");
                        }
                        textFromToolResults.append(textBlock.getText());
                    }
                }
            }
        }
        return stripSessionIdPrefix(textFromToolResults.toString());
    }

    /**
     * 提取智能体名称
     *
     * @description 从事件中提取智能体名称，依次尝试从工具结果元数据、工具名称和消息名称中获取
     * @param event 智能体事件
     * @return 智能体名称，无法提取时返回null
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static String extractAgentName(Event event) {
        Msg message = event.getMessage();
        for (ContentBlock block : message.getContent()) {
            if (block instanceof ToolResultBlock toolResult) {
                Map<String, Object> metadata = toolResult.getMetadata();
                if (metadata != null && metadata.containsKey("subagent_name")) {
                    return (String) metadata.get("subagent_name");
                }
                String toolName = toolResult.getName();
                if (toolName != null && !toolName.isEmpty()) {
                    return toolName;
                }
            }
        }
        String name = message.getName();
        if (name != null && !"system".equals(name)) {
            return name;
        }
        return null;
    }

    private static String stripSessionIdPrefix(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replaceAll(SESSION_ID_PREFIX_PATTERN, "");
    }

    /**
     * 流式数据块记录
     *
     * @description 封装SSE推送的单个数据块，包含事件类型、文本内容、是否为最后一块以及智能体名称
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    public record StreamChunk(String type, String content, boolean isLast, String agentName) {
    }
}
