package com.njupt.aiassistant.vo;

import java.time.LocalDateTime;

public record AnonymousSessionVO(
        String anonymousSessionId,
        LocalDateTime expiresAt
) {
}
