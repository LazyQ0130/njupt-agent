package com.njupt.aiassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.answer")
public record RagAnswerProperties(
        Integer topK,
        Integer candidateK,
        Double minimumRelevanceScore
) {
    public RagAnswerProperties {
        topK = topK == null ? 5 : Math.max(1, Math.min(20, topK));
        candidateK = candidateK == null
                ? 20
                : Math.max(topK, Math.min(20, candidateK));
        minimumRelevanceScore = minimumRelevanceScore == null
                ? 0.35
                : Math.max(0.0, Math.min(1.0, minimumRelevanceScore));
    }
}
