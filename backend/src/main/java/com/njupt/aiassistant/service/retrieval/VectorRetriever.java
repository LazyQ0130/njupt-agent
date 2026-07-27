package com.njupt.aiassistant.service.retrieval;

import com.njupt.aiassistant.client.RagClient;
import com.njupt.aiassistant.dto.RagSearchRequest;
import com.njupt.aiassistant.entity.DocumentCategory;
import com.njupt.aiassistant.vo.RagSearchResultVO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VectorRetriever implements Retriever {

    private final RagClient ragClient;

    public VectorRetriever(RagClient ragClient) {
        this.ragClient = ragClient;
    }

    @Override
    public List<RagSearchResultVO> retrieve(
            String question,
            int topK,
            DocumentCategory preferredCategory
    ) {
        return ragClient.search(new RagSearchRequest(
                question,
                topK,
                preferredCategory == null ? null : preferredCategory.name()
        ));
    }
}
