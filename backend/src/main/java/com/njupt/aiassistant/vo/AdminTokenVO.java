package com.njupt.aiassistant.vo;

public record AdminTokenVO(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String adminName
) {
}
