package com.njupt.aiassistant.service.storage;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.config.AppProperties;
import com.njupt.aiassistant.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;

@Service
public class FileStorageService {

    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.of(
            "pdf", Set.of("application/pdf", "application/octet-stream"),
            "doc", Set.of("application/msword", "application/octet-stream"),
            "docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/octet-stream"
            )
    );

    private final AppProperties appProperties;

    public FileStorageService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public StoredFile store(MultipartFile file) {
        validate(file);
        var originalFilename = sanitizeFilename(file.getOriginalFilename());
        var extension = extensionOf(originalFilename);
        var storedFilename = UUID.randomUUID() + "." + extension;
        var root = appProperties.storage().rootPath().toAbsolutePath().normalize();
        var destination = root.resolve(storedFilename).normalize();

        if (!destination.startsWith(root)) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED, "文件保存路径不合法");
        }

        try {
            Files.createDirectories(root);
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(
                    originalFilename,
                    storedFilename,
                    extension.toUpperCase(Locale.ROOT),
                    normalizeContentType(file.getContentType()),
                    file.getSize(),
                    destination
            );
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.FILE_STORAGE_FAILED,
                    "文件保存失败: " + originalFilename,
                    exception
            );
        }
    }

    public void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Cleanup failure is intentionally non-fatal; production should emit a metric.
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > appProperties.storage().maxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        var filename = sanitizeFilename(file.getOriginalFilename());
        var extension = extensionOf(filename);
        var contentType = normalizeContentType(file.getContentType());
        var allowedTypes = ALLOWED_CONTENT_TYPES.get(extension);
        if (allowedTypes == null || !allowedTypes.contains(contentType)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        validateFileSignature(file, extension);
    }

    private void validateFileSignature(
            MultipartFile file,
            String extension
    ) {
        try {
            var valid = switch (extension) {
                case "pdf" -> startsWith(
                        file,
                        new int[]{0x25, 0x50, 0x44, 0x46, 0x2D}
                );
                case "doc" -> startsWith(
                        file,
                        new int[]{
                                0xD0, 0xCF, 0x11, 0xE0,
                                0xA1, 0xB1, 0x1A, 0xE1
                        }
                );
                case "docx" -> isWordOpenXml(file);
                default -> false;
            };
            if (!valid) {
                throw new BusinessException(
                        ErrorCode.FILE_TYPE_NOT_ALLOWED,
                        "文件内容与扩展名不匹配"
                );
            }
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "无法校验文件内容",
                    exception
            );
        }
    }

    private boolean startsWith(
            MultipartFile file,
            int[] signature
    ) throws IOException {
        try (var input = file.getInputStream()) {
            var header = input.readNBytes(signature.length);
            if (header.length != signature.length) {
                return false;
            }
            for (int index = 0; index < signature.length; index++) {
                if ((header[index] & 0xFF) != signature[index]) {
                    return false;
                }
            }
            return true;
        }
    }

    private boolean isWordOpenXml(MultipartFile file) throws IOException {
        var hasContentTypes = false;
        var hasDocument = false;
        var entries = 0;
        try (var archive = new ZipInputStream(file.getInputStream())) {
            for (var entry = archive.getNextEntry();
                    entry != null && entries < 256;
                    entry = archive.getNextEntry()) {
                entries++;
                var name = entry.getName().replace('\\', '/');
                hasContentTypes |= "[Content_Types].xml".equals(name);
                hasDocument |= "word/document.xml".equals(name);
                if (hasContentTypes && hasDocument) {
                    return true;
                }
            }
        }
        return false;
    }

    private String sanitizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new BusinessException(ErrorCode.FILE_EMPTY, "文件名不能为空");
        }
        return Path.of(filename).getFileName().toString();
    }

    private String extensionOf(String filename) {
        var dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType)
                ? contentType.toLowerCase(Locale.ROOT)
                : "application/octet-stream";
    }
}
