package com.njupt.aiassistant.mapper;

import com.njupt.aiassistant.entity.DocumentExclusionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DocumentExclusionMapper
        extends JpaRepository<DocumentExclusionEntity, Long> {

    boolean existsBySourceUrl(String sourceUrl);

    Optional<DocumentExclusionEntity> findBySourceUrl(String sourceUrl);
}
