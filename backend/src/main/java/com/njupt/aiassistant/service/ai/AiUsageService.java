package com.njupt.aiassistant.service.ai;

import com.njupt.aiassistant.entity.AiUsageRecordEntity;
import com.njupt.aiassistant.mapper.AiUsageRecordMapper;
import com.njupt.aiassistant.vo.AiUsageSummaryVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

@Service
public class AiUsageService {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> PERIODS = Set.of("today", "7d", "30d", "all");

    private final AiUsageRecordMapper mapper;

    public AiUsageService(AiUsageRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            String model,
            Usage usage,
            long latencyMs
    ) {
        save(model, "SUCCESS", usage, latencyMs, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String model, long latencyMs, String errorType) {
        save(model, "FAILED", Usage.empty(), latencyMs, safeErrorType(errorType));
    }

    @Transactional(readOnly = true)
    public AiUsageSummaryVO summarize(String requestedPeriod) {
        var period = normalizePeriod(requestedPeriod);
        var aggregate = mapper.aggregateSince(startTime(period));
        var requests = safe(aggregate.getRequestCount());
        var successes = safe(aggregate.getSuccessCount());
        return new AiUsageSummaryVO(
                period,
                requests,
                successes,
                safe(aggregate.getFailureCount()),
                requests == 0 ? 0 : Math.round(successes * 10000.0 / requests) / 100.0,
                safe(aggregate.getPromptTokens()),
                safe(aggregate.getCompletionTokens()),
                safe(aggregate.getTotalTokens()),
                safe(aggregate.getCacheHitTokens()),
                safe(aggregate.getCacheMissTokens())
        );
    }

    private void save(
            String model,
            String status,
            Usage usage,
            long latencyMs,
            String errorType
    ) {
        var entity = new AiUsageRecordEntity();
        entity.setProvider(AiProviderConfigurationService.PROVIDER);
        entity.setModel(model == null ? "" : model);
        entity.setStatus(status);
        entity.setPromptTokens(nonNegative(usage.promptTokens()));
        entity.setCompletionTokens(nonNegative(usage.completionTokens()));
        entity.setTotalTokens(nonNegative(usage.totalTokens()));
        entity.setCacheHitTokens(nonNegative(usage.cacheHitTokens()));
        entity.setCacheMissTokens(nonNegative(usage.cacheMissTokens()));
        entity.setLatencyMs(Math.max(0, latencyMs));
        entity.setErrorType(errorType);
        mapper.save(entity);
    }

    private String normalizePeriod(String period) {
        var normalized = period == null ? "today" : period.trim().toLowerCase(Locale.ROOT);
        return PERIODS.contains(normalized) ? normalized : "today";
    }

    private LocalDateTime startTime(String period) {
        var today = LocalDate.now(REPORT_ZONE);
        return switch (period) {
            case "7d" -> today.minusDays(6).atStartOfDay();
            case "30d" -> today.minusDays(29).atStartOfDay();
            case "all" -> null;
            default -> today.atStartOfDay();
        };
    }

    private int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private long safe(Long value) {
        return value == null ? 0 : value;
    }

    private String safeErrorType(String value) {
        var normalized = value == null ? "UNKNOWN" : value.replaceAll("[^A-Z0-9_]", "_");
        return normalized.substring(0, Math.min(normalized.length(), 64));
    }

    public record Usage(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Integer cacheHitTokens,
            Integer cacheMissTokens
    ) {
        public static Usage empty() {
            return new Usage(0, 0, 0, 0, 0);
        }
    }
}
