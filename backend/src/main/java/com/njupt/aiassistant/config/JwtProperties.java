package com.njupt.aiassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.jwt")
@Validated
public record JwtProperties(
        String secret,
        Duration accessTokenTtl
) {
    public JwtProperties {
        secret = secret == null ? "" : secret.trim();
        accessTokenTtl = accessTokenTtl == null
                ? Duration.ofHours(2)
                : accessTokenTtl;
    }
}
