package com.njupt.aiassistant.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record EvaluationReportVO(
        String runId,
        int total,
        double averageScore,
        double sourceCoverage,
        double sourceMatchRate,
        double keywordMatchRate,
        double nonEmptyRate,
        int humanReviewedCount,
        Double humanAccuracyRate,
        Map<String, Double> categoryScores,
        LocalDateTime executedAt,
        List<EvaluationItemVO> results
) {
}
