package com.njupt.aiassistant.vo;

import com.njupt.aiassistant.entity.DocumentStatus;
import com.njupt.aiassistant.entity.DocumentCategory;
import com.njupt.aiassistant.entity.DocumentSourceType;

import java.time.LocalDateTime;

public record DocumentVO(
        Long id,
        String title,
        String filename,
        String source,
        String type,
        DocumentStatus status,
        long fileSize,
        String contentType,
        DocumentSourceType sourceType,
        String sourceUrl,
        DocumentCategory category,
        LocalDateTime crawlTime,
        LocalDateTime lastUpdated,
        String contentHash,
        LocalDateTime createdTime
) {
    public DocumentVO(
            Long id,
            String title,
            String filename,
            String source,
            String type,
            DocumentStatus status,
            long fileSize,
            String contentType,
            LocalDateTime createdTime
    ) {
        this(
                id, title, filename, source, type, status, fileSize, contentType,
                DocumentSourceType.UPLOADED_FILE, null, null, null, null, null,
                createdTime
        );
    }
}
