package com.njupt.aiassistant.mapper;

import com.njupt.aiassistant.entity.AiProviderConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiProviderConfigMapper extends JpaRepository<AiProviderConfigEntity, Long> {
    Optional<AiProviderConfigEntity> findByProvider(String provider);
}
