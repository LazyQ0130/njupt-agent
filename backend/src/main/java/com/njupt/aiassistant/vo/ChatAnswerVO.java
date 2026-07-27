package com.njupt.aiassistant.vo;

import java.util.List;

public record ChatAnswerVO(
        String answer,
        List<ChatSourceVO> sources,
        int confidence,
        Long chatId
) {
    public ChatAnswerVO(String answer, List<ChatSourceVO> sources, int confidence) {
        this(answer, sources, confidence, null);
    }
}
