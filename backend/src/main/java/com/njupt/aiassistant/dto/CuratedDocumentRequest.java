package com.njupt.aiassistant.dto;

import com.njupt.aiassistant.entity.DocumentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record CuratedDocumentRequest(
        @NotBlank
        @Size(max = 255)
        String title,
        @NotBlank
        @Size(min = 120, max = 20000)
        String content,
        @NotBlank
        @Size(max = 128)
        String source,
        @NotBlank
        @Size(max = 768)
        String sourceUrl,
        @NotNull
        DocumentCategory category,
        OffsetDateTime publishedTime,
        @NotNull
        OffsetDateTime verifiedTime
) {
}
