package com.njupt.aiassistant.mapper;

import com.njupt.aiassistant.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageMapper extends JpaRepository<MessageEntity, Long> {
    List<MessageEntity> findByConversationIdOrderByCreatedTimeAsc(Long conversationId);
    void deleteByConversationIdIn(List<Long> conversationIds);
}
