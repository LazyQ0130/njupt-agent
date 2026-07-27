package com.njupt.aiassistant.vo;

import com.fasterxml.jackson.annotation.JsonAlias;

public record CrawlerTriggerVO(
        boolean accepted,
        @JsonAlias("run_id") Long runId,
        String message
) {
}
