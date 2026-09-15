package com.njupt.aiassistant.vo;

import java.util.List;

public record QualityStatsVO(
        long totalAnswers,
        long uniqueAnonymousUsers,
        long feedbackCount,
        long helpfulCount,
        long incorrectCount,
        double helpfulRate,
        long lowConfidenceAnswerCount,
        long lowConfidenceIncorrectCount,
        List<FrequentQuestionVO> highFrequencyQuestions,
        List<LowQualityQuestionVO> lowQualityQuestions
) {
}
