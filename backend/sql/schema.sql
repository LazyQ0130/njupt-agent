CREATE DATABASE IF NOT EXISTS njupt_ai_assistant
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE njupt_ai_assistant;

CREATE TABLE IF NOT EXISTS `user` (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username     VARCHAR(64)  NOT NULL COMMENT '用户名',
    password     VARCHAR(255) NOT NULL COMMENT 'BCrypt 等算法生成的密码摘要',
    nickname     VARCHAR(64)  NULL COMMENT '用户昵称',
    avatar       VARCHAR(512) NULL COMMENT '头像地址',
    created_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB COMMENT = '用户';

CREATE TABLE IF NOT EXISTS document (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title         VARCHAR(255) NOT NULL COMMENT '文档标题',
    filename      VARCHAR(255) NOT NULL COMMENT '原始文件名',
    source        VARCHAR(128) NOT NULL COMMENT '来源部门',
    type          VARCHAR(32)  NOT NULL COMMENT 'PDF/DOC/DOCX',
    content       LONGTEXT     NULL COMMENT '解析后的全文，Phase 3 暂为空',
    status        VARCHAR(32)  NOT NULL COMMENT 'UPLOADING/PROCESSING/COMPLETED/FAILED',
    storage_path  VARCHAR(512) NULL COMMENT '上传文件存储路径，网页文档为空',
    file_size     BIGINT       NOT NULL COMMENT '字节数',
    content_type  VARCHAR(128) NULL COMMENT 'MIME 类型',
    source_type   VARCHAR(32)  NOT NULL DEFAULT 'UPLOADED_FILE' COMMENT 'OFFICIAL_WEBSITE/CURATED_OFFICIAL/UPLOADED_FILE',
    source_url    VARCHAR(768) NULL COMMENT '官网原始 URL',
    category      VARCHAR(32)  NULL COMMENT '知识分类（八类枚举）',
    crawl_time    DATETIME(6)  NULL COMMENT '最近采集时间',
    last_updated  DATETIME(6)  NULL COMMENT '来源内容更新时间',
    content_hash  CHAR(64)     NULL COMMENT '清洗后内容 SHA-256',
    vector_document_id CHAR(64) NULL COMMENT 'Knowledge Service 向量文档标识',
    created_time  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '上传时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_source_url (source_url),
    KEY idx_document_status_created_time (status, created_time),
    KEY idx_document_source (source),
    KEY idx_document_source_type_category (source_type, category),
    KEY idx_document_content_hash (content_hash),
    KEY idx_document_vector_document_id (vector_document_id),
    CONSTRAINT chk_document_status
        CHECK (status IN ('UPLOADING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_document_source_type
        CHECK (
            source_type IN (
                'OFFICIAL_WEBSITE',
                'CURATED_OFFICIAL',
                'UPLOADED_FILE'
            )
        ),
    CONSTRAINT chk_document_category
        CHECK (
            category IS NULL OR category IN (
                'NEW_STUDENT', 'ACADEMIC', 'LIFE', 'MAJOR', 'CAREER',
                'SCHOOL_OVERVIEW', 'ORGANIZATION', 'RESEARCH'
            )
        )
) ENGINE = InnoDB COMMENT = '知识库文档';

CREATE TABLE IF NOT EXISTS document_exclusion (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    source_url   VARCHAR(768) NOT NULL,
    reason       VARCHAR(255) NOT NULL,
    created_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_exclusion_source_url (source_url)
) ENGINE = InnoDB COMMENT = '管理员禁止重新入库的官网来源';

CREATE TABLE IF NOT EXISTS chat_history (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT      NULL COMMENT '用户 ID，未登录时可为空',
    anonymous_session_id VARCHAR(36) NOT NULL COMMENT '匿名会话标识',
    conversation_id BIGINT NULL COMMENT '所属多轮会话',
    question     VARCHAR(2000) NOT NULL COMMENT '用户问题',
    answer       LONGTEXT    NOT NULL COMMENT 'AI 回答',
    sources      LONGTEXT    NOT NULL COMMENT '来源 JSON',
    confidence   INT         NOT NULL COMMENT '可信度 0-100',
    created_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_chat_history_user_created_time (user_id, created_time),
    KEY idx_chat_history_session_created (anonymous_session_id, created_time),
    KEY idx_chat_history_conversation (conversation_id),
    CONSTRAINT fk_chat_history_user
        FOREIGN KEY (user_id) REFERENCES `user` (id)
        ON DELETE SET NULL,
    CONSTRAINT chk_chat_history_confidence
        CHECK (confidence BETWEEN 0 AND 100)
) ENGINE = InnoDB COMMENT = '聊天历史';

CREATE TABLE IF NOT EXISTS conversation (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT       NULL COMMENT '用户 ID，匿名会话为空',
    anonymous_session_id VARCHAR(36) NOT NULL COMMENT '匿名会话标识',
    title        VARCHAR(255) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    created_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    expire_time  DATETIME(6)  NOT NULL COMMENT '匿名会话过期时间',
    PRIMARY KEY (id),
    KEY idx_conversation_user_updated (user_id, updated_time),
    KEY idx_conversation_session_updated (anonymous_session_id, updated_time),
    KEY idx_conversation_expire_time (expire_time),
    CONSTRAINT fk_conversation_user
        FOREIGN KEY (user_id) REFERENCES `user` (id)
        ON DELETE SET NULL
) ENGINE = InnoDB COMMENT = '多轮对话会话';

CREATE TABLE IF NOT EXISTS message (
    id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    conversation_id  BIGINT      NOT NULL COMMENT '会话 ID',
    role             VARCHAR(16) NOT NULL COMMENT 'USER/ASSISTANT',
    content          LONGTEXT    NOT NULL COMMENT '消息正文',
    sources          LONGTEXT    NULL COMMENT '回答来源 JSON',
    chat_history_id  BIGINT      NULL COMMENT '对应回答记录',
    created_time     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_message_conversation_created (conversation_id, created_time),
    KEY idx_message_chat_history (chat_history_id),
    CONSTRAINT fk_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_message_chat_history
        FOREIGN KEY (chat_history_id) REFERENCES chat_history (id)
        ON DELETE SET NULL,
    CONSTRAINT chk_message_role CHECK (role IN ('USER', 'ASSISTANT'))
) ENGINE = InnoDB COMMENT = '多轮对话消息';

CREATE TABLE IF NOT EXISTS answer_feedback (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    chat_history_id  BIGINT        NOT NULL COMMENT '回答记录 ID',
    user_feedback    VARCHAR(16)   NOT NULL COMMENT 'HELPFUL/INCORRECT',
    reason           VARCHAR(1000) NULL COMMENT '用户补充原因',
    created_time     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_feedback_chat_history (chat_history_id),
    KEY idx_feedback_type_created (user_feedback, created_time),
    CONSTRAINT fk_feedback_chat_history
        FOREIGN KEY (chat_history_id) REFERENCES chat_history (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_feedback_type
        CHECK (user_feedback IN ('HELPFUL', 'INCORRECT'))
) ENGINE = InnoDB COMMENT = '回答反馈';

CREATE TABLE IF NOT EXISTS evaluation_result (
      id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
      run_id        VARCHAR(36)   NOT NULL COMMENT '评测批次 UUID',
      category      VARCHAR(32)   NOT NULL COMMENT '问题分类',
      question      VARCHAR(2000) NOT NULL COMMENT '评测问题',
      answer        LONGTEXT      NOT NULL COMMENT '系统回答',
      expected_source VARCHAR(500) NOT NULL COMMENT '期望来源关键词',
      has_source    BOOLEAN       NOT NULL COMMENT '是否引用来源',
      source_match  BOOLEAN       NOT NULL COMMENT '来源是否匹配',
      keyword_match BOOLEAN       NOT NULL COMMENT '答案关键词是否命中',
      non_empty     BOOLEAN       NOT NULL COMMENT '是否为有效非空回答',
      score         INT           NOT NULL COMMENT '规则评分 0-100',
      human_accurate BOOLEAN      NULL COMMENT '人工准确性复核',
      review_note   VARCHAR(1000) NULL COMMENT '人工复核备注',
      created_time  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
      PRIMARY KEY (id),
      KEY idx_evaluation_run (run_id, id),
    KEY idx_evaluation_created (created_time),
    CONSTRAINT chk_evaluation_score CHECK (score BETWEEN 0 AND 100)
) ENGINE = InnoDB COMMENT = 'RAG 自动评测结果';

CREATE TABLE IF NOT EXISTS operation_log (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    admin_id     BIGINT        NOT NULL COMMENT '管理员 ID',
    operation    VARCHAR(64)   NOT NULL COMMENT '操作类型',
    target       VARCHAR(1000) NOT NULL COMMENT '操作目标',
    created_time DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_operation_log_admin_created (admin_id, created_time),
    KEY idx_operation_log_created (created_time)
) ENGINE = InnoDB COMMENT = '管理员操作审计';

CREATE TABLE IF NOT EXISTS ai_provider_config (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    provider          VARCHAR(32)   NOT NULL,
    encrypted_api_key VARCHAR(2048) NULL,
    key_last_four     VARCHAR(8)    NULL,
    model             VARCHAR(64)   NOT NULL,
    enabled           BOOLEAN       NOT NULL DEFAULT FALSE,
    verified_time     DATETIME(6)   NULL,
    created_time      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_time      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_provider_config_provider (provider)
) ENGINE = InnoDB COMMENT = 'AI 服务加密运行配置';

CREATE TABLE IF NOT EXISTS ai_usage_record (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    provider            VARCHAR(32)  NOT NULL,
    model               VARCHAR(64)  NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    prompt_tokens       INT          NOT NULL DEFAULT 0,
    completion_tokens   INT          NOT NULL DEFAULT 0,
    total_tokens        INT          NOT NULL DEFAULT 0,
    cache_hit_tokens    INT          NOT NULL DEFAULT 0,
    cache_miss_tokens   INT          NOT NULL DEFAULT 0,
    latency_ms          BIGINT       NOT NULL DEFAULT 0,
    error_type          VARCHAR(64)  NULL,
    created_time        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ai_usage_created_time (created_time),
    KEY idx_ai_usage_provider_status (provider, status, created_time),
    CONSTRAINT chk_ai_usage_status CHECK (status IN ('SUCCESS', 'FAILED'))
) ENGINE = InnoDB COMMENT = 'AI 调用用量计数';
