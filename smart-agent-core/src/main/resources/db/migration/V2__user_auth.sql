-- Flyway migration V2: User authentication tables

CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT          NOT NULL AUTO_INCREMENT  PRIMARY KEY,
    gmt_create      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modified    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    username        VARCHAR(64)     NOT NULL DEFAULT '',
    password_hash   VARCHAR(256)    NOT NULL DEFAULT '',
    nickname        VARCHAR(128)    NOT NULL DEFAULT '',
    email           VARCHAR(128)    NOT NULL DEFAULT '',
    avatar_url      VARCHAR(512)    NOT NULL DEFAULT '',
    status          INT             NOT NULL DEFAULT 1 COMMENT '1=active, 0=disabled',
    last_login_at   DATETIME        DEFAULT NULL,
    UNIQUE INDEX uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
