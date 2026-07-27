USE njupt_ai_assistant;

ALTER TABLE document
    MODIFY storage_path VARCHAR(512) NULL,
    ADD COLUMN source_type VARCHAR(32) NOT NULL
        DEFAULT 'UPLOADED_FILE' AFTER content_type,
    ADD COLUMN source_url VARCHAR(768) NULL AFTER source_type,
    ADD COLUMN category VARCHAR(32) NULL AFTER source_url,
    ADD COLUMN crawl_time DATETIME(6) NULL AFTER category,
    ADD COLUMN last_updated DATETIME(6) NULL AFTER crawl_time,
    ADD COLUMN content_hash CHAR(64) NULL AFTER last_updated,
    ADD UNIQUE KEY uk_document_source_url (source_url),
    ADD KEY idx_document_source_type_category (source_type, category),
    ADD KEY idx_document_content_hash (content_hash);
