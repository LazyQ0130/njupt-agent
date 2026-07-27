package com.njupt.aiassistant.vo;

import java.util.List;

public record QualityStatsVO(
        long totalAnswers,
        long feedbackCount,
        long helpfulCount,
        long incorrectCount,
        double helpfulRate,
        List<FrequentQuestionVO> highFrequencyQuestions
) {
}
