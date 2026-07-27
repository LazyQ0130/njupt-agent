package com.njupt.aiassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CustomCrawlerRequest(
        @JsonProperty("seed_url")
        @NotBlank
        @Size(max = 2_048)
        String seedUrl,
        @JsonProperty("date_scope")
        CrawlerDateScope dateScope,
        @JsonProperty("crawl_scope")
        CrawlerScope crawlScope,
        @Min(1) @Max(5) Integer years,
        @JsonProperty("max_pages")
        @NotNull
        @Min(1)
        @Max(50)
        Integer maxPages,
        @JsonProperty("force_reindex")
        boolean forceReindex
) {
}
