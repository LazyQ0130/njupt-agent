package com.njupt.aiassistant.service.indexing;

import java.nio.file.Path;
import java.time.LocalDateTime;

public record DocumentUploadedEvent(
        Long documentId,
        Path path,
        String filename,
        String type,
        String source,
        LocalDateTime uploadTime
) {
}
