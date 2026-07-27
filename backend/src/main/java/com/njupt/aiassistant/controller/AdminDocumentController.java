package com.njupt.aiassistant.controller;

import com.njupt.aiassistant.common.ApiResponse;
import com.njupt.aiassistant.service.DocumentService;
import com.njupt.aiassistant.service.AdminAuditService;
import com.njupt.aiassistant.vo.DocumentVO;
import com.njupt.aiassistant.vo.DocumentReindexVO;
import com.njupt.aiassistant.dto.CuratedDocumentRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/admin/documents")
@Validated
public class AdminDocumentController {

    private final DocumentService documentService;
    private final AdminAuditService adminAuditService;

    public AdminDocumentController(
            DocumentService documentService,
            AdminAuditService adminAuditService
    ) {
        this.documentService = documentService;
        this.adminAuditService = adminAuditService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentVO> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false)
            @Size(max = 255, message = "标题长度不能超过 255 个字符") String title,
            @RequestParam(required = false)
            @Size(max = 128, message = "来源长度不能超过 128 个字符") String source
    ) {
        var document = documentService.upload(file, title, source);
        adminAuditService.record(
                "DOCUMENT_UPLOAD",
                "documentId=" + document.id()
                        + ", filename=" + document.filename()
        );
        return ApiResponse.success(
                "文件上传成功，等待解析",
                document
        );
    }

    @PostMapping("/curated")
    public ApiResponse<DocumentVO> upsertCurated(
            @Valid @RequestBody CuratedDocumentRequest request
    ) {
        var document = documentService.upsertCurated(request);
        adminAuditService.record(
                "DOCUMENT_CURATE",
                "documentId=" + document.id()
                        + ", sourceUrl=" + document.sourceUrl()
        );
        return ApiResponse.success("人工知识已保存，等待索引", document);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable @Positive(message = "文档 ID 必须为正整数") Long id,
            @RequestParam(defaultValue = "true") boolean suppressReingest
    ) {
        documentService.delete(id, suppressReingest);
        adminAuditService.record(
                "DOCUMENT_DELETE",
                "documentId=" + id
                        + ", suppressReingest=" + suppressReingest
        );
        return ApiResponse.success(null);
    }

    @PostMapping("/reindex")
    public ApiResponse<DocumentReindexVO> reindexUploadedDocuments() {
        var result = documentService.reindexUploadedDocuments();
        adminAuditService.record(
                "DOCUMENT_REINDEX",
                "scheduledCount=" + result.scheduledCount()
                        + ", skippedCount=" + result.skippedCount()
        );
        return ApiResponse.success("已安排上传文件索引重建", result);
    }
}
