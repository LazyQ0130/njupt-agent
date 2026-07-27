package com.njupt.aiassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "deepseek")
public record DeepSeekProperties(
        String apiKey,
        URI baseUrl,
        String model,
        Duration connectTimeout,
        Duration readTimeout,
        Integer maxTokens
) {
    public DeepSeekProperties {
        apiKey = apiKey == null ? "" : apiKey;
        baseUrl = baseUrl == null ? URI.create("https://api.deepseek.com") : baseUrl;
        model = model == null || model.isBlank() ? "deepseek-v4-flash" : model;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(45) : readTimeout;
        maxTokens = maxTokens == null ? 1200 : maxTokens;
    }

    @Override
    public String toString() {
        return "DeepSeekProperties[apiKey=***, baseUrl=" + baseUrl
                + ", model=" + model
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout
                + ", maxTokens=" + maxTokens + "]";
    }
}
