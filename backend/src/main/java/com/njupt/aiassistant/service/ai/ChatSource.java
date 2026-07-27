package com.njupt.aiassistant.service.ai;

public record ChatSource(
        String title,
        String type,
        Integer page,
        Double score,
        String source,
        String url,
        String category
) {
}
