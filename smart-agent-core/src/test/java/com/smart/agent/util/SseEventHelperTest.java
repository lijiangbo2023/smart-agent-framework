package com.smart.agent.util;

import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SseEventHelperTest {

    // ========== extractTextContent ==========

    @Test
    void extractTextContent_directText() {
        Msg msg = Msg.builder().role(MsgRole.ASSISTANT).textContent("hello world").build();
        Event event = new Event(EventType.AGENT_RESULT, msg, true);

        assertEquals("hello world", SseEventHelper.extractTextContent(event));
    }

    @Test
    void extractTextContent_emptyText_returnsEmpty() {
        Msg msg = Msg.builder().role(MsgRole.ASSISTANT).textContent("").build();
        Event event = new Event(EventType.REASONING, msg, false);

        String result = SseEventHelper.extractTextContent(event);
        assertTrue(result == null || result.isEmpty());
    }

    @Test
    void extractTextContent_stripsSessionIdPrefix() {
        Msg msg = Msg.builder().role(MsgRole.ASSISTANT)
                .textContent("session_id: abc123\n\n实际内容").build();
        Event event = new Event(EventType.AGENT_RESULT, msg, true);

        assertEquals("实际内容", SseEventHelper.extractTextContent(event));
    }

    @Test
    void extractTextContent_sessionIdWithDash() {
        Msg msg = Msg.builder().role(MsgRole.ASSISTANT)
                .textContent("session_id: abc-def-123\n\n实际内容").build();
        Event event = new Event(EventType.AGENT_RESULT, msg, true);

        assertEquals("实际内容", SseEventHelper.extractTextContent(event));
    }

    @Test
    void extractTextContent_noSessionIdPrefix_unchanged() {
        Msg msg = Msg.builder().role(MsgRole.ASSISTANT).textContent("普通文本").build();
        Event event = new Event(EventType.AGENT_RESULT, msg, true);

        assertEquals("普通文本", SseEventHelper.extractTextContent(event));
    }

    @Test
    void extractTextContent_fromToolResultBlock() {
        TextBlock textBlock = TextBlock.builder().text("工具结果文本").build();
        ToolResultBlock toolResult = ToolResultBlock.builder()
                .id("call_123").name("test_tool").output(List.of(textBlock)).build();
        Msg msg = Msg.builder().role(MsgRole.TOOL).content(List.of(toolResult)).build();
        Event event = new Event(EventType.TOOL_RESULT, msg, false);

        assertEquals("工具结果文本", SseEventHelper.extractTextContent(event));
    }

    @Test
    void extractTextContent_multipleToolResultBlocks_joined() {
        TextBlock text1 = TextBlock.builder().text("第一段").build();
        TextBlock text2 = TextBlock.builder().text("第二段").build();
        ToolResultBlock tr1 = ToolResultBlock.builder()
                .id("call_1").name("tool1").output(List.of(text1)).build();
        ToolResultBlock tr2 = ToolResultBlock.builder()
                .id("call_2").name("tool2").output(List.of(text2)).build();
        Msg msg = Msg.builder().role(MsgRole.TOOL).content(List.of(tr1, tr2)).build();
        Event event = new Event(EventType.TOOL_RESULT, msg, false);

        assertEquals("第一段\n第二段", SseEventHelper.extractTextContent(event));
    }

    // ========== extractAgentName ==========

    @Test
    void extractAgentName_fromMetadata() {
        ToolResultBlock toolResult = ToolResultBlock.builder()
                .id("call_1").name("demo_tool")
                .output(List.of())
                .metadata(Map.of("subagent_name", "DemoAgent"))
                .build();
        Msg msg = Msg.builder().role(MsgRole.TOOL).content(List.of(toolResult)).build();
        Event event = new Event(EventType.TOOL_RESULT, msg, false);

        assertEquals("DemoAgent", SseEventHelper.extractAgentName(event));
    }

    @Test
    void extractAgentName_fromToolName() {
        ToolResultBlock toolResult = ToolResultBlock.builder()
                .id("call_1").name("demo_assistant")
                .output(List.of())
                .build();
        Msg msg = Msg.builder().role(MsgRole.TOOL).content(List.of(toolResult)).build();
        Event event = new Event(EventType.TOOL_RESULT, msg, false);

        assertEquals("demo_assistant", SseEventHelper.extractAgentName(event));
    }

    @Test
    void extractAgentName_fromMsgName() {
        Msg msg = Msg.builder().role(MsgRole.ASSISTANT).name("Supervisor").textContent("hi").build();
        Event event = new Event(EventType.AGENT_RESULT, msg, true);

        assertEquals("Supervisor", SseEventHelper.extractAgentName(event));
    }

    @Test
    void extractAgentName_systemName_returnsNull() {
        Msg msg = Msg.builder().role(MsgRole.ASSISTANT).name("system").textContent("hi").build();
        Event event = new Event(EventType.AGENT_RESULT, msg, true);

        assertNull(SseEventHelper.extractAgentName(event));
    }

    @Test
    void extractAgentName_noName_returnsNull() {
        Msg msg = Msg.builder().role(MsgRole.ASSISTANT).textContent("hi").build();
        Event event = new Event(EventType.AGENT_RESULT, msg, true);

        assertNull(SseEventHelper.extractAgentName(event));
    }

    // ========== StreamChunk record ==========

    @Test
    void streamChunk_recordFields() {
        SseEventHelper.StreamChunk chunk = new SseEventHelper.StreamChunk("AGENT_RESULT", "hello", true, "Agent1");
        assertEquals("AGENT_RESULT", chunk.type());
        assertEquals("hello", chunk.content());
        assertTrue(chunk.isLast());
        assertEquals("Agent1", chunk.agentName());
    }
}
