package com.college.placement.audit;

import com.college.placement.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public void log(String action, String entityType, Long entityId, String oldValue, String newValue) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .performedBy(securityUtils.getCurrentUser())
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Audit logging should not fail the main operation
        }
    }

    @Transactional
    public void log(String action, String entityType, Long entityId, String newValue) {
        log(action, entityType, entityId, null, newValue);
    }
}
