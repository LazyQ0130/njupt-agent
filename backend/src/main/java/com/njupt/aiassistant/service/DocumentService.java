package com.njupt.aiassistant.service;

import com.njupt.aiassistant.vo.DocumentVO;
import com.njupt.aiassistant.vo.DocumentDetailVO;
import com.njupt.aiassistant.vo.DocumentReindexVO;
import com.njupt.aiassistant.dto.CuratedDocumentRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    List<DocumentVO> listDocuments();

    DocumentDetailVO getDocument(Long id);

    DocumentVO upload(MultipartFile file, String title, String source);

    DocumentVO upsertCurated(CuratedDocumentRequest request);

    void delete(Long id, boolean suppressReingest);

    DocumentReindexVO reindexUploadedDocuments();
}
