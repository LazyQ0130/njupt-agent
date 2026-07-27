package com.njupt.aiassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record WebDocumentIndexRequest(
        String title,
        String content,
        String source,
        @JsonProperty("source_url") String sourceUrl,
        String category,
        @JsonProperty("crawl_time") LocalDateTime crawlTime,
        @JsonProperty("last_updated") LocalDateTime lastUpdated,
        @JsonProperty("content_hash") String contentHash,
        @JsonProperty("source_type") String sourceType
) {
    public WebDocumentIndexRequest(
            String title,
            String content,
            String source,
            String sourceUrl,
            String category,
            LocalDateTime crawlTime,
            LocalDateTime lastUpdated,
            String contentHash
    ) {
        this(
                title,
                content,
                source,
                sourceUrl,
                category,
                crawlTime,
                lastUpdated,
                contentHash,
                "OFFICIAL_WEBSITE"
        );
    }
}
