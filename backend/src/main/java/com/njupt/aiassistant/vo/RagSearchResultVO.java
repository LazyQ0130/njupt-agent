package com.njupt.aiassistant.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RagSearchResultVO(
        String content,
        String filename,
        Integer page,
        String source,
        @JsonProperty("source_url") String sourceUrl,
        @JsonProperty("source_type") String sourceType,
        String category,
        double score
) {
    public RagSearchResultVO(
            String content,
            String filename,
            Integer page,
            String source,
            String sourceUrl,
            String category,
            double score
    ) {
        this(content, filename, page, source, sourceUrl, null, category, score);
    }

    public RagSearchResultVO(
            String content,
            String filename,
            Integer page,
            String source,
            double score
    ) {
        this(content, filename, page, source, null, null, null, score);
    }
}
