package com.njupt.aiassistant.vo;

public record AiOverviewVO(
        AiProviderConfigVO config,
        AiBalanceVO balance,
        AiUsageSummaryVO usage
) {
}
