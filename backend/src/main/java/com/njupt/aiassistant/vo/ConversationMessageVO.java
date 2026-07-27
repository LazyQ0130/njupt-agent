package com.njupt.aiassistant.vo;

import com.njupt.aiassistant.entity.MessageRole;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationMessageVO(
        Long id,
        MessageRole role,
        String content,
        List<ChatSourceVO> sources,
        Long chatId,
        Integer confidence,
        LocalDateTime createdTime
) {
}
