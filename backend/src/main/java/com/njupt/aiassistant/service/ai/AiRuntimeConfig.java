package com.njupt.aiassistant.service.ai;

public record AiRuntimeConfig(
        String apiKey,
        String model,
        boolean enabled,
        String source
) {
}
