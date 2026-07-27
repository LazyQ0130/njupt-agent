package com.njupt.aiassistant.service.ai;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.config.AiProperties;
import com.njupt.aiassistant.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiProviderRouter {
    private final AiProperties aiProperties;
    private final AiProviderConfigurationService configurationService;
    private final List<ChatAnswerProvider> providers;

    public AiProviderRouter(
            AiProperties aiProperties,
            AiProviderConfigurationService configurationService,
            List<ChatAnswerProvider> providers
    ) {
        this.aiProperties = aiProperties;
        this.configurationService = configurationService;
        this.providers = providers;
    }

    public ChatAnswer answer(String question) {
        return provider().answer(question);
    }

    public ChatAnswer answer(String question, List<DialogueMessage> history) {
        return provider().answer(question, history);
    }

    private ChatAnswerProvider provider() {
        var providerName = configurationService.hasDatabaseOverride()
                ? "deepseek"
                : aiProperties.provider();
        return providers.stream()
                .filter(provider -> provider.providerName().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "未找到 AI Provider: " + providerName
                ));
    }
}
