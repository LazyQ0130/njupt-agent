package com.njupt.aiassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.admin")
public record AdminSecurityProperties(
        Long id,
        String username,
        String passwordHash
) {
    public AdminSecurityProperties {
        id = id == null ? 1L : id;
        username = username == null ? "" : username.trim();
        passwordHash = passwordHash == null ? "" : passwordHash.trim();
    }
}
