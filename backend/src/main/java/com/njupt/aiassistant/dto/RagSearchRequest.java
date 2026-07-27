package com.njupt.aiassistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RagSearchRequest(
        @NotBlank(message = "问题不能为空")
        @Size(max = 2000, message = "问题长度不能超过 2000 个字符")
        String question,

        @JsonProperty("top_k")
        @Min(value = 1, message = "top_k 不能小于 1")
        @Max(value = 20, message = "top_k 不能大于 20")
        Integer topK,

        @Size(max = 32, message = "category 长度不能超过 32")
        String category
) {
    public RagSearchRequest(String question, Integer topK) {
        this(question, topK, null);
    }
}
