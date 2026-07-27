package com.njupt.aiassistant.service.ai;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.config.AiConfigSecurityProperties;
import com.njupt.aiassistant.config.DeepSeekProperties;
import com.njupt.aiassistant.entity.AiProviderConfigEntity;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.mapper.AiProviderConfigMapper;
import com.njupt.aiassistant.vo.AiBalanceVO;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiProviderConfigurationServiceTests {
    private static final String MASTER_KEY =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void validationFailureDoesNotReplaceExistingConfiguration() {
        var mapper = mock(AiProviderConfigMapper.class);
        var account = mock(DeepSeekAccountClient.class);
        when(account.fetchBalance("sk-invalid")).thenThrow(
                new BusinessException(ErrorCode.AI_CREDENTIALS_INVALID)
        );
        var service = service(mapper, account, "sk-environment");

        assertThatThrownBy(() ->
                service.validateAndSave("sk-invalid", "deepseek-v4-flash")
        ).isInstanceOf(BusinessException.class);
        verify(mapper, never()).save(any());
    }

    @Test
    void zeroBalanceCanBeEncryptedSavedAndMasked() {
        var mapper = mock(AiProviderConfigMapper.class);
        var account = mock(DeepSeekAccountClient.class);
        var balance = new AiBalanceVO(
                false,
                "SUCCESS",
                LocalDateTime.now(),
                "余额不足",
                List.of(new AiBalanceVO.BalanceItem("CNY", "0.00", "0.00", "0.00"))
        );
        when(account.fetchBalance("sk-valid-9876")).thenReturn(balance);
        when(mapper.findByProvider("DEEPSEEK")).thenReturn(Optional.empty());
        when(mapper.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var cipher = new ApiKeyCipherService(new AiConfigSecurityProperties(MASTER_KEY));
        var service = new AiProviderConfigurationService(
                mapper,
                cipher,
                environment("sk-environment"),
                account
        );

        var saved = service.validateAndSave("sk-valid-9876", "deepseek-v4-flash");

        assertThat(saved.balance().available()).isFalse();
        assertThat(saved.config().keyMask()).isEqualTo("••••••••9876");
        assertThat(saved.config().enabled()).isTrue();
        verify(mapper).save(any(AiProviderConfigEntity.class));
    }

    @Test
    void databaseDisableOverridesEnvironmentFallback() {
        var mapper = mock(AiProviderConfigMapper.class);
        var account = mock(DeepSeekAccountClient.class);
        var stored = new AiProviderConfigEntity();
        stored.setProvider("DEEPSEEK");
        stored.setModel("deepseek-v4-flash");
        stored.setEnabled(false);
        stored.setEncryptedApiKey(null);
        when(mapper.findByProvider("DEEPSEEK")).thenReturn(Optional.of(stored));
        var service = service(mapper, account, "sk-environment");

        var runtime = service.resolve();

        assertThat(runtime.enabled()).isFalse();
        assertThat(runtime.apiKey()).isEmpty();
        assertThat(runtime.source()).isEqualTo("DATABASE");
    }

    private AiProviderConfigurationService service(
            AiProviderConfigMapper mapper,
            DeepSeekAccountClient account,
            String environmentKey
    ) {
        return new AiProviderConfigurationService(
                mapper,
                new ApiKeyCipherService(new AiConfigSecurityProperties(MASTER_KEY)),
                environment(environmentKey),
                account
        );
    }

    private DeepSeekProperties environment(String key) {
        return new DeepSeekProperties(
                key,
                URI.create("https://api.deepseek.com"),
                "deepseek-chat",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                800
        );
    }
}
