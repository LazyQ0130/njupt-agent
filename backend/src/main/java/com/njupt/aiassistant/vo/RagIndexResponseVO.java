package com.njupt.aiassistant.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RagIndexResponseVO(
        @JsonProperty("document_id") String documentId,
        String filename,
        @JsonProperty("chunks_indexed") int chunksIndexed,
        String category
) {
}
