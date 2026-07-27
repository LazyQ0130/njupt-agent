package com.njupt.aiassistant.service.impl;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.client.RagClient;
import com.njupt.aiassistant.config.CrawlerProperties;
import com.njupt.aiassistant.dto.CuratedDocumentRequest;
import com.njupt.aiassistant.entity.DocumentExclusionEntity;
import com.njupt.aiassistant.entity.DocumentEntity;
import com.njupt.aiassistant.entity.DocumentStatus;
import com.njupt.aiassistant.entity.DocumentSourceType;
import com.njupt.aiassistant.mapper.DocumentMapper;
import com.njupt.aiassistant.mapper.DocumentExclusionMapper;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.service.DocumentService;
import com.njupt.aiassistant.service.indexing.DocumentUploadedEvent;
import com.njupt.aiassistant.service.indexing.WebDocumentIndexEvent;
import com.njupt.aiassistant.service.storage.FileStorageService;
import com.njupt.aiassistant.vo.DocumentVO;
import com.njupt.aiassistant.vo.DocumentDetailVO;
import com.njupt.aiassistant.vo.DocumentReindexVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log =
            LoggerFactory.getLogger(DocumentServiceImpl.class);

    private final DocumentMapper documentMapper;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final DocumentExclusionMapper exclusionMapper;
    private final RagClient ragClient;
    private final CrawlerProperties crawlerProperties;

    public DocumentServiceImpl(
            DocumentMapper documentMapper,
            FileStorageService fileStorageService,
            ApplicationEventPublisher eventPublisher,
            DocumentExclusionMapper exclusionMapper,
            RagClient ragClient,
            CrawlerProperties crawlerProperties
    ) {
        this.documentMapper = documentMapper;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
        this.exclusionMapper = exclusionMapper;
        this.ragClient = ragClient;
        this.crawlerProperties = crawlerProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentVO> listDocuments() {
        return documentMapper.findAllByOrderByCreatedTimeDesc().stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDetailVO getDocument(Long id) {
        return documentMapper.findById(id)
                .map(this::toDetailVO)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    @Override
    @Transactional
    public DocumentVO upload(MultipartFile file, String title, String source) {
        var storedFile = fileStorageService.store(file);
        try {
            var entity = new DocumentEntity();
            entity.setTitle(StringUtils.hasText(title)
                    ? title.trim()
                    : stripExtension(storedFile.originalFilename()));
            entity.setFilename(storedFile.originalFilename());
            entity.setSource(StringUtils.hasText(source) ? source.trim() : "未知来源");
            entity.setType(storedFile.extension());
            entity.setContent(null);
            entity.setStatus(DocumentStatus.PROCESSING);
            entity.setSourceType(DocumentSourceType.UPLOADED_FILE);
            entity.setStoragePath(storedFile.absolutePath().toString());
            entity.setFileSize(storedFile.size());
            entity.setContentType(storedFile.contentType());
            entity.setCreatedTime(LocalDateTime.now());
            var saved = documentMapper.save(entity);
            eventPublisher.publishEvent(new DocumentUploadedEvent(
                    saved.getId(),
                    storedFile.absolutePath(),
                    storedFile.originalFilename(),
                    storedFile.extension(),
                    saved.getSource(),
                    saved.getCreatedTime()
            ));
            return toVO(saved);
        } catch (RuntimeException exception) {
            fileStorageService.deleteQuietly(storedFile.absolutePath());
            throw exception;
        }
    }

    @Override
    @Transactional
    public DocumentVO upsertCurated(CuratedDocumentRequest request) {
        var sourceUrl = validateOfficialUrl(request.sourceUrl());
        exclusionMapper.findBySourceUrl(sourceUrl).ifPresent(exclusionMapper::delete);

        var title = request.title().trim();
        var content = request.content().trim();
        var source = request.source().trim();
        var publishedTime = request.publishedTime() == null
                ? null
                : request.publishedTime()
                .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                .toLocalDateTime();
        var verifiedTime = request.verifiedTime()
                .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                .toLocalDateTime();
        var contentHash = sha256(
                title + "\n" + content + "\n"
                        + (publishedTime == null ? "" : publishedTime)
        );

        var existing = documentMapper.findBySourceUrl(sourceUrl).orElse(null);
        if (existing != null
                && existing.getSourceType()
                == DocumentSourceType.CURATED_OFFICIAL
                && contentHash.equals(existing.getContentHash())
                && existing.getStatus() == DocumentStatus.COMPLETED) {
            existing.setCrawlTime(verifiedTime);
            existing.setLastUpdated(
                    publishedTime == null ? verifiedTime : publishedTime
            );
            return toVO(documentMapper.save(existing));
        }

        var entity = existing == null ? new DocumentEntity() : existing;
        entity.setTitle(title);
        entity.setFilename(title);
        entity.setSource(source);
        entity.setType("TEXT");
        entity.setContent(content);
        entity.setStatus(DocumentStatus.PROCESSING);
        entity.setStoragePath(null);
        entity.setFileSize((long) content.getBytes(StandardCharsets.UTF_8).length);
        entity.setContentType("text/plain;charset=UTF-8");
        entity.setSourceType(DocumentSourceType.CURATED_OFFICIAL);
        entity.setSourceUrl(sourceUrl);
        entity.setCategory(request.category());
        entity.setCrawlTime(verifiedTime);
        entity.setLastUpdated(
                publishedTime == null ? verifiedTime : publishedTime
        );
        entity.setContentHash(contentHash);
        entity.setVectorDocumentId(null);
        if (existing == null) {
            entity.setCreatedTime(LocalDateTime.now());
        }
        var saved = documentMapper.save(entity);
        eventPublisher.publishEvent(new WebDocumentIndexEvent(saved.getId()));
        return toVO(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, boolean suppressReingest) {
        var document = documentMapper.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DOCUMENT_NOT_FOUND
                ));
        if (document.getStatus() == DocumentStatus.PROCESSING
                || document.getStatus() == DocumentStatus.UPLOADING) {
            throw new BusinessException(ErrorCode.DOCUMENT_BUSY);
        }

        var vectorDocumentId = resolveVectorDocumentId(document);
        if (vectorDocumentId != null) {
            ragClient.delete(vectorDocumentId);
        }

        if (suppressReingest
                && StringUtils.hasText(document.getSourceUrl())) {
            exclusionMapper.findBySourceUrl(document.getSourceUrl())
                    .orElseGet(() -> {
                        var exclusion = new DocumentExclusionEntity();
                        exclusion.setSourceUrl(document.getSourceUrl());
                        exclusion.setReason(
                                "管理员删除：" + document.getTitle()
                        );
                        return exclusionMapper.save(exclusion);
                    });
        }

        var storedPath = document.getStoragePath();
        documentMapper.delete(document);
        if (StringUtils.hasText(storedPath)) {
            fileStorageService.deleteQuietly(Path.of(storedPath));
        }
    }

    @Override
    @Transactional
    public DocumentReindexVO reindexUploadedDocuments() {
        var scheduledCount = 0;
        var skippedCount = 0;
        for (var document : documentMapper.findAllByOrderByCreatedTimeDesc()) {
            if (document.getSourceType() != DocumentSourceType.UPLOADED_FILE) {
                continue;
            }
            if (document.getStatus() == DocumentStatus.PROCESSING
                    || document.getStatus() == DocumentStatus.UPLOADING
                    || !StringUtils.hasText(document.getStoragePath())) {
                skippedCount++;
                log.info(
                        "Skipping uploaded document reindex id={}, status={}, pathPresent={}",
                        document.getId(),
                        document.getStatus(),
                        StringUtils.hasText(document.getStoragePath())
                );
                continue;
            }
            var path = Path.of(document.getStoragePath());
            if (!Files.isRegularFile(path)) {
                skippedCount++;
                log.warn(
                        "Skipping uploaded document reindex id={} because source file is missing: {}",
                        document.getId(),
                        path
                );
                continue;
            }
            document.setStatus(DocumentStatus.PROCESSING);
            documentMapper.save(document);
            eventPublisher.publishEvent(new DocumentUploadedEvent(
                    document.getId(),
                    path,
                    document.getFilename(),
                    document.getType(),
                    document.getSource(),
                    document.getCreatedTime()
            ));
            scheduledCount++;
        }
        return new DocumentReindexVO(scheduledCount, skippedCount);
    }

    private String stripExtension(String filename) {
        var dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private String validateOfficialUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value.trim()).normalize();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "官方来源网址格式不正确",
                    exception
            );
        }
        var host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        var allowed = host.endsWith(".njupt.edu.cn")
                || crawlerProperties.allowedDomainSet().contains(host)
                || "njupt.91job.org.cn".equals(host);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !allowed
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "仅允许南邮官方 HTTPS 来源"
            );
        }
        return uri.toString();
    }

    private String resolveVectorDocumentId(DocumentEntity document) {
        if (StringUtils.hasText(document.getVectorDocumentId())) {
            return document.getVectorDocumentId();
        }
        if (document.getStatus() != DocumentStatus.COMPLETED) {
            return null;
        }
        if (document.getSourceType() != DocumentSourceType.UPLOADED_FILE) {
            if (!StringUtils.hasText(document.getSourceUrl())
                    || !StringUtils.hasText(document.getContentHash())) {
                return null;
            }
            return sha256(
                    document.getSourceUrl() + "\0" + document.getContentHash()
            );
        }
        if (!StringUtils.hasText(document.getStoragePath())) {
            return null;
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(Files.readAllBytes(Path.of(document.getStoragePath())));
            digest.update((byte) 0);
            digest.update(document.getFilename().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(document.getSource().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.io.IOException | NoSuchAlgorithmException exception) {
            throw new BusinessException(
                    ErrorCode.FILE_STORAGE_FAILED,
                    "无法确定文档向量标识",
                    exception
            );
        }
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private DocumentVO toVO(DocumentEntity entity) {
        return new DocumentVO(
                entity.getId(),
                entity.getTitle(),
                entity.getFilename(),
                entity.getSource(),
                entity.getType(),
                entity.getStatus(),
                entity.getFileSize(),
                entity.getContentType(),
                entity.getSourceType(),
                entity.getSourceUrl(),
                entity.getCategory(),
                entity.getCrawlTime(),
                entity.getLastUpdated(),
                entity.getContentHash(),
                entity.getCreatedTime()
        );
    }

    private DocumentDetailVO toDetailVO(DocumentEntity entity) {
        return new DocumentDetailVO(
                entity.getId(),
                entity.getTitle(),
                entity.getFilename(),
                entity.getSource(),
                entity.getType(),
                entity.getContent(),
                entity.getStatus(),
                entity.getFileSize(),
                entity.getContentType(),
                entity.getSourceType(),
                entity.getSourceUrl(),
                entity.getCategory(),
                entity.getCrawlTime(),
                entity.getLastUpdated(),
                entity.getContentHash(),
                entity.getCreatedTime()
        );
    }
}
