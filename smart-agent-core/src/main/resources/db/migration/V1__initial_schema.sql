-- Flyway migration V1: Initial schema
-- smart-agent-framework database initialization
-- Database: MySQL 8.x

CREATE TABLE IF NOT EXISTS agent_session (
    id              BIGINT          NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    gmt_create      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    user_id         VARCHAR(64)     NOT NULL DEFAULT '',
    agent_name      VARCHAR(128)    NOT NULL DEFAULT '',
    session_id      VARCHAR(128)    NOT NULL DEFAULT '',
    data_key        VARCHAR(256)    NOT NULL DEFAULT '',
    data_value      MEDIUMTEXT,
    UNIQUE INDEX uk_user_agent_session_key (user_id, agent_name, session_id, data_key),
    INDEX idx_session_user (user_id, session_id),
    INDEX idx_session_agent (agent_name, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
