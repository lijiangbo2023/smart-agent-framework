package com.smart.agent.agent.service;

import com.smart.agent.nacos.AgentPromptManager;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.OpenAIChatModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 消息意图过滤服务
 *
 * @description 基于轻量级模型对用户消息进行意图分类，判断消息是否需要AI助手处理（PROCESS）或跳过（SKIP），用于过滤纯确认、通知、转达等无需AI回复的消息
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Slf4j
@Service
public class MessageIntentFilterService {

    private static final String AGENT_NAME = "MessageIntentFilter";

    private static final String FALLBACK_PROMPT = """
            你是一个消息意图分类器。判断用户消息是否需要AI助手处理。

            只有以下情况回复SKIP，其余所有情况回复PROCESS：
            - 纯确认/知晓回复：如"收到"、"好的"、"OK"、"嗯"、"了解"
            - 通知/转达类信息：如"最近XX出了问题 请相关团队重点关注"
            - 对他人/团队的指令：如"看下这个"、"帮我转给XX"
            - 工作汇报/进度同步：如"XX相关反馈均已处理"

            注意：数字、字母、序号选项，一律回复PROCESS。

            请只回复一个词：SKIP 或 PROCESS
            """;

    private final OpenAIChatModel fastModel;
    private final AgentPromptManager agentPromptManager;

    public MessageIntentFilterService(@Qualifier("lightModel") OpenAIChatModel fastModel,
                                      AgentPromptManager agentPromptManager) {
        this.fastModel = fastModel;
        this.agentPromptManager = agentPromptManager;
    }

    @PostConstruct
    private void init() {
        agentPromptManager.register(AGENT_NAME, FALLBACK_PROMPT);
    }

    /**
     * 判断用户消息是否应跳过处理
     *
     * @description 调用轻量级模型对用户消息进行意图分类，返回是否应跳过。当模型调用失败时默认不跳过（返回false）
     * @param userMessage 用户消息文本
     * @return true表示消息应跳过，false表示消息需要处理
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public boolean shouldSkip(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        try {
            Msg systemMsg = Msg.builder()
                    .role(MsgRole.SYSTEM)
                    .textContent(agentPromptManager.getPrompt(AGENT_NAME))
                    .build();
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(userMessage)
                    .build();

            String result = fastModel.stream(List.of(systemMsg, userMsg), null, null)
                    .filter(Objects::nonNull)
                    .map(ChatResponse::getContent)
                    .filter(Objects::nonNull)
                    .flatMapIterable(blocks -> blocks)
                    .filter(block -> block instanceof TextBlock)
                    .map(block -> ((TextBlock) block).getText())
                    .filter(Objects::nonNull)
                    .reduce("", String::concat)
                    .block();

            log.info("MessageIntentFilter: message=[{}], result=[{}]", userMessage, result);
            return result != null && result.trim().toUpperCase().contains("SKIP");
        } catch (Exception e) {
            log.warn("MessageIntentFilter failed, defaulting to PROCESS: {}", e.getMessage());
            return false;
        }
    }
}
