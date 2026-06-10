package com.smart.agent.constant.enums;

import lombok.Getter;

/**
 * 消息状态枚举
 *
 * @description 定义消息的处理状态，包括处理中、成功和异常
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Getter
public enum MessageStatus {

    PROCESSING(1, "处理中"),
    SUCCESS(2, "成功"),
    ERROR(-1, "异常");

    private final int code;
    private final String description;

    MessageStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }
}
