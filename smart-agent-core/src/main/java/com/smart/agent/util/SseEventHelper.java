package com.smart.agent.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SSE event push utility.
 *
 * @description Encapsulates the building and sending logic for Server-Sent Events, supporting extraction
 *              of text content, tool-use blocks, and nested sub-agent events from agent events.
 *              Emits fine-grained event types (REASONING, TOOL_USE, TOOL_RESULT, SUB_REASONING,
 *              SUB_RESULT, AGENT_RESULT) so the frontend can render a full routing timeline.
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.1
 */
@Slf4j
public final class SseEventHelper {

    private static final String SESSION_ID_PREFIX_PATTERN = "session_id: [\\w-]+\\n\\n";

    private SseEventHelper() {
    }

    /**
     * Send SSE events for an agent event.
     *
     * @description Converts an agent event into one or more SSE data chunks.
     *              For REASONING events containing ToolUseBlock, a TOOL_USE event is emitted first.
     *              For TOOL_RESULT events containing nested sub-agent JSON, individual SUB_REASONING
     *              and SUB_RESULT events are emitted instead of wrapping everything as one event.
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
            Msg message = event.getMessage();
            String eventType = event.getType().name();
            String agentName = extractAgentName(event);

            // ── REASONING: emit tool-use blocks first, then reasoning text ──
            if ("REASONING".equals(eventType)) {
                List<String> toolNames = extractToolUseNames(message);
                for (String toolName : toolNames) {
                    emit(emitter, objectMapper, "TOOL_USE", toolName, false, agentName);
                }
                String text = extractDirectText(message);
                if (text != null && !text.isEmpty()) {
                    emit(emitter, objectMapper, "REASONING", text, false, agentName);
                }
                return;
            }

            // ── TOOL_RESULT: check for nested sub-agent events ──
            if ("TOOL_RESULT".equals(eventType)) {
                String text = extractTextContent(event);

                // Nested sub-agent JSON: parse and emit individual sub-events
                if (text != null && text.startsWith("{\"type\":")) {
                    emitNestedSubEvents(emitter, objectMapper, text, event.isLast(), agentName);
                } else {
                    // Plain tool result (e.g. final sub-agent output)
                    if (text != null && !text.isEmpty()) {
                        emit(emitter, objectMapper, "TOOL_RESULT", text, event.isLast(), agentName);
                    }
                }
                return;
            }

            // ── AGENT_RESULT and other types ──
            String textContent = extractTextContent(event);
            if (!textContent.isEmpty()) {
                emit(emitter, objectMapper, eventType, textContent, event.isLast(), agentName);
            }

        } catch (IOException e) {
            log.warn("Failed to send SSE event: {}", e.getMessage());
            emitter.completeWithError(e);
        }
    }

    /**
     * Emit a single SSE data chunk.
     */
    private static void emit(SseEmitter emitter, ObjectMapper objectMapper,
                             String type, String content, boolean isLast, String agentName) throws IOException {
        String data = objectMapper.writeValueAsString(new StreamChunk(
                type, SensitiveUtils.mask(content), isLast, agentName));
        emitter.send(SseEmitter.event()
                .data(data, MediaType.APPLICATION_JSON));
        // Also set event name for clients that listen by event type
    }

    /**
     * Parse nested sub-agent JSON and emit individual SUB_REASONING / SUB_RESULT events.
     *
     * @description The nested JSON has the form:
     *              {"type":"REASONING"|"AGENT_RESULT","agentName":"...","message":{"content":[...]}}
     *              Each text block in the content array becomes a SUB_REASONING or SUB_RESULT event,
     *              and each tool_use block becomes a TOOL_USE event.
     */
    private static void emitNestedSubEvents(SseEmitter emitter, ObjectMapper objectMapper,
                                            String jsonText, boolean isLast, String fallbackAgentName) {
        try {
            Map<String, Object> nested = objectMapper.readValue(jsonText,
                    new TypeReference<Map<String, Object>>() {});
            String nestedType = (String) nested.getOrDefault("type", "");
            String nestedAgent = (String) nested.getOrDefault("agentName", fallbackAgentName);
            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) nested.get("message");
            if (message == null) {
                // No sub-message — emit as plain TOOL_RESULT
                String text = nested.containsKey("content") ? String.valueOf(nested.get("content")) : jsonText;
                emit(emitter, objectMapper, "TOOL_RESULT", text, isLast, nestedAgent);
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blocks = (List<Map<String, Object>>) message.get("content");
            if (blocks == null || blocks.isEmpty()) {
                return;
            }

            // Determine the phase type for text blocks
            boolean isReasoning = "REASONING".equals(nestedType);
            String textEventType = isReasoning ? "SUB_REASONING" : "SUB_RESULT";

            StringBuilder accumulatedText = new StringBuilder();
            for (Map<String, Object> block : blocks) {
                String blockType = (String) block.get("type");
                if ("tool_use".equals(blockType)) {
                    // Flush accumulated text first
                    if (accumulatedText.length() > 0) {
                        emit(emitter, objectMapper, textEventType, accumulatedText.toString(), false, nestedAgent);
                        accumulatedText.setLength(0);
                    }
                    String toolName = (String) block.get("name");
                    if (toolName != null && !"__fragment__".equals(toolName)) {
                        emit(emitter, objectMapper, "TOOL_USE", toolName, false, nestedAgent);
                    }
                } else if ("text".equals(blockType)) {
                    String text = (String) block.get("text");
                    if (text != null && !text.isEmpty()) {
                        accumulatedText.append(text);
                    }
                }
            }
            // Flush remaining text
            if (accumulatedText.length() > 0) {
                emit(emitter, objectMapper, textEventType, accumulatedText.toString(), isLast, nestedAgent);
            }
        } catch (Exception e) {
            log.warn("Failed to parse nested sub-agent event: {}", e.getMessage());
            // Fallback: emit as plain TOOL_RESULT with raw JSON
            try {
                emit(emitter, objectMapper, "TOOL_RESULT", jsonText, isLast, fallbackAgentName);
            } catch (IOException ignored) {
                // already in catch block, give up
            }
        }
    }

    /**
     * Extract tool-use names from message content blocks.
     *
     * @description Scans the message for ToolUseBlock entries and returns their tool names.
     *              Filters out internal __fragment__ blocks.
     * @param message the agent message
     * @return list of tool names, never null
     */
    private static List<String> extractToolUseNames(Msg message) {
        List<String> names = new ArrayList<>();
        for (ContentBlock block : message.getContent()) {
            if (block instanceof ToolUseBlock toolUse) {
                String name = toolUse.getName();
                if (name != null && !"__fragment__".equals(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * Extract direct text content from a message (from getTextContent(), unprocessed).
     */
    private static String extractDirectText(Msg message) {
        String direct = message.getTextContent();
        if (direct == null || direct.isEmpty()) {
            return null;
        }
        return stripSessionIdPrefix(direct);
    }

    /**
     * Extract text content.
     *
     * @description Extracts text content from an agent event, preferring direct text, then concatenating
     *              text from tool result blocks
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
     * @description Extracts the agent name from the event by trying, in order: tool result metadata,
     *              tool name, and message name
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
     * @description Encapsulates a single data chunk for SSE delivery, containing the event type,
     *              text content, whether it is the last chunk, and the agent name
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    public record StreamChunk(String type, String content, boolean isLast, String agentName) {
    }
}
