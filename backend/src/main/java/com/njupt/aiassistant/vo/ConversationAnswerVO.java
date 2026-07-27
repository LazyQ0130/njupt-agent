package com.njupt.aiassistant.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationAnswerVO(
        Long conversationId,
        Long messageId,
        Long chatId,
        String answer,
        List<ChatSourceVO> sources,
        int confidence,
        LocalDateTime createdTime
) {
}
