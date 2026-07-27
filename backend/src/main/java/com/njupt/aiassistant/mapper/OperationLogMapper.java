package com.njupt.aiassistant.mapper;

import com.njupt.aiassistant.entity.OperationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OperationLogMapper
        extends JpaRepository<OperationLogEntity, Long> {
    List<OperationLogEntity> findTop100ByOrderByCreatedTimeDesc();
}
