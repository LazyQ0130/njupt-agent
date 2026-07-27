package com.njupt.aiassistant.service;

import com.njupt.aiassistant.vo.OperationLogVO;

import java.util.List;

public interface AdminAuditService {
    void record(String operation, String target);
    List<OperationLogVO> latest();
}
