package com.njupt.aiassistant.vo;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.OffsetDateTime;

public record CrawlerRunVO(
        Long id,
        String status,
        @JsonAlias("force_reindex") boolean forceReindex,
        @JsonAlias("started_at") OffsetDateTime startedAt,
        @JsonAlias("finished_at") OffsetDateTime finishedAt,
        @JsonAlias("total_pages") int totalPages,
        @JsonAlias("success_count") int successCount,
        @JsonAlias("failed_count") int failedCount,
        @JsonAlias("indexed_count") int indexedCount,
        @JsonAlias("unchanged_count") int unchangedCount,
        @JsonAlias("robots_denied_count") int robotsDeniedCount,
        @JsonAlias("last_error") String lastError
) {
}
