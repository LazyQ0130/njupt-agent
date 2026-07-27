package com.njupt.aiassistant.controller;

import com.njupt.aiassistant.common.ApiResponse;
import com.njupt.aiassistant.service.AnonymousSessionService;
import com.njupt.aiassistant.vo.AnonymousSessionVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class AnonymousSessionController {

    private final AnonymousSessionService anonymousSessionService;

    public AnonymousSessionController(
            AnonymousSessionService anonymousSessionService
    ) {
        this.anonymousSessionService = anonymousSessionService;
    }

    @PostMapping("/anonymous")
    public ApiResponse<AnonymousSessionVO> create() {
        return ApiResponse.success(anonymousSessionService.create());
    }
}
