package com.njupt.aiassistant.vo;

public record LowQualityQuestionVO(
        String question,
        long occurrences,
        long incorrectCount,
        double averageConfidence
) {
}
