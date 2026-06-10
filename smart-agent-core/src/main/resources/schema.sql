-- ============================================================
-- smart-agent-framework 数据库初始化脚本
-- 数据库: MySQL 8.x
-- ============================================================

CREATE DATABASE IF NOT EXISTS smart_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE smart_agent;

-- -----------------------------------------------------------
-- 1. agent_session — Agent 会话记忆存储
--    compositeKey = userId::sessionId, 按 dataKey 存储多条数据
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_session (
    id              BIGINT          NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    gmt_create      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    user_id         VARCHAR(64)     NOT NULL DEFAULT '',
    agent_name      VARCHAR(128)    NOT NULL DEFAULT '',
    session_id      VARCHAR(128)    NOT NULL DEFAULT '',
    data_key        VARCHAR(256)    NOT NULL DEFAULT '',
    data_value      MEDIUMTEXT,
    INDEX idx_session_user (user_id, session_id),
    INDEX idx_session_agent (agent_name, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- 2. agent_chat_message — 用户与 Agent 的对话消息
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_chat_message (
    id                  BIGINT          NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    gmt_create          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    session_id          VARCHAR(128)    NOT NULL DEFAULT '',
    user_id             VARCHAR(64)     NOT NULL DEFAULT '',
    business_name       VARCHAR(128)    NOT NULL DEFAULT '',
    user_input          MEDIUMTEXT,
    agent_output        MEDIUMTEXT,
    channel             VARCHAR(32)     NOT NULL DEFAULT '',
    status              INT             NOT NULL DEFAULT 0,
    feedback_type       INT             DEFAULT NULL,
    conversation_id     VARCHAR(128)    NOT NULL DEFAULT '',
    conversation_type   VARCHAR(16)     NOT NULL DEFAULT '',
    process_query_key   VARCHAR(256)    DEFAULT NULL,
    feedback_comment    TEXT            DEFAULT NULL,
    INDEX idx_msg_session (session_id),
    INDEX idx_msg_user_conv (user_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- 3. agent_conversation_session_mapper — 会话与 Session 的映射
--    唯一索引: (user_id, conversation_id, business_name)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_conversation_session_mapper (
    id                  BIGINT          NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    gmt_create          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    user_id             VARCHAR(64)     NOT NULL DEFAULT '',
    conversation_id     VARCHAR(128)    NOT NULL DEFAULT '',
    current_session_id  VARCHAR(128)    NOT NULL DEFAULT '',
    conversation_type   VARCHAR(16)     NOT NULL DEFAULT '2',
    business_name       VARCHAR(128)    NOT NULL DEFAULT '',
    UNIQUE INDEX uk_user_conv_biz (user_id, conversation_id, business_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
