package com.njupt.aiassistant.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record EvaluationQuestion(
        String question,
        String category,
        @JsonProperty("expected_source") String expectedSource,
        @JsonProperty("expected_keywords") List<String> expectedKeywords
) {
}
