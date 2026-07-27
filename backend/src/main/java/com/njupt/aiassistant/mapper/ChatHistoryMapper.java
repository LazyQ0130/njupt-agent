package com.njupt.aiassistant.mapper;

import com.njupt.aiassistant.entity.ChatHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatHistoryMapper extends JpaRepository<ChatHistoryEntity, Long> {
    List<ChatHistoryEntity> findAllByOrderByCreatedTimeDesc();

    List<ChatHistoryEntity> findByUserIdOrderByCreatedTimeDesc(Long userId);

    List<ChatHistoryEntity>
            findByAnonymousSessionIdOrderByCreatedTimeDesc(
                    String anonymousSessionId
            );

    void deleteByConversationIdIn(List<Long> conversationIds);

    void deleteByConversationIdIsNullAndCreatedTimeBefore(
            java.time.LocalDateTime cutoff
    );
}
