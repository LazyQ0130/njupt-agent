package com.njupt.aiassistant.service.ai;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.config.AiConfigSecurityProperties;
import com.njupt.aiassistant.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyCipherServiceTests {
    private static final String MASTER_KEY =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void encryptsWithRandomIvAndDecryptsWithoutPlaintextLeakage() {
        var service = new ApiKeyCipherService(
                new AiConfigSecurityProperties(MASTER_KEY)
        );
        var plaintext = "sk-sensitive-test-key-1234";

        var first = service.encrypt(plaintext);
        var second = service.encrypt(plaintext);

        assertThat(first).startsWith("v1:").doesNotContain(plaintext);
        assertThat(second).startsWith("v1:").doesNotContain(plaintext);
        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo(plaintext);
        assertThat(service.decrypt(second)).isEqualTo(plaintext);
    }

    @Test
    void refusesPersistenceWhenMasterKeyIsMissingOrInvalid() {
        var missing = new ApiKeyCipherService(new AiConfigSecurityProperties(""));
        var invalid = new ApiKeyCipherService(
                new AiConfigSecurityProperties("not-a-32-byte-base64-key")
        );

        assertThat(missing.isAvailable()).isFalse();
        assertThat(invalid.isAvailable()).isFalse();
        assertThatThrownBy(() -> missing.encrypt("sk-test"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_CONFIG_STORAGE_UNAVAILABLE)
                );
    }
}
