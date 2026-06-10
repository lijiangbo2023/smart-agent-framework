package com.smart.agent.constant.enums;

import lombok.Getter;

/**
 * 消息渠道枚举
 *
 * @description 定义消息的来源渠道，包括钉钉机器人和HTTP接口
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
