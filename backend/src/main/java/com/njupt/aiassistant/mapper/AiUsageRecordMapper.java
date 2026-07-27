package com.njupt.aiassistant.mapper;

import com.njupt.aiassistant.entity.AiUsageRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AiUsageRecordMapper extends JpaRepository<AiUsageRecordEntity, Long> {

    interface UsageAggregate {
        Long getRequestCount();
        Long getSuccessCount();
        Long getFailureCount();
        Long getPromptTokens();
        Long getCompletionTokens();
        Long getTotalTokens();
        Long getCacheHitTokens();
        Long getCacheMissTokens();
    }

    @Query(value = """
            SELECT COUNT(*) AS requestCount,
                   COALESCE(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS successCount,
                   COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failureCount,
                   COALESCE(SUM(prompt_tokens), 0) AS promptTokens,
                   COALESCE(SUM(completion_tokens), 0) AS completionTokens,
                   COALESCE(SUM(total_tokens), 0) AS totalTokens,
                   COALESCE(SUM(cache_hit_tokens), 0) AS cacheHitTokens,
                   COALESCE(SUM(cache_miss_tokens), 0) AS cacheMissTokens
              FROM ai_usage_record
             WHERE (:startTime IS NULL OR created_time >= :startTime)
            """, nativeQuery = true)
    UsageAggregate aggregateSince(@Param("startTime") LocalDateTime startTime);
}
