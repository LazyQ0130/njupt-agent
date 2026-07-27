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
) ENGINE = InnoDB COMMENT = 'AI provider encrypted runtime configuration';

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
) ENGINE = InnoDB COMMENT = 'Non-sensitive AI request usage counters';
