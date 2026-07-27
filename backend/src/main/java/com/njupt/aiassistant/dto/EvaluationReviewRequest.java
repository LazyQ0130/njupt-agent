package com.njupt.aiassistant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EvaluationReviewRequest(
        @NotNull Boolean accurate,
        @Size(max = 1000) String note
) {
}
