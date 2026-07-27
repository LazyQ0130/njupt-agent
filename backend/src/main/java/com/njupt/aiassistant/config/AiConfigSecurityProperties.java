package com.njupt.aiassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.config-security")
public record AiConfigSecurityProperties(String masterKey) {
    public AiConfigSecurityProperties {
        masterKey = masterKey == null ? "" : masterKey.trim();
    }

    @Override
    public String toString() {
        return "AiConfigSecurityProperties[masterKey=***]";
    }
}
