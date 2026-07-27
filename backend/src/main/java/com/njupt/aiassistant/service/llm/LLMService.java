package com.njupt.aiassistant.service.llm;

import com.njupt.aiassistant.vo.RagSearchResultVO;
import com.njupt.aiassistant.service.ai.DialogueMessage;

import java.util.List;

/**
 * Model-neutral answer generation boundary.
 */
public interface LLMService {

    String generateAnswer(String question, List<RagSearchResultVO> context);

    default String generateAnswer(
            String question,
            List<RagSearchResultVO> context,
            List<DialogueMessage> history
    ) {
        return generateAnswer(question, context);
    }
}
