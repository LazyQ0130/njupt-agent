package com.njupt.aiassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat.conversation")
public record ConversationProperties(
        Integer maxHistoryMessages,
        Integer maxHistoryCharacters
) {
    public ConversationProperties {
        maxHistoryMessages = maxHistoryMessages == null
                ? 12
                : Math.max(2, Math.min(30, maxHistoryMessages));
        maxHistoryCharacters = maxHistoryCharacters == null
                ? 6000
                : Math.max(1000, Math.min(20000, maxHistoryCharacters));
    }
}
