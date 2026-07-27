package com.njupt.aiassistant.vo;

import java.time.LocalDateTime;

public record OperationLogVO(
        Long id,
        Long adminId,
        String operation,
        String target,
        LocalDateTime createdTime
) {
}
