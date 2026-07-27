package com.njupt.aiassistant.service;

import com.njupt.aiassistant.vo.AnonymousSessionVO;

public interface AnonymousSessionService {
    AnonymousSessionVO create();
    String requireValid(String sessionId);
}
