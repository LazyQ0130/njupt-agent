package com.njupt.aiassistant.client;

import com.njupt.aiassistant.dto.RagSearchRequest;
import com.njupt.aiassistant.dto.WebDocumentIndexRequest;
import com.njupt.aiassistant.vo.RagIndexResponseVO;
import com.njupt.aiassistant.vo.RagSearchResultVO;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public interface RagClient {

    List<RagSearchResultVO> search(RagSearchRequest request);

    RagIndexResponseVO index(
            Path path,
            String filename,
            String source,
            LocalDateTime uploadTime
    );

    RagIndexResponseVO indexWeb(WebDocumentIndexRequest request);

    void delete(String documentId);
}
