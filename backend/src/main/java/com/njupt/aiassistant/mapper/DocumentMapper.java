package com.njupt.aiassistant.mapper;

import com.njupt.aiassistant.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentMapper extends JpaRepository<DocumentEntity, Long> {
    List<DocumentEntity> findAllByOrderByCreatedTimeDesc();

    Optional<DocumentEntity> findBySourceUrl(String sourceUrl);
}
