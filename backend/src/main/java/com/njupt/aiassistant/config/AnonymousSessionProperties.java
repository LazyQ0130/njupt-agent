package com.njupt.aiassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "anonymous-session")
public record AnonymousSessionProperties(
        Duration retention,
        String cleanupCron
) {
    public AnonymousSessionProperties {
        retention = retention == null ? Duration.ofDays(90) : retention;
        cleanupCron = cleanupCron == null || cleanupCron.isBlank()
                ? "0 30 3 * * *"
                : cleanupCron;
    }
}
