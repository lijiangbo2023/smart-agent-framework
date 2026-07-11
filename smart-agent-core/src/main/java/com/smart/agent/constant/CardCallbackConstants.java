package com.smart.agent.constant;

import java.util.List;

/**
 * Card callback constants.
 *
 * @description Defines constants used in DingTalk interactive card callback scenarios, including action identifiers, status values, parameter keys, and default values
 * @author Jiangbo Li
 * @date 2026-06-10
 * @version 1.0
 */
public final class CardCallbackConstants {

    private CardCallbackConstants() {
    }

    /**
     * Action ID constants.
     *
     * @description Defines unique identifiers for each interactive action in card callbacks, such as delete memory, like, dislike, and submit feedback
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
     * Delete status constants.
     *
     * @description Defines result statuses for the memory deletion operation, including success, failure, and denied
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
     * Feedback result constants.
     *
     * @description Defines result statuses for user feedback operations, including success, failure, and denied
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
     * Parameter key constants.
     *
     * @description Defines parameter keys used in card callback requests and responses for data passing and state management
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
     * Feedback comment status constants.
     *
     * @description Defines the display states of the feedback comment input field, including normal and disabled
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
     * Default value constants.
     *
     * @description Defines the initial default values for card callback status fields
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
