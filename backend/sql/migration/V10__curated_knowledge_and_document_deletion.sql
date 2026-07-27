USE njupt_ai_assistant;

ALTER TABLE document
    ADD COLUMN vector_document_id CHAR(64) NULL AFTER content_hash,
    DROP CHECK chk_document_source_type,
    ADD CONSTRAINT chk_document_source_type
        CHECK (
            source_type IN (
                'OFFICIAL_WEBSITE',
                'CURATED_OFFICIAL',
                'UPLOADED_FILE'
            )
        ),
    ADD KEY idx_document_vector_document_id (vector_document_id);

CREATE TABLE IF NOT EXISTS document_exclusion (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    source_url   VARCHAR(768) NOT NULL,
    reason       VARCHAR(255) NOT NULL,
    created_time DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_exclusion_source_url (source_url)
) ENGINE = InnoDB COMMENT = '管理员禁止重新入库的官网来源';
