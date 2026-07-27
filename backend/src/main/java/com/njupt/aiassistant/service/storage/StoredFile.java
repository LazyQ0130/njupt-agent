package com.njupt.aiassistant.service.storage;

import java.nio.file.Path;

public record StoredFile(
        String originalFilename,
        String storedFilename,
        String extension,
        String contentType,
        long size,
        Path absolutePath
) {
}
