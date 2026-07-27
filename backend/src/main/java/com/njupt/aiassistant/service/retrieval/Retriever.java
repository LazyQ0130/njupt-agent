package com.njupt.aiassistant.service.retrieval;

import com.njupt.aiassistant.entity.DocumentCategory;
import com.njupt.aiassistant.vo.RagSearchResultVO;

import java.util.List;

/**
 * Retrieval boundary reserved for vector, keyword and future hybrid strategies.
 */
public interface Retriever {
    List<RagSearchResultVO> retrieve(
            String question,
            int topK,
            DocumentCategory preferredCategory
    );
}
