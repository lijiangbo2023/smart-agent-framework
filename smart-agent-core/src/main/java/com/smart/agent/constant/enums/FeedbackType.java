package com.smart.agent.constant.enums;

import lombok.Getter;

/**
 * 反馈类型枚举
 *
 * @description 用户对消息的反馈类型，包括未操作、点赞、点踩
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
@Getter
public enum FeedbackType {

    NONE(0, "未操作"),
    LIKE(1, "赞"),
    DISLIKE(2, "踩");

    private final int code;
    private final String description;

    FeedbackType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据编码获取反馈类型
     *
     * @description 通过整型编码值查找对应的反馈类型枚举实例，若编码不存在则抛出异常
     * @param code 反馈类型编码
     * @return 对应的反馈类型枚举实例
     * @author Jiangbo Li
     * @date 2026-06-10
     */
    public static FeedbackType fromCode(int code) {
        for (FeedbackType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown FeedbackType code: " + code);
    }
}
