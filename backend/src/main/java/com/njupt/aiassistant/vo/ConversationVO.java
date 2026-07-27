package com.njupt.aiassistant.vo;

import java.time.LocalDateTime;

public record ConversationVO(
        Long id,
        Long userId,
        String title,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
