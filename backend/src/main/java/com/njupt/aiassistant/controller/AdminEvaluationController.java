package com.njupt.aiassistant.controller;

import com.njupt.aiassistant.common.ApiResponse;
import com.njupt.aiassistant.dto.EvaluationReviewRequest;
import com.njupt.aiassistant.service.EvaluationService;
import com.njupt.aiassistant.service.AdminAuditService;
import com.njupt.aiassistant.vo.EvaluationReportVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/evaluation")
public class AdminEvaluationController {

    private final EvaluationService evaluationService;
    private final AdminAuditService adminAuditService;

    public AdminEvaluationController(
            EvaluationService evaluationService,
            AdminAuditService adminAuditService
    ) {
        this.evaluationService = evaluationService;
        this.adminAuditService = adminAuditService;
    }

    @PostMapping("/run")
    public ApiResponse<EvaluationReportVO> run() {
        var report = evaluationService.run();
        adminAuditService.record(
                "EVALUATION_RUN",
                "questions=" + report.total()
                        + ", score=" + report.averageScore()
        );
        return ApiResponse.success(report);
    }

    @GetMapping("/{runId}")
    public ApiResponse<EvaluationReportVO> getReport(
            @PathVariable String runId
    ) {
        return ApiResponse.success(evaluationService.getReport(runId));
    }

    @PutMapping("/results/{resultId}/review")
    public ApiResponse<EvaluationReportVO> review(
            @PathVariable Long resultId,
            @Valid @org.springframework.web.bind.annotation.RequestBody
            EvaluationReviewRequest request
    ) {
        var report = evaluationService.review(resultId, request);
        adminAuditService.record(
                "EVALUATION_REVIEW",
                "resultId=" + resultId + ", accurate=" + request.accurate()
        );
        return ApiResponse.success(report);
    }
}
