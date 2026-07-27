package com.njupt.aiassistant.controller;

import com.njupt.aiassistant.client.CrawlerClient;
import com.njupt.aiassistant.common.ApiResponse;
import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.dto.CustomCrawlerRequest;
import com.njupt.aiassistant.dto.CrawlerDateScope;
import com.njupt.aiassistant.dto.CrawlerScope;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.service.AdminAuditService;
import com.njupt.aiassistant.vo.CrawlerStatusVO;
import com.njupt.aiassistant.vo.CrawlerTriggerVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/admin/crawler")
public class AdminCrawlerController {

    private final CrawlerClient crawlerClient;
    private final AdminAuditService adminAuditService;

    public AdminCrawlerController(
            CrawlerClient crawlerClient,
            AdminAuditService adminAuditService
    ) {
        this.crawlerClient = crawlerClient;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public ApiResponse<CrawlerStatusVO> status() {
        return ApiResponse.success(crawlerClient.status());
    }

    @PostMapping("/run")
    public ApiResponse<CrawlerTriggerVO> run() {
        var result = crawlerClient.run();
        adminAuditService.record(
                "CRAWLER_RUN",
                "runId=" + result.runId()
        );
        return ApiResponse.success(result);
    }

    @PostMapping("/custom")
    public ApiResponse<CrawlerTriggerVO> custom(
            @Valid @RequestBody CustomCrawlerRequest request
    ) {
        var seedUrl = validateOfficialUrl(request.seedUrl());
        var dateScope = request.dateScope() == null
                ? CrawlerDateScope.RECENT
                : request.dateScope();
        var crawlScope = request.crawlScope() == null
                ? CrawlerScope.EXACT_HOST
                : request.crawlScope();
        var years = request.years() == null ? 2 : request.years();
        var normalized = new CustomCrawlerRequest(
                seedUrl.toString(),
                dateScope,
                crawlScope,
                years,
                request.maxPages(),
                request.forceReindex()
        );
        var result = crawlerClient.custom(normalized);
        adminAuditService.record(
                "CRAWLER_CUSTOM",
                "runId=" + result.runId()
                        + ", host=" + seedUrl.getHost()
                        + ", dateScope=" + dateScope
                        + ", crawlScope=" + crawlScope
                        + ", years=" + years
                        + ", forceReindex=" + request.forceReindex()
        );
        return ApiResponse.success(result);
    }

    @PostMapping("/reindex")
    public ApiResponse<CrawlerTriggerVO> reindex() {
        var result = crawlerClient.reindex();
        adminAuditService.record(
                "CRAWLER_REINDEX",
                "runId=" + result.runId()
        );
        return ApiResponse.success(result);
    }

    private URI validateOfficialUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value.trim()).normalize();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "采集网址格式不正确");
        }
        var host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !host.endsWith(".njupt.edu.cn")
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "仅支持南京邮电大学官方 HTTPS 子域名"
            );
        }
        return uri;
    }
}
