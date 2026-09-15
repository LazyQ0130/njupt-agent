package com.njupt.aiassistant.service.impl;

import com.njupt.aiassistant.entity.DocumentEntity;
import com.njupt.aiassistant.entity.DocumentSourceType;
import com.njupt.aiassistant.entity.DocumentStatus;
import com.njupt.aiassistant.mapper.DocumentMapper;
import com.njupt.aiassistant.mapper.DocumentExclusionMapper;
import com.njupt.aiassistant.client.RagClient;
import com.njupt.aiassistant.config.CrawlerProperties;
import com.njupt.aiassistant.dto.CuratedDocumentRequest;
import com.njupt.aiassistant.entity.DocumentCategory;
import com.njupt.aiassistant.entity.DocumentExclusionEntity;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.service.indexing.DocumentUploadedEvent;
import com.njupt.aiassistant.service.indexing.WebDocumentIndexEvent;
import com.njupt.aiassistant.service.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentServiceImplTests {

    @TempDir
    Path tempDirectory;

    @Test
    void reindexSchedulesOnlyUploadedDocumentsWithAvailableSourceFiles()
            throws Exception {
        var mapper = mock(DocumentMapper.class);
        var storage = mock(FileStorageService.class);
        var publisher = mock(ApplicationEventPublisher.class);
        var exclusions = mock(DocumentExclusionMapper.class);
        var ragClient = mock(RagClient.class);
        var crawlerProperties = new CrawlerProperties(
                null,
                null,
                null,
                "test-token",
                "www.njupt.edu.cn"
        );
        var sourceFile = Files.writeString(
                tempDirectory.resolve("automation.pdf"),
                "test"
        );
        var ready = document(
                1L,
                DocumentSourceType.UPLOADED_FILE,
                DocumentStatus.COMPLETED,
                sourceFile
        );
        var processing = document(
                2L,
                DocumentSourceType.UPLOADED_FILE,
                DocumentStatus.PROCESSING,
                sourceFile
        );
        var uploading = document(
                3L,
                DocumentSourceType.UPLOADED_FILE,
                DocumentStatus.UPLOADING,
                sourceFile
        );
        var failed = document(
                4L,
                DocumentSourceType.UPLOADED_FILE,
                DocumentStatus.FAILED,
                sourceFile
        );
        var missing = document(
                5L,
                DocumentSourceType.UPLOADED_FILE,
                DocumentStatus.COMPLETED,
                tempDirectory.resolve("missing.pdf")
        );
        var website = document(
                6L,
                DocumentSourceType.OFFICIAL_WEBSITE,
                DocumentStatus.COMPLETED,
                null
        );
        when(mapper.findAllByOrderByCreatedTimeDesc())
                .thenReturn(List.of(
                        ready,
                        processing,
                        uploading,
                        failed,
                        missing,
                        website
                ));
        when(mapper.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = new DocumentServiceImpl(
                mapper,
                storage,
                publisher,
                exclusions,
                ragClient,
                crawlerProperties
        ).reindexUploadedDocuments();

        assertThat(result.scheduledCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(4);
        assertThat(ready.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(processing.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(uploading.getStatus()).isEqualTo(DocumentStatus.UPLOADING);
        assertThat(failed.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(missing.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        verify(publisher).publishEvent(any(DocumentUploadedEvent.class));
    }

    @Test
    void upsertsCuratedKnowledgeWithOfficialSourceMetadata() {
        var mapper = mock(DocumentMapper.class);
        var storage = mock(FileStorageService.class);
        var publisher = mock(ApplicationEventPublisher.class);
        var exclusions = mock(DocumentExclusionMapper.class);
        var ragClient = mock(RagClient.class);
        when(exclusions.findBySourceUrl(any())).thenReturn(Optional.empty());
        when(mapper.findBySourceUrl(any())).thenReturn(Optional.empty());
        when(mapper.save(any())).thenAnswer(invocation -> {
            var value = invocation.<DocumentEntity>getArgument(0);
            value.setId(99L);
            return value;
        });

        var saved = service(
                mapper,
                storage,
                publisher,
                exclusions,
                ragClient
        ).upsertCurated(new CuratedDocumentRequest(
                "本科生缓考申请条件与流程",
                "结论：学生因疾病无法参加考试时，应在考试前申请缓考。"
                        + "条件：提供能够证明无法参加考试的材料。"
                        + "流程：向所在学院提交申请，完成学院审核并等待学校审批。"
                        + "未获批准而缺考的，按照学校考试管理规定处理。",
                "南京邮电大学本科生院",
                "https://jwc.njupt.edu.cn/rules/exam/page.htm",
                DocumentCategory.ACADEMIC,
                OffsetDateTime.parse("2024-04-30T00:00:00+08:00"),
                OffsetDateTime.parse("2026-07-27T12:00:00+08:00")
        ));

        assertThat(saved.id()).isEqualTo(99L);
        assertThat(saved.sourceType())
                .isEqualTo(DocumentSourceType.CURATED_OFFICIAL);
        assertThat(saved.sourceUrl())
                .isEqualTo("https://jwc.njupt.edu.cn/rules/exam/page.htm");
        verify(publisher).publishEvent(new WebDocumentIndexEvent(99L));
    }

    @Test
    void deletesVectorThenDocumentAndSuppressesWebsiteReingest() {
        var mapper = mock(DocumentMapper.class);
        var storage = mock(FileStorageService.class);
        var publisher = mock(ApplicationEventPublisher.class);
        var exclusions = mock(DocumentExclusionMapper.class);
        var ragClient = mock(RagClient.class);
        var website = document(
                8L,
                DocumentSourceType.OFFICIAL_WEBSITE,
                DocumentStatus.COMPLETED,
                null
        );
        website.setTitle("低价值活动新闻");
        website.setSourceUrl(
                "https://cs.njupt.edu.cn/2026/0701/c1a1/page.htm"
        );
        website.setContentHash("d".repeat(64));
        website.setVectorDocumentId("e".repeat(64));
        when(mapper.findById(8L)).thenReturn(Optional.of(website));
        when(exclusions.findBySourceUrl(website.getSourceUrl()))
                .thenReturn(Optional.empty());
        when(exclusions.save(any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        service(
                mapper,
                storage,
                publisher,
                exclusions,
                ragClient
        ).delete(8L, true);

        verify(ragClient).delete("e".repeat(64));
        verify(exclusions).save(any(DocumentExclusionEntity.class));
        verify(mapper).delete(website);
    }

    @Test
    void keepsDatabaseRecordWhenVectorDeletionFails() {
        var mapper = mock(DocumentMapper.class);
        var storage = mock(FileStorageService.class);
        var publisher = mock(ApplicationEventPublisher.class);
        var exclusions = mock(DocumentExclusionMapper.class);
        var ragClient = mock(RagClient.class);
        var website = document(
                9L,
                DocumentSourceType.OFFICIAL_WEBSITE,
                DocumentStatus.COMPLETED,
                null
        );
        website.setVectorDocumentId("f".repeat(64));
        when(mapper.findById(9L)).thenReturn(Optional.of(website));
        doThrow(new BusinessException(
                com.njupt.aiassistant.common.ErrorCode.RAG_SERVICE_FAILED
        )).when(ragClient).delete("f".repeat(64));

        assertThatThrownBy(() -> service(
                mapper,
                storage,
                publisher,
                exclusions,
                ragClient
        ).delete(9L, true)).isInstanceOf(BusinessException.class);

        verify(mapper, never()).delete(any());
    }

    private DocumentServiceImpl service(
            DocumentMapper mapper,
            FileStorageService storage,
            ApplicationEventPublisher publisher,
            DocumentExclusionMapper exclusions,
            RagClient ragClient
    ) {
        return new DocumentServiceImpl(
                mapper,
                storage,
                publisher,
                exclusions,
                ragClient,
                new CrawlerProperties(
                        null,
                        null,
                        null,
                        "test-token",
                        "www.njupt.edu.cn"
                )
        );
    }

    private DocumentEntity document(
            Long id,
            DocumentSourceType sourceType,
            DocumentStatus status,
            Path storagePath
    ) {
        var document = new DocumentEntity();
        document.setId(id);
        document.setSourceType(sourceType);
        document.setStatus(status);
        document.setStoragePath(storagePath == null ? null : storagePath.toString());
        document.setFilename("automation.pdf");
        document.setType("PDF");
        document.setSource("自动化学院");
        document.setCreatedTime(LocalDateTime.of(2026, 7, 26, 12, 0));
        return document;
    }
}
