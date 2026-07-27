package com.njupt.aiassistant.vo;

public record ChatSourceVO(
        String title,
        String type,
        Integer page,
        Double score,
        String source,
        String url,
        String category
) {
    public ChatSourceVO(
            String title,
            String type,
            Integer page,
            Double score,
            String source
    ) {
        this(title, type, page, score, source, null, null);
    }
}
