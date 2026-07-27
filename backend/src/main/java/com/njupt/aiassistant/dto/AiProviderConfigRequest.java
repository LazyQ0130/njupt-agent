package com.njupt.aiassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiProviderConfigRequest(
        @NotBlank(message = "API Key 不能为空")
        @Size(max = 512, message = "API Key 长度无效")
        String apiKey,

        @NotBlank(message = "模型不能为空")
        String model
) {
}
