package com.njupt.aiassistant.service.storage;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.config.AppProperties;
import com.njupt.aiassistant.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void storesPdfWithRandomizedServerFilename() {
        var service = serviceWithMaxSize(DataSize.ofMegabytes(1));
        var file = new MockMultipartFile(
                "file",
                "本科生转专业管理办法.pdf",
                "application/pdf",
                "%PDF-1.7 mock".getBytes()
        );

        var stored = service.store(file);

        assertThat(stored.originalFilename()).isEqualTo("本科生转专业管理办法.pdf");
        assertThat(stored.extension()).isEqualTo("PDF");
        assertThat(stored.storedFilename()).endsWith(".pdf");
        assertThat(stored.storedFilename()).isNotEqualTo(stored.originalFilename());
        assertThat(Files.exists(stored.absolutePath())).isTrue();
    }

    @Test
    void rejectsUnsupportedExtension() {
        var service = serviceWithMaxSize(DataSize.ofMegabytes(1));
        var file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED)
                );
    }

    @Test
    void rejectsSpoofedPdfContent() {
        var service = serviceWithMaxSize(DataSize.ofMegabytes(1));
        var file = new MockMultipartFile(
                "file",
                "伪装资料.pdf",
                "application/pdf",
                "<script>alert(1)</script>".getBytes()
        );

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED)
                );
    }

    @Test
    void rejectsOversizedFileBeforeWriting() {
        var service = serviceWithMaxSize(DataSize.ofBytes(2));
        var file = new MockMultipartFile(
                "file",
                "guide.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> service.store(file))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE)
                );
    }

    private FileStorageService serviceWithMaxSize(DataSize maxSize) {
        var properties = new AppProperties(
                new AppProperties.Storage(tempDir, maxSize),
                new AppProperties.Cors(List.of("http://localhost:4174"))
        );
        return new FileStorageService(properties);
    }
}
