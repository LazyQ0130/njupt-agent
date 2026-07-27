package com.njupt.aiassistant.vo;

import com.njupt.aiassistant.entity.DocumentStatus;

public record CrawlerDocumentIngestVO(
        Long id,
        String action,
        DocumentStatus status
) {
}
