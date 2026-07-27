package com.njupt.aiassistant.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ChatHistoryVO(
        Long id,
        Long userId,
        String question,
        String answer,
        List<ChatSourceVO> sources,
        int confidence,
        LocalDateTime createdTime
) {
}
