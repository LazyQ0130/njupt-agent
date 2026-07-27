package com.njupt.aiassistant.service.ai;

import java.util.List;

public record ChatAnswer(
        String answer,
        List<ChatSource> sources,
        int confidence
) {
}
