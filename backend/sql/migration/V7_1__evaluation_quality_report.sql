USE njupt_ai_assistant;

ALTER TABLE evaluation_result
    ADD COLUMN run_id VARCHAR(36) NULL AFTER id,
    ADD COLUMN category VARCHAR(32) NULL AFTER run_id,
    ADD COLUMN expected_source VARCHAR(500) NULL AFTER answer,
    ADD COLUMN has_source BOOLEAN NULL AFTER expected_source,
    ADD COLUMN keyword_match BOOLEAN NULL AFTER source_match,
    ADD COLUMN non_empty BOOLEAN NULL AFTER keyword_match,
    ADD COLUMN human_accurate BOOLEAN NULL AFTER score,
    ADD COLUMN review_note VARCHAR(1000) NULL AFTER human_accurate;

UPDATE evaluation_result
SET run_id = UUID(),
    category = 'UNKNOWN',
    expected_source = '',
    has_source = source_match,
    keyword_match = FALSE,
    non_empty = answer <> ''
WHERE run_id IS NULL;

ALTER TABLE evaluation_result
    MODIFY run_id VARCHAR(36) NOT NULL,
    MODIFY category VARCHAR(32) NOT NULL,
    MODIFY expected_source VARCHAR(500) NOT NULL,
    MODIFY has_source BOOLEAN NOT NULL,
    MODIFY keyword_match BOOLEAN NOT NULL,
    MODIFY non_empty BOOLEAN NOT NULL,
    ADD KEY idx_evaluation_run (run_id, id);
