package com.njupt.aiassistant.service.ai;

import java.util.List;

/**
 * Vendor-neutral LLM boundary. Future DeepSeek/OpenAI adapters implement this interface.
 */
public interface ChatAnswerProvider {

    String providerName();

    ChatAnswer answer(String question);

    default ChatAnswer answer(
            String question,
            List<DialogueMessage> history
    ) {
        return answer(question);
    }
}
