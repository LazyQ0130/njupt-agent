package com.njupt.aiassistant.mapper;

import com.njupt.aiassistant.entity.AnswerFeedbackEntity;
import com.njupt.aiassistant.entity.FeedbackType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnswerFeedbackMapper extends JpaRepository<AnswerFeedbackEntity, Long> {
    Optional<AnswerFeedbackEntity> findByChatHistoryId(Long chatHistoryId);
    long countByUserFeedback(FeedbackType feedback);
}
