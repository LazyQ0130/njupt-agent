package com.njupt.aiassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "rag.service")
public record RagProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration indexReadTimeout,
        Boolean indexingEnabled
) {
    public RagProperties {
        baseUrl = baseUrl == null ? URI.create("http://localhost:8090") : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
        indexReadTimeout = indexReadTimeout == null
                ? Duration.ofSeconds(60)
                : indexReadTimeout;
        indexingEnabled = indexingEnabled == null || indexingEnabled;
    }
}
