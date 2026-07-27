package com.njupt.aiassistant.controller;

import com.njupt.aiassistant.common.ApiResponse;
import com.njupt.aiassistant.service.FeedbackService;
import com.njupt.aiassistant.vo.QualityStatsVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/quality")
public class AdminQualityController {

    private final FeedbackService feedbackService;

    public AdminQualityController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping("/stats")
    public ApiResponse<QualityStatsVO> statistics() {
        return ApiResponse.success(feedbackService.statistics());
    }
}
