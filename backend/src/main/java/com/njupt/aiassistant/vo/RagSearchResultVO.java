package com.njupt.aiassistant.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RagSearchResultVO(
        String content,
        String filename,
        Integer page,
        String source,
        @JsonProperty("source_url") String sourceUrl,
        String category,
        double score
) {
    public RagSearchResultVO(
            String content,
            String filename,
            Integer page,
            String source,
            double score
    ) {
        this(content, filename, page, source, null, null, score);
    }
}
