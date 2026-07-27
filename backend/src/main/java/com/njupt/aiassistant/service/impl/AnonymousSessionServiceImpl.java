package com.njupt.aiassistant.service.impl;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.config.AnonymousSessionProperties;
import com.njupt.aiassistant.exception.BusinessException;
import com.njupt.aiassistant.service.AnonymousSessionService;
import com.njupt.aiassistant.vo.AnonymousSessionVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AnonymousSessionServiceImpl implements AnonymousSessionService {

    private final AnonymousSessionProperties properties;

    public AnonymousSessionServiceImpl(
            AnonymousSessionProperties properties
    ) {
        this.properties = properties;
    }

    @Override
    public AnonymousSessionVO create() {
        return new AnonymousSessionVO(
                UUID.randomUUID().toString(),
                LocalDateTime.now().plus(properties.retention())
        );
    }

    @Override
    public String requireValid(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(ErrorCode.ANONYMOUS_SESSION_REQUIRED);
        }
        try {
            return UUID.fromString(sessionId.trim()).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.ANONYMOUS_SESSION_REQUIRED
            );
        }
    }
}
