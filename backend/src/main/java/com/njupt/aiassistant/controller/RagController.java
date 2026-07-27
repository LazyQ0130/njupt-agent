package com.njupt.aiassistant.controller;

import com.njupt.aiassistant.client.RagClient;
import com.njupt.aiassistant.common.ApiResponse;
import com.njupt.aiassistant.dto.RagSearchRequest;
import com.njupt.aiassistant.vo.RagSearchResultVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagClient ragClient;

    public RagController(RagClient ragClient) {
        this.ragClient = ragClient;
    }

    @PostMapping("/search")
    public ApiResponse<List<RagSearchResultVO>> search(
            @Valid @RequestBody RagSearchRequest request
    ) {
        return ApiResponse.success(ragClient.search(request));
    }
}
