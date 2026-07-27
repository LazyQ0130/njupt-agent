package com.njupt.aiassistant.vo;

import java.time.LocalDateTime;
import java.util.List;

public record AiBalanceVO(
        boolean available,
        String syncStatus,
        LocalDateTime syncedAt,
        String message,
        List<BalanceItem> balances
) {
    public record BalanceItem(
            String currency,
            String totalBalance,
            String grantedBalance,
            String toppedUpBalance
    ) {
    }
}
