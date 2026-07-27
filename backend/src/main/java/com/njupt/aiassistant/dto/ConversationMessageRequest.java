package com.njupt.aiassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ConversationMessageRequest(
        @NotNull @Positive Long conversationId,
        @NotBlank @Size(max = 2000) String question
) {
}
