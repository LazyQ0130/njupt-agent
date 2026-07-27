package com.njupt.aiassistant.vo;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.OffsetDateTime;

public record CrawlerStatusVO(
        boolean running,
        @JsonAlias("total_webpages") int totalWebpages,
        @JsonAlias("last_updated") OffsetDateTime lastUpdated,
        @JsonAlias("latest_run") CrawlerRunVO latestRun
) {
}
