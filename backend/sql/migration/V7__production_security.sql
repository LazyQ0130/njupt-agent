USE njupt_ai_assistant;

ALTER TABLE conversation
    ADD COLUMN anonymous_session_id VARCHAR(36) NULL AFTER user_id,
    ADD COLUMN expire_time DATETIME(6) NULL AFTER updated_time,
    ADD KEY idx_conversation_session_updated
        (anonymous_session_id, updated_time),
    ADD KEY idx_conversation_expire_time (expire_time);

UPDATE conversation
SET anonymous_session_id = UUID(),
    expire_time = DATE_ADD(updated_time, INTERVAL 90 DAY)
WHERE anonymous_session_id IS NULL OR expire_time IS NULL;

ALTER TABLE conversation
    MODIFY anonymous_session_id VARCHAR(36) NOT NULL,
    MODIFY expire_time DATETIME(6) NOT NULL;

ALTER TABLE chat_history
    ADD COLUMN anonymous_session_id VARCHAR(36) NULL AFTER user_id,
    ADD COLUMN conversation_id BIGINT NULL AFTER anonymous_session_id,
    ADD KEY idx_chat_history_session_created
        (anonymous_session_id, created_time),
    ADD KEY idx_chat_history_conversation (conversation_id);

UPDATE chat_history
SET anonymous_session_id = UUID()
WHERE anonymous_session_id IS NULL;

ALTER TABLE chat_history
    MODIFY anonymous_session_id VARCHAR(36) NOT NULL;

CREATE TABLE IF NOT EXISTS operation_log (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    admin_id     BIGINT        NOT NULL,
    operation    VARCHAR(64)   NOT NULL,
    target       VARCHAR(1000) NOT NULL,
    created_time DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_operation_log_admin_created (admin_id, created_time),
    KEY idx_operation_log_created (created_time)
) ENGINE = InnoDB;
