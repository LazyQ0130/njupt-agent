package com.njupt.aiassistant.mapper;

import com.njupt.aiassistant.entity.EvaluationResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationResultMapper extends JpaRepository<EvaluationResultEntity, Long> {
    List<EvaluationResultEntity> findByRunIdOrderByIdAsc(String runId);
}
