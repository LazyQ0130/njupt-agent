package com.njupt.aiassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        boolean enabled,
        Backend backend,
        URI redisUri,
        int chatIpPerMinute,
        int chatSessionPerMinute,
        int adminIpPerMinute,
        int adminLoginIpPerMinute
) {
    public enum Backend {
        REDIS,
        LOCAL
    }

    public RateLimitProperties {
        backend = backend == null ? Backend.REDIS : backend;
        chatIpPerMinute = positive(chatIpPerMinute, 60);
        chatSessionPerMinute = positive(chatSessionPerMinute, 30);
        adminIpPerMinute = positive(adminIpPerMinute, 20);
        adminLoginIpPerMinute = positive(adminLoginIpPerMinute, 5);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
