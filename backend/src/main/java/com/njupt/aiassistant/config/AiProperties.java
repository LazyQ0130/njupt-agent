package com.njupt.aiassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        String provider,
        String apiKey,
        String baseUrl,
        String model
) {
}
