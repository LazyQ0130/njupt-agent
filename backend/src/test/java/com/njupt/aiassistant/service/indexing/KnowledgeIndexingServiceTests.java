package com.njupt.aiassistant.service.indexing;

import com.njupt.aiassistant.client.RagClient;
import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.entity.DocumentEntity;
import com.njupt.aiassistant.entity.DocumentStatus;
import com.njupt.aiassistant.entity.DocumentCategory;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.mapper.DocumentMapper;
import com.njupt.aiassistant.vo.RagIndexResponseVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeIndexingServiceTests {

    @Test
    void marksDocumentCompletedAfterSuccessfulIndexing() {
        var ragClient = mock(RagClient.class);
        var mapper = mock(DocumentMapper.class);
        var document = document(1L);
        var event = event("PDF");
        when(mapper.findById(1L)).thenReturn(Optional.of(document));
        when(ragClient.index(any(), any(), any(), any()))
                .thenReturn(new RagIndexResponseVO(
                        "vector-doc-1",
                        "教务管理规定.pdf",
                        3,
                        "ACADEMIC"
                ));

        new KnowledgeIndexingService(ragClient, mapper).index(event);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(document.getCategory()).isEqualTo(DocumentCategory.ACADEMIC);
        verify(mapper).save(document);
    }

    @Test
    void marksDocumentFailedWithoutLeakingIndexingFailure() {
        var ragClient = mock(RagClient.class);
        var mapper = mock(DocumentMapper.class);
        var document = document(1L);
        when(mapper.findById(1L)).thenReturn(Optional.of(document));
        when(ragClient.index(any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.RAG_SERVICE_UNAVAILABLE));

        new KnowledgeIndexingService(ragClient, mapper).index(event("DOCX"));

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        verify(mapper).save(document);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    void clearsExistingCategoryWhenIndexingDoesNotClassifyDocument(
            String indexedCategory
    ) {
        var ragClient = mock(RagClient.class);
        var mapper = mock(DocumentMapper.class);
        var document = document(1L);
        document.setCategory(DocumentCategory.ACADEMIC);
        when(mapper.findById(1L)).thenReturn(Optional.of(document));
        when(ragClient.index(any(), any(), any(), any()))
                .thenReturn(new RagIndexResponseVO(
                        "vector-doc-1",
                        "unclassified.pdf",
                        3,
                        indexedCategory
                ));

        new KnowledgeIndexingService(ragClient, mapper).index(event("PDF"));

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(document.getCategory()).isNull();
        verify(mapper).save(document);
    }

    private DocumentEntity document(Long id) {
        var document = new DocumentEntity();
        document.setId(id);
        document.setStatus(DocumentStatus.PROCESSING);
        return document;
    }

    private DocumentUploadedEvent event(String type) {
        return new DocumentUploadedEvent(
                1L,
                Path.of("data", "stored.pdf"),
                "教务管理规定.pdf",
                type,
                "教务处",
                LocalDateTime.of(2026, 7, 26, 10, 0)
        );
    }
}
