USE njupt_ai_assistant;

ALTER TABLE document
    DROP CHECK chk_document_category;

ALTER TABLE document
    ADD CONSTRAINT chk_document_category
        CHECK (
            category IS NULL OR category IN (
                'NEW_STUDENT', 'ACADEMIC', 'LIFE', 'MAJOR', 'CAREER',
                'SCHOOL_OVERVIEW', 'ORGANIZATION', 'RESEARCH'
            )
        );

ALTER TABLE document
    MODIFY category VARCHAR(32) NULL
        COMMENT '知识分类（八类枚举）';
