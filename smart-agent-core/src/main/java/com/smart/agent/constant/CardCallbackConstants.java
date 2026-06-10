package com.smart.agent.constant;

import java.util.List;

/**
 * 卡片回调常量类
 *
 * @description 定义钉钉互动卡片回调场景中使用的常量，包括动作标识、状态值、参数键名和默认值等
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
public final class CardCallbackConstants {

    private CardCallbackConstants() {
    }

    /**
     * 动作标识常量类
     *
     * @description 定义卡片回调中各交互动作的唯一标识符，如删除记忆、点赞、点踩和提交反馈
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    public static final class ActionIds {
        public static final String DELETE_MEMORY = "deleteMemory";
        public static final String LIKE = "like";
        public static final String DISLIKE = "dislike";
        public static final String SUBMIT_FEEDBACK = "submitFeedback";

        private ActionIds() {
        }
    }

    /**
     * 删除状态常量类
     *
     * @description 定义记忆删除操作的结果状态，包括成功、失败和无权限
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    public static final class DeleteStatus {
        public static final String SUCCESS = "success";
        public static final String FAILED = "failed";
        public static final String DENIED = "denied";

        private DeleteStatus() {
        }
    }

    /**
     * 反馈结果常量类
     *
     * @description 定义用户反馈操作的结果状态，包括成功、失败和无权限
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    public static final class FeedbackResult {
        public static final String SUCCESS = "success";
        public static final String FAILED = "failed";
        public static final String DENIED = "denied";

        private FeedbackResult() {
        }
    }

    /**
     * 参数键名常量类
     *
     * @description 定义卡片回调请求和响应中使用的参数键名，用于数据传递和状态管理
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    public static final class ParamKeys {
        public static final String OWNER_USER_ID = "ownerUserId";
        public static final String BUSINESS_NAME = "businessName";
        public static final String MESSAGE_ID = "messageId";
        public static final String FEEDBACK_STATUS = "feedbackStatus";
        public static final String DELETE_STATUS = "deleteStatus";
        public static final String DELETE_HINT = "deleteHint";
        public static final String FEEDBACK_HINT = "feedbackHint";
        public static final String FEEDBACK_RESULT = "feedbackResult";
        public static final String FEEDBACK_COMMENT = "feedbackComment";
        public static final String FEEDBACK_COMMENT_STATUS = "feedbackCommentStatus";

        private ParamKeys() {
        }
    }

    /**
     * 反馈评论状态常量类
     *
     * @description 定义反馈评论输入框的显示状态，包括正常显示和禁用
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    public static final class FeedbackCommentStatus {
        public static final String NORMAL = "normal";
        public static final String DISABLED = "disabled";

        private FeedbackCommentStatus() {
        }
    }

    /**
     * 默认值常量类
     *
     * @description 定义卡片回调各状态字段的初始默认值
     * @author Jiangbo Li
     * @date 2026-06-10
     * @version 1.0
     */
    public static final class Defaults {
        public static final String FEEDBACK_STATUS_NONE = "none";
        public static final String DELETE_STATUS_NORMAL = "normal";
        public static final String FEEDBACK_COMMENT_STATUS_HIDDEN = "hidden";

        private Defaults() {
        }
    }

    public static final List<String> PRESERVED_PRIVATE_FIELDS = List.of(
            ParamKeys.FEEDBACK_STATUS,
            ParamKeys.DELETE_STATUS,
            ParamKeys.FEEDBACK_COMMENT_STATUS
    );
}
