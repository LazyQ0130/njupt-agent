package com.njupt.aiassistant.service.indexing;

import com.njupt.aiassistant.client.RagClient;
import com.njupt.aiassistant.entity.DocumentStatus;
import com.njupt.aiassistant.dto.WebDocumentIndexRequest;
import com.njupt.aiassistant.mapper.DocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;
import com.njupt.aiassistant.entity.DocumentCategory;

@Service
@ConditionalOnProperty(
        prefix = "rag.service",
        name = "indexing-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class KnowledgeIndexingService {

    private static final Logger log =
            LoggerFactory.getLogger(KnowledgeIndexingService.class);

    private final RagClient ragClient;
    private final DocumentMapper documentMapper;

    public KnowledgeIndexingService(
            RagClient ragClient,
            DocumentMapper documentMapper
    ) {
        this.ragClient = ragClient;
        this.documentMapper = documentMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("knowledgeIndexingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void index(DocumentUploadedEvent event) {
        var document = documentMapper.findById(event.documentId()).orElse(null);
        if (document == null) {
            return;
        }
        if ("DOC".equalsIgnoreCase(event.type())) {
            document.setStatus(DocumentStatus.FAILED);
            documentMapper.save(document);
            return;
        }

        try {
            var result = ragClient.index(
                    event.path(),
                    event.filename(),
                    event.source(),
                    event.uploadTime()
            );
            var indexedCategory = result.category();
            if (indexedCategory == null || indexedCategory.isBlank()) {
                document.setCategory(null);
            } else {
                try {
                    document.setCategory(
                            DocumentCategory.valueOf(indexedCategory.trim())
                    );
                } catch (IllegalArgumentException exception) {
                    log.warn(
                            "Ignoring unknown indexed category={} for document id={}",
                            indexedCategory,
                            event.documentId()
                    );
                }
            }
            document.setVectorDocumentId(result.documentId());
            document.setStatus(DocumentStatus.COMPLETED);
        } catch (RuntimeException exception) {
            document.setStatus(DocumentStatus.FAILED);
            log.warn(
                    "Knowledge indexing failed for document id={}, exceptionType={}, message={}",
                    event.documentId(),
                    exception.getClass().getName(),
                    exception.getMessage(),
                    exception
            );
        }
        documentMapper.save(document);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("knowledgeIndexingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void indexWebDocument(WebDocumentIndexEvent event) {
        var document = documentMapper.findById(event.documentId()).orElse(null);
        if (document == null) {
            return;
        }
        try {
            var result = ragClient.indexWeb(new WebDocumentIndexRequest(
                    document.getTitle(),
                    document.getContent(),
                    document.getSource(),
                    document.getSourceUrl(),
                    document.getCategory().name(),
                    document.getCrawlTime(),
                    document.getLastUpdated(),
                    document.getContentHash(),
                    document.getSourceType().name()
            ));
            document.setVectorDocumentId(result.documentId());
            document.setStatus(DocumentStatus.COMPLETED);
        } catch (RuntimeException exception) {
            document.setStatus(DocumentStatus.FAILED);
            log.warn(
                    "Web knowledge indexing failed for document id={}, exceptionType={}, message={}",
                    event.documentId(),
                    exception.getClass().getName(),
                    exception.getMessage(),
                    exception
            );
        }
        documentMapper.save(document);
    }
}
