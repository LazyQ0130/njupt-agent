package com.njupt.aiassistant.service;

import com.njupt.aiassistant.dto.ConversationMessageRequest;
import com.njupt.aiassistant.vo.ConversationAnswerVO;
import com.njupt.aiassistant.vo.ConversationDetailVO;
import com.njupt.aiassistant.vo.ConversationVO;

public interface ConversationService {
    ConversationVO create(String anonymousSessionId);
    ConversationAnswerVO send(
            ConversationMessageRequest request,
            String anonymousSessionId
    );
    ConversationDetailVO get(
            Long conversationId,
            String anonymousSessionId
    );
}
