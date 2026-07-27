package com.njupt.aiassistant.vo;

public record EvaluationItemVO(
        Long id,
        String question,
        String category,
        String answer,
        boolean hasSource,
        boolean sourceMatch,
        boolean keywordMatch,
        boolean nonEmpty,
        int score,
        Boolean humanAccurate,
        String reviewNote
) {
}
