package com.njupt.aiassistant.dto;

import jakarta.validation.constraints.NotNull;

public record AiProviderStatusRequest(
        @NotNull(message = "启用状态不能为空")
        Boolean enabled
) {
}
