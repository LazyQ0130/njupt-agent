package com.njupt.aiassistant.service;

import com.njupt.aiassistant.dto.CrawlerDocumentRequest;
import com.njupt.aiassistant.vo.CrawlerDocumentIngestVO;

public interface CrawlerDocumentService {

    CrawlerDocumentIngestVO ingest(CrawlerDocumentRequest request);
}
