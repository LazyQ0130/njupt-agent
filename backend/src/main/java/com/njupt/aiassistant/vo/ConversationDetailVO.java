package com.njupt.aiassistant.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationDetailVO(
        Long id,
        Long userId,
        String title,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        List<ConversationMessageVO> messages
) {
}
