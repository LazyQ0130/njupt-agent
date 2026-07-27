package com.njupt.aiassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "crawler")
public record CrawlerProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String sharedToken,
        String allowedDomains
) {
    public CrawlerProperties {
        baseUrl = baseUrl == null ? URI.create("http://localhost:8091") : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
        sharedToken = sharedToken == null ? "" : sharedToken;
        allowedDomains = allowedDomains == null || allowedDomains.isBlank()
                ? "www.njupt.edu.cn,jwc.njupt.edu.cn,cs.njupt.edu.cn"
                : allowedDomains;
    }

    public Set<String> allowedDomainSet() {
        return Arrays.stream(allowedDomains.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean authorizes(String providedToken) {
        if (sharedToken.isBlank() || providedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sharedToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public String toString() {
        return "CrawlerProperties[baseUrl=" + baseUrl
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout
                + ", sharedToken=***, allowedDomains=" + allowedDomains + "]";
    }
}
