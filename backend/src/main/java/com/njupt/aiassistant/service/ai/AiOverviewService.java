package com.njupt.aiassistant.service.ai;

import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.vo.AiBalanceVO;
import com.njupt.aiassistant.vo.AiOverviewVO;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class AiOverviewService {
    private static final Duration MINIMUM_REFRESH_INTERVAL = Duration.ofSeconds(60);

    private final AiProviderConfigurationService configurationService;
    private final DeepSeekAccountClient accountClient;
    private final AiUsageService usageService;
    private volatile CachedBalance cache;

    public AiOverviewService(
            AiProviderConfigurationService configurationService,
            DeepSeekAccountClient accountClient,
            AiUsageService usageService
    ) {
        this.configurationService = configurationService;
        this.accountClient = accountClient;
        this.usageService = usageService;
    }

    public AiOverviewVO overview(String period, boolean refreshBalance) {
        var configView = configurationService.getConfiguration();
        AiBalanceVO balance;
        try {
            var runtime = configurationService.resolve();
            balance = balance(runtime, refreshBalance);
        } catch (BusinessException exception) {
            balance = new AiBalanceVO(
                    false,
                    "ERROR",
                    LocalDateTime.now(),
                    exception.getMessage(),
                    List.of()
            );
        }
        return new AiOverviewVO(
                configView,
                balance,
                usageService.summarize(period)
        );
    }

    public void remember(AiRuntimeConfig runtime, AiBalanceVO balance) {
        if (runtime.enabled()) {
            cache = new CachedBalance(fingerprint(runtime.apiKey()), LocalDateTime.now(), balance);
        }
    }

    private AiBalanceVO balance(AiRuntimeConfig runtime, boolean refresh) {
        if (!runtime.enabled() || runtime.apiKey().isBlank()) {
            return new AiBalanceVO(
                    false,
                    "NOT_CONFIGURED",
                    null,
                    "DeepSeek 尚未启用",
                    List.of()
            );
        }
        var fingerprint = fingerprint(runtime.apiKey());
        var current = cache;
        if (current != null
                && current.fingerprint().equals(fingerprint)
                && Duration.between(current.cachedAt(), LocalDateTime.now())
                        .compareTo(MINIMUM_REFRESH_INTERVAL) < 0) {
            return current.balance();
        }
        try {
            var fetched = accountClient.fetchBalance(runtime.apiKey());
            cache = new CachedBalance(fingerprint, LocalDateTime.now(), fetched);
            return fetched;
        } catch (BusinessException exception) {
            if (!refresh && current != null && current.fingerprint().equals(fingerprint)) {
                return new AiBalanceVO(
                        current.balance().available(),
                        "STALE",
                        current.balance().syncedAt(),
                        "实时同步失败，当前显示上次结果",
                        current.balance().balances()
                );
            }
            return new AiBalanceVO(
                    false,
                    "ERROR",
                    LocalDateTime.now(),
                    exception.getMessage(),
                    List.of()
            );
        }
    }

    private String fingerprint(String apiKey) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(apiKey.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record CachedBalance(
            String fingerprint,
            LocalDateTime cachedAt,
            AiBalanceVO balance
    ) {
    }
}
