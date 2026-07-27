package com.njupt.aiassistant.controller;

import com.njupt.aiassistant.common.ApiResponse;
import com.njupt.aiassistant.service.AdminAuditService;
import com.njupt.aiassistant.vo.OperationLogVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/operations")
public class AdminOperationLogController {

    private final AdminAuditService adminAuditService;

    public AdminOperationLogController(
            AdminAuditService adminAuditService
    ) {
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    public ApiResponse<List<OperationLogVO>> latest() {
        return ApiResponse.success(adminAuditService.latest());
    }
}
