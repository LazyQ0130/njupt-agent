package com.njupt.aiassistant.controller;

import com.njupt.aiassistant.common.ApiResponse;
import com.njupt.aiassistant.dto.AdminLoginRequest;
import com.njupt.aiassistant.service.AdminAuthService;
import com.njupt.aiassistant.vo.AdminTokenVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<AdminTokenVO> login(
            @Valid @RequestBody AdminLoginRequest request
    ) {
        return ApiResponse.success(adminAuthService.login(request));
    }
}
