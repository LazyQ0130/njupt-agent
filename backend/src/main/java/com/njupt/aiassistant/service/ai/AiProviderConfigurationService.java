package com.njupt.aiassistant.service.ai;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.config.DeepSeekProperties;
import com.njupt.aiassistant.entity.AiProviderConfigEntity;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.mapper.AiProviderConfigMapper;
import com.njupt.aiassistant.vo.AiProviderConfigVO;
import com.njupt.aiassistant.vo.AiProviderSaveVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
public class AiProviderConfigurationService {
    public static final String PROVIDER = "DEEPSEEK";
    public static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final Set<String> ADMIN_MODELS = Set.of(
            "deepseek-v4-flash",
            "deepseek-v4-pro"
    );

    private final AiProviderConfigMapper mapper;
    private final ApiKeyCipherService cipherService;
    private final DeepSeekProperties environment;
    private final DeepSeekAccountClient accountClient;

    public AiProviderConfigurationService(
            AiProviderConfigMapper mapper,
            ApiKeyCipherService cipherService,
            DeepSeekProperties environment,
            DeepSeekAccountClient accountClient
    ) {
        this.mapper = mapper;
        this.cipherService = cipherService;
        this.environment = environment;
        this.accountClient = accountClient;
    }

    @Transactional(readOnly = true)
    public AiRuntimeConfig resolve() {
        var stored = mapper.findByProvider(PROVIDER);
        if (stored.isPresent()) {
            var entity = stored.get();
            var hasKey = StringUtils.hasText(entity.getEncryptedApiKey());
            var enabled = Boolean.TRUE.equals(entity.getEnabled()) && hasKey;
            var apiKey = enabled ? cipherService.decrypt(entity.getEncryptedApiKey()) : "";
            return new AiRuntimeConfig(apiKey, entity.getModel(), enabled, "DATABASE");
        }
        var enabled = StringUtils.hasText(environment.apiKey());
        return new AiRuntimeConfig(
                environment.apiKey(),
                environment.model(),
                enabled,
                enabled ? "ENVIRONMENT" : "NONE"
        );
    }

    @Transactional(readOnly = true)
    public boolean hasDatabaseOverride() {
        return mapper.findByProvider(PROVIDER).isPresent();
    }

    @Transactional(readOnly = true)
    public AiProviderConfigVO getConfiguration() {
        var stored = mapper.findByProvider(PROVIDER);
        if (stored.isPresent()) {
            return toView(stored.get());
        }
        var configured = StringUtils.hasText(environment.apiKey());
        return new AiProviderConfigVO(
                configured,
                configured,
                configured ? "ENVIRONMENT" : "NONE",
                configured ? mask(environment.apiKey()) : "",
                environment.model(),
                null,
                cipherService.isAvailable()
        );
    }

    @Transactional
    public AiProviderSaveVO validateAndSave(String apiKey, String model) {
        if (!cipherService.isAvailable()) {
            throw new BusinessException(ErrorCode.AI_CONFIG_STORAGE_UNAVAILABLE);
        }
        var normalizedKey = apiKey == null ? "" : apiKey.trim();
        if (!StringUtils.hasText(normalizedKey)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "API Key 不能为空");
        }
        var normalizedModel = normalizeModel(model);

        // Validation happens before mutating the persisted row. A failed request
        // therefore leaves the last known-good credential untouched.
        var balance = accountClient.fetchBalance(normalizedKey);

        var entity = mapper.findByProvider(PROVIDER)
                .orElseGet(AiProviderConfigEntity::new);
        entity.setProvider(PROVIDER);
        entity.setEncryptedApiKey(cipherService.encrypt(normalizedKey));
        entity.setKeyLastFour(lastFour(normalizedKey));
        entity.setModel(normalizedModel);
        entity.setEnabled(true);
        entity.setVerifiedTime(LocalDateTime.now());
        var saved = mapper.save(entity);
        return new AiProviderSaveVO(toView(saved), balance);
    }

    @Transactional
    public AiProviderConfigVO updateStatus(boolean enabled) {
        var entity = mapper.findByProvider(PROVIDER)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "请先在后台保存 DeepSeek API Key"
                ));
        if (enabled && !StringUtils.hasText(entity.getEncryptedApiKey())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "Key 已清除，请重新保存后再启用"
            );
        }
        entity.setEnabled(enabled);
        return toView(mapper.save(entity));
    }

    @Transactional
    public AiProviderConfigVO clear() {
        var entity = mapper.findByProvider(PROVIDER)
                .orElseGet(AiProviderConfigEntity::new);
        entity.setProvider(PROVIDER);
        entity.setEncryptedApiKey(null);
        entity.setKeyLastFour(null);
        entity.setModel(StringUtils.hasText(entity.getModel())
                ? entity.getModel()
                : DEFAULT_MODEL);
        entity.setEnabled(false);
        entity.setVerifiedTime(null);
        return toView(mapper.save(entity));
    }

    private AiProviderConfigVO toView(AiProviderConfigEntity entity) {
        var configured = StringUtils.hasText(entity.getEncryptedApiKey());
        return new AiProviderConfigVO(
                configured,
                configured && Boolean.TRUE.equals(entity.getEnabled()),
                "DATABASE",
                configured ? "••••••••" + entity.getKeyLastFour() : "",
                entity.getModel(),
                entity.getVerifiedTime(),
                cipherService.isAvailable()
        );
    }

    private String normalizeModel(String model) {
        var normalized = model == null ? "" : model.trim().toLowerCase(Locale.ROOT);
        if (!ADMIN_MODELS.contains(normalized)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "仅支持 deepseek-v4-flash 或 deepseek-v4-pro"
            );
        }
        return normalized;
    }

    private String mask(String key) {
        return key.length() <= 4 ? "••••••••" : "••••••••" + lastFour(key);
    }

    private String lastFour(String key) {
        return key.length() <= 4 ? key : key.substring(key.length() - 4);
    }
}
