package com.njupt.aiassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.njupt.aiassistant.entity.DocumentCategory;
import com.njupt.aiassistant.entity.DocumentSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CrawlerDocumentRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 2_000_000) String content,
        @NotBlank @Size(max = 128) String source,
        @JsonProperty("source_type") @NotNull DocumentSourceType sourceType,
        @JsonProperty("source_url") @NotBlank @Size(max = 768) String sourceUrl,
        @NotNull DocumentCategory category,
        @JsonProperty("published_time") OffsetDateTime publishedTime,
        @JsonProperty("crawl_time") @NotNull OffsetDateTime crawlTime,
        @JsonProperty("last_updated") @NotNull OffsetDateTime lastUpdated,
        @JsonProperty("content_hash")
        @NotBlank
        @Pattern(regexp = "^[a-f0-9]{64}$")
        String contentHash,
        @JsonProperty("force_reindex") boolean forceReindex
) {
}
