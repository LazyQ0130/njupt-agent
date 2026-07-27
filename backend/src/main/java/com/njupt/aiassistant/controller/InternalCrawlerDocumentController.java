package com.njupt.aiassistant.controller;

import com.njupt.aiassistant.common.ApiResponse;
import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.config.CrawlerProperties;
import com.njupt.aiassistant.dto.CrawlerDocumentRequest;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.service.CrawlerDocumentService;
import com.njupt.aiassistant.vo.CrawlerDocumentIngestVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/crawler/documents")
public class InternalCrawlerDocumentController {

    private final CrawlerDocumentService crawlerDocumentService;
    private final CrawlerProperties properties;

    public InternalCrawlerDocumentController(
            CrawlerDocumentService crawlerDocumentService,
            CrawlerProperties properties
    ) {
        this.crawlerDocumentService = crawlerDocumentService;
        this.properties = properties;
    }

    @PostMapping
    public ApiResponse<CrawlerDocumentIngestVO> ingest(
            @RequestHeader(value = "X-Crawler-Token", required = false) String token,
            @Valid @RequestBody CrawlerDocumentRequest request
    ) {
        if (!properties.authorizes(token)) {
            throw new BusinessException(ErrorCode.CRAWLER_UNAUTHORIZED);
        }
        return ApiResponse.success(crawlerDocumentService.ingest(request));
    }
}
