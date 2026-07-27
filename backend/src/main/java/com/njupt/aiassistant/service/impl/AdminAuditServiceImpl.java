package com.njupt.aiassistant.service.impl;

import com.njupt.aiassistant.entity.OperationLogEntity;
import com.njupt.aiassistant.mapper.OperationLogMapper;
import com.njupt.aiassistant.service.AdminAuditService;
import com.njupt.aiassistant.vo.OperationLogVO;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminAuditServiceImpl implements AdminAuditService {

    private final OperationLogMapper operationLogMapper;

    public AdminAuditServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    @Transactional
    public void record(String operation, String target) {
        var authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return;
        }
        var entity = new OperationLogEntity();
        entity.setAdminId(token.getToken().getClaim("admin_id"));
        entity.setOperation(truncate(operation, 64));
        entity.setTarget(truncate(target, 1000));
        operationLogMapper.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OperationLogVO> latest() {
        return operationLogMapper.findTop100ByOrderByCreatedTimeDesc()
                .stream()
                .map(entity -> new OperationLogVO(
                        entity.getId(),
                        entity.getAdminId(),
                        entity.getOperation(),
                        entity.getTarget(),
                        entity.getCreatedTime()
                ))
                .toList();
    }

    private String truncate(String value, int maxLength) {
        var normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }
}
