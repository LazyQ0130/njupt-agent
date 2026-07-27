package com.njupt.aiassistant.service.impl;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.config.CrawlerProperties;
import com.njupt.aiassistant.dto.CrawlerDocumentRequest;
import com.njupt.aiassistant.entity.DocumentEntity;
import com.njupt.aiassistant.entity.DocumentSourceType;
import com.njupt.aiassistant.entity.DocumentStatus;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.mapper.DocumentMapper;
import com.njupt.aiassistant.mapper.DocumentExclusionMapper;
import com.njupt.aiassistant.service.CrawlerDocumentService;
import com.njupt.aiassistant.service.indexing.WebDocumentIndexEvent;
import com.njupt.aiassistant.vo.CrawlerDocumentIngestVO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class CrawlerDocumentServiceImpl implements CrawlerDocumentService {

    private final DocumentMapper documentMapper;
    private final CrawlerProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final DocumentExclusionMapper exclusionMapper;

    public CrawlerDocumentServiceImpl(
            DocumentMapper documentMapper,
            CrawlerProperties properties,
            ApplicationEventPublisher eventPublisher,
            DocumentExclusionMapper exclusionMapper
    ) {
        this.documentMapper = documentMapper;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.exclusionMapper = exclusionMapper;
    }

    @Override
    @Transactional
    public CrawlerDocumentIngestVO ingest(CrawlerDocumentRequest request) {
        var sourceUrl = validateSourceUrl(request.sourceUrl());
        if (exclusionMapper.existsBySourceUrl(sourceUrl)) {
            return new CrawlerDocumentIngestVO(
                    null,
                    "EXCLUDED",
                    null
            );
        }
        var existing = documentMapper.findBySourceUrl(sourceUrl).orElse(null);
        if (existing != null
                && request.contentHash().equals(existing.getContentHash())
                && !request.forceReindex()) {
            existing.setCrawlTime(toLocalTime(request.crawlTime()));
            existing.setLastUpdated(toLocalTime(request.lastUpdated()));
            var saved = documentMapper.save(existing);
            return new CrawlerDocumentIngestVO(
                    saved.getId(),
                    "UNCHANGED",
                    saved.getStatus()
            );
        }

        var entity = existing == null ? new DocumentEntity() : existing;
        entity.setTitle(request.title().trim());
        entity.setFilename(request.title().trim());
        entity.setSource(request.source().trim());
        entity.setType("HTML");
        entity.setContent(request.content().trim());
        entity.setStatus(DocumentStatus.PROCESSING);
        entity.setStoragePath(null);
        entity.setFileSize((long) request.content().getBytes(StandardCharsets.UTF_8).length);
        entity.setContentType("text/html;charset=UTF-8");
        entity.setSourceType(DocumentSourceType.OFFICIAL_WEBSITE);
        entity.setSourceUrl(sourceUrl);
        entity.setCategory(request.category());
        entity.setCrawlTime(toLocalTime(request.crawlTime()));
        entity.setLastUpdated(toLocalTime(
                request.publishedTime() == null
                        ? request.lastUpdated()
                        : request.publishedTime()
        ));
        entity.setContentHash(request.contentHash());
        if (existing == null) {
            entity.setCreatedTime(LocalDateTime.now());
        }

        var saved = documentMapper.save(entity);
        eventPublisher.publishEvent(new WebDocumentIndexEvent(saved.getId()));
        var action = existing == null
                ? "CREATED"
                : request.forceReindex() ? "REINDEXED" : "UPDATED";
        return new CrawlerDocumentIngestVO(saved.getId(), action, saved.getStatus());
    }

    private String validateSourceUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "来源 URL 不合法", exception);
        }
        var host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || (!properties.allowedDomainSet().contains(host)
                && !host.endsWith(".njupt.edu.cn"))
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "仅允许采集南京邮电大学 HTTPS 官方站点"
            );
        }
        return uri.normalize().toString();
    }

    private LocalDateTime toLocalTime(java.time.OffsetDateTime value) {
        return value.atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
    }
}
