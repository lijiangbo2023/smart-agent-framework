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
 * SSE event push utility.
 *
 * @description Encapsulates the building and sending logic for Server-Sent Events, supporting extraction of text content and agent names from agent events for streaming response delivery
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
     * Send an SSE event.
     *
     * @description Converts an agent event into SSE data and pushes it to the client, automatically masking sensitive information in text content
     * @param emitter the SSE emitter
     * @param event the agent event
     * @param objectMapper the JSON serializer
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
     * Extract text content.
     *
     * @description Extracts text content from an agent event, preferring direct text, then concatenating text from tool result blocks
     * @param event the agent event
     * @return the extracted text content, or an empty string if no text is found
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
     * Extract agent name.
     *
     * @description Extracts the agent name from the event by trying, in order: tool result metadata, tool name, and message name
     * @param event the agent event
     * @return the agent name, or null if it cannot be extracted
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

    /**
     * Strip the session_id prefix from text content.
     *
     * @description Removes the "session_id: xxx\n\n" prefix that AgentScope prepends to messages
     * @param text the text to strip
     * @return the text without the session_id prefix
     * @author Jiangbo Li
     * @date 2026-06-18
     */
    public static String stripSessionIdPrefix(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replaceFirst(SESSION_ID_PREFIX_PATTERN, "");
    }

    /**
     * Stream chunk record.
     *
     * @description Encapsulates a single data chunk for SSE delivery, containing the event type, text content, whether it is the last chunk, and the agent name
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    public record StreamChunk(String type, String content, boolean isLast, String agentName) {
    }
}
