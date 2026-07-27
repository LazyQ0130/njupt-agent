package com.njupt.aiassistant.mapper;

import com.njupt.aiassistant.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ConversationMapper extends JpaRepository<ConversationEntity, Long> {
    @Query("select c.id from ConversationEntity c where c.expireTime < :now")
    List<Long> findExpiredIds(LocalDateTime now);

    void deleteByIdIn(List<Long> ids);
}
