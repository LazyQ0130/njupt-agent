package com.njupt.aiassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Storage storage,
        Cors cors
) {
    public record Storage(Path rootPath, DataSize maxFileSize) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
