package com.njupt.aiassistant.controller;

import com.njupt.aiassistant.common.ApiResponse;
import com.njupt.aiassistant.service.DocumentService;
import com.njupt.aiassistant.vo.DocumentDetailVO;
import com.njupt.aiassistant.vo.DocumentVO;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@Validated
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ApiResponse<List<DocumentVO>> listDocuments() {
        return ApiResponse.success(documentService.listDocuments());
    }

    @GetMapping("/{id}")
    public ApiResponse<DocumentDetailVO> getDocument(
            @PathVariable @Positive(message = "文档 ID 必须为正整数") Long id
    ) {
        return ApiResponse.success(documentService.getDocument(id));
    }
}
