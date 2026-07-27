package com.njupt.aiassistant.controller;

import com.njupt.aiassistant.common.ApiResponse;
import com.njupt.aiassistant.dto.AiProviderConfigRequest;
import com.njupt.aiassistant.dto.AiProviderStatusRequest;
import com.njupt.aiassistant.service.AdminAuditService;
import com.njupt.aiassistant.service.ai.AiOverviewService;
import com.njupt.aiassistant.service.ai.AiProviderConfigurationService;
import com.njupt.aiassistant.vo.AiOverviewVO;
import com.njupt.aiassistant.vo.AiProviderConfigVO;
import com.njupt.aiassistant.vo.AiProviderSaveVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai")
public class AdminAiSettingsController {
    private final AiProviderConfigurationService configurationService;
    private final AiOverviewService overviewService;
    private final AdminAuditService auditService;

    public AdminAiSettingsController(
            AiProviderConfigurationService configurationService,
            AiOverviewService overviewService,
            AdminAuditService auditService
    ) {
        this.configurationService = configurationService;
        this.overviewService = overviewService;
        this.auditService = auditService;
    }

    @GetMapping("/config")
    public ApiResponse<AiProviderConfigVO> configuration() {
        return ApiResponse.success(configurationService.getConfiguration());
    }

    @PutMapping("/config")
    public ApiResponse<AiProviderSaveVO> save(
            @Valid @RequestBody AiProviderConfigRequest request
    ) {
        var result = configurationService.validateAndSave(
                request.apiKey(),
                request.model()
        );
        auditService.record("AI_CONFIG_REPLACED", "DeepSeek model=" + result.config().model());
        return ApiResponse.success("DeepSeek 配置已验证并保存", result);
    }

    @PatchMapping("/config/status")
    public ApiResponse<AiProviderConfigVO> status(
            @Valid @RequestBody AiProviderStatusRequest request
    ) {
        var result = configurationService.updateStatus(request.enabled());
        auditService.record(
                request.enabled() ? "AI_CONFIG_ENABLED" : "AI_CONFIG_DISABLED",
                "DeepSeek"
        );
        return ApiResponse.success(
                request.enabled() ? "DeepSeek 已启用" : "DeepSeek 已停用",
                result
        );
    }

    @DeleteMapping("/config")
    public ApiResponse<AiProviderConfigVO> clear() {
        var result = configurationService.clear();
        auditService.record("AI_CONFIG_CLEARED", "DeepSeek");
        return ApiResponse.success("DeepSeek Key 已清除", result);
    }

    @GetMapping("/overview")
    public ApiResponse<AiOverviewVO> overview(
            @RequestParam(defaultValue = "today") String period,
            @RequestParam(defaultValue = "false") boolean refreshBalance
    ) {
        return ApiResponse.success(overviewService.overview(period, refreshBalance));
    }
}
