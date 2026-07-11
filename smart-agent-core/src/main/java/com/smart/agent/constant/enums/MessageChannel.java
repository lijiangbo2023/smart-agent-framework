package com.smart.agent.constant.enums;

import lombok.Getter;

/**
 * Message channel enum.
 *
 * @description Defines the source channels of messages, including DingTalk bot and HTTP API
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Getter
public enum MessageChannel {

    DINGTALK("DING_ROBOT", "钉钉机器人"),
    HTTP("API", "HTTP接口");

    private final String code;
    private final String description;

    MessageChannel(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
