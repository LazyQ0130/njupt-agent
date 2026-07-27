package com.njupt.aiassistant.service;

import com.njupt.aiassistant.dto.AnswerFeedbackRequest;
import com.njupt.aiassistant.vo.AnswerFeedbackVO;
import com.njupt.aiassistant.vo.QualityStatsVO;

public interface FeedbackService {
    AnswerFeedbackVO submit(
            AnswerFeedbackRequest request,
            String anonymousSessionId
    );
    QualityStatsVO statistics();
}
