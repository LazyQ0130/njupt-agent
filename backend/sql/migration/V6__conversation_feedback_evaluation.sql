USE njupt_ai_assistant;

CREATE TABLE IF NOT EXISTS conversation (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NULL,
    title        VARCHAR(255) NOT NULL DEFAULT '新对话',
    created_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_conversation_user_updated (user_id, updated_time),
    CONSTRAINT fk_conversation_user
        FOREIGN KEY (user_id) REFERENCES `user` (id)
        ON DELETE SET NULL
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS message (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id  BIGINT      NOT NULL,
    role             VARCHAR(16) NOT NULL,
    content          LONGTEXT    NOT NULL,
    sources          LONGTEXT    NULL,
    chat_history_id  BIGINT      NULL,
    created_time     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_message_conversation_created (conversation_id, created_time),
    KEY idx_message_chat_history (chat_history_id),
    CONSTRAINT fk_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_message_chat_history
        FOREIGN KEY (chat_history_id) REFERENCES chat_history (id)
        ON DELETE SET NULL
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS answer_feedback (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    chat_history_id  BIGINT        NOT NULL,
    user_feedback    VARCHAR(16)   NOT NULL,
    reason           VARCHAR(1000) NULL,
    created_time     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_feedback_chat_history (chat_history_id),
    KEY idx_feedback_type_created (user_feedback, created_time),
    CONSTRAINT fk_feedback_chat_history
        FOREIGN KEY (chat_history_id) REFERENCES chat_history (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS evaluation_result (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    question      VARCHAR(2000) NOT NULL,
    answer        LONGTEXT      NOT NULL,
    source_match  BOOLEAN       NOT NULL,
    score         INT           NOT NULL,
    created_time  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_evaluation_created (created_time)
) ENGINE = InnoDB;
