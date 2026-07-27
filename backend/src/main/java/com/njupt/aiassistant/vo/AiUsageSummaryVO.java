package com.njupt.aiassistant.vo;

public record AiUsageSummaryVO(
        String period,
        long requestCount,
        long successCount,
        long failureCount,
        double successRate,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        long cacheHitTokens,
        long cacheMissTokens
) {
}
