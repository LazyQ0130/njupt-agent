package com.njupt.aiassistant.service;

import com.njupt.aiassistant.dto.AdminLoginRequest;
import com.njupt.aiassistant.vo.AdminTokenVO;

public interface AdminAuthService {
    AdminTokenVO login(AdminLoginRequest request);
}
