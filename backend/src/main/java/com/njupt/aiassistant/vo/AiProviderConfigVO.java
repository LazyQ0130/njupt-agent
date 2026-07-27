package com.njupt.aiassistant.vo;

import java.time.LocalDateTime;

public record AiProviderConfigVO(
        boolean configured,
        boolean enabled,
        String source,
        String keyMask,
        String model,
        LocalDateTime verifiedAt,
        boolean storageAvailable
) {
}
