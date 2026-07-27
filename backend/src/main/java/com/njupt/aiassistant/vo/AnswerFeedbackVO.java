package com.njupt.aiassistant.vo;

import com.njupt.aiassistant.entity.FeedbackType;

import java.time.LocalDateTime;

public record AnswerFeedbackVO(
        Long id,
        Long chatId,
        FeedbackType feedback,
        String reason,
        LocalDateTime createdTime
) {
}
