package com.njupt.aiassistant.service.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.config.DeepSeekProperties;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.service.ai.AiProviderConfigurationService;
import com.njupt.aiassistant.service.ai.AiRuntimeConfig;
import com.njupt.aiassistant.service.ai.AiUsageService;
import com.njupt.aiassistant.service.ai.DialogueMessage;
import com.njupt.aiassistant.vo.RagSearchResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DeepSeekLLMService implements LLMService {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekLLMService.class);
    private static final String FRIENDLY_ERROR = "AI服务暂时不可用，请稍后重试。";

    private final RestClient restClient;
    private final AiProviderConfigurationService configurationService;
    private final AiUsageService usageService;
    private final DeepSeekProperties properties;
    private final RagPromptBuilder promptBuilder;

    @Autowired
    public DeepSeekLLMService(
            @Qualifier("deepSeekRestClient") RestClient restClient,
            AiProviderConfigurationService configurationService,
            AiUsageService usageService,
            DeepSeekProperties properties,
            RagPromptBuilder promptBuilder
    ) {
        this.restClient = restClient;
        this.configurationService = configurationService;
        this.usageService = usageService;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
    }

    DeepSeekLLMService(
            RestClient restClient,
            DeepSeekProperties properties,
            RagPromptBuilder promptBuilder
    ) {
        this.restClient = restClient;
        this.configurationService = null;
        this.usageService = null;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public String generateAnswer(String question, List<RagSearchResultVO> context) {
        return generateAnswer(question, context, List.of());
    }

    @Override
    public String generateAnswer(
            String question,
            List<RagSearchResultVO> context,
            List<DialogueMessage> history
    ) {
        var runtime = resolveRuntime();
        if (!runtime.enabled() || !StringUtils.hasText(runtime.apiKey())) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, FRIENDLY_ERROR);
        }

        var request = new ChatCompletionRequest(
                runtime.model(),
                List.of(
                        new Message("system", promptBuilder.systemPrompt()),
                        new Message(
                                "user",
                                promptBuilder.buildUserMessage(question, context, history)
                        )
                ),
                false,
                maxTokens()
        );
        var started = System.nanoTime();

        try {
            var response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + runtime.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            var answer = extractAnswer(response);
            safeRecordSuccess(runtime.model(), response == null ? null : response.usage(),
                    elapsedMillis(started));
            return answer;
        } catch (RestClientResponseException exception) {
            safeRecordFailure(runtime.model(), elapsedMillis(started),
                    "HTTP_" + exception.getStatusCode().value());
            throw new BusinessException(
                    ErrorCode.AI_SERVICE_FAILED,
                    FRIENDLY_ERROR,
                    exception
            );
        } catch (RestClientException exception) {
            safeRecordFailure(runtime.model(), elapsedMillis(started), "NETWORK");
            throw new BusinessException(
                    ErrorCode.AI_SERVICE_UNAVAILABLE,
                    FRIENDLY_ERROR,
                    exception
            );
        } catch (BusinessException exception) {
            safeRecordFailure(runtime.model(), elapsedMillis(started), "INVALID_RESPONSE");
            throw exception;
        }
    }

    private AiRuntimeConfig resolveRuntime() {
        if (configurationService != null) {
            return configurationService.resolve();
        }
        return new AiRuntimeConfig(
                properties.apiKey(),
                properties.model(),
                StringUtils.hasText(properties.apiKey()),
                "TEST"
        );
    }

    private int maxTokens() {
        return properties.maxTokens();
    }

    private String extractAnswer(ChatCompletionResponse response) {
        if (response == null
                || response.choices() == null
                || response.choices().isEmpty()
                || response.choices().get(0).message() == null
                || !StringUtils.hasText(response.choices().get(0).message().content())) {
            throw new BusinessException(ErrorCode.AI_SERVICE_FAILED, FRIENDLY_ERROR);
        }
        return response.choices().get(0).message().content().trim();
    }

    private void safeRecordSuccess(String model, Usage usage, long latencyMs) {
        if (usageService == null) {
            return;
        }
        try {
            usageService.recordSuccess(
                    model,
                    usage == null
                            ? AiUsageService.Usage.empty()
                            : new AiUsageService.Usage(
                                    usage.promptTokens(),
                                    usage.completionTokens(),
                                    usage.totalTokens(),
                                    usage.cacheHitTokens(),
                                    usage.cacheMissTokens()
                            ),
                    latencyMs
            );
        } catch (RuntimeException exception) {
            log.warn("Failed to persist AI usage counters");
        }
    }

    private void safeRecordFailure(String model, long latencyMs, String errorType) {
        if (usageService == null) {
            return;
        }
        try {
            usageService.recordFailure(model, latencyMs, errorType);
        } catch (RuntimeException exception) {
            log.warn("Failed to persist AI failure counter");
        }
    }

    private long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    record ChatCompletionRequest(
            String model,
            List<Message> messages,
            boolean stream,
            @JsonProperty("max_tokens") Integer maxTokens
    ) {
    }

    record Message(String role, String content) {
    }

    record ChatCompletionResponse(List<Choice> choices, Usage usage) {
    }

    record Choice(Message message) {
    }

    record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens,
            @JsonProperty("prompt_cache_hit_tokens") Integer cacheHitTokens,
            @JsonProperty("prompt_cache_miss_tokens") Integer cacheMissTokens
    ) {
    }
}
