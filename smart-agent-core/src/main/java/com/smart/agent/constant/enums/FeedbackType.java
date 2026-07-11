package com.smart.agent.constant.enums;

import lombok.Getter;

/**
 * Feedback type enum.
 *
 * @description User feedback types for messages, including no action, like, and dislike
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
     * Get feedback type by code.
     *
     * @description Looks up the corresponding feedback type enum instance by integer code; throws an exception if the code does not exist
     * @param code the feedback type code
     * @return the corresponding feedback type enum instance
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
