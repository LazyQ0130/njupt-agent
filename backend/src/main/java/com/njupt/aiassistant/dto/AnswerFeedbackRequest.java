package com.njupt.aiassistant.dto;

import com.njupt.aiassistant.entity.FeedbackType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AnswerFeedbackRequest(
        @NotNull @Positive Long chatId,
        @NotNull FeedbackType feedback,
        @Size(max = 1000) String reason
) {
}
