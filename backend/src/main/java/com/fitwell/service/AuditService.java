package com.fitwell.service;

import com.fitwell.dao.AuditLogDao;
import com.fitwell.model.entity.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.UUID;

public final class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogDao auditLogDao;

    public AuditService(AuditLogDao auditLogDao) {
        this.auditLogDao = auditLogDao;
    }

    public void logAction(UUID userId, String action, String resourceType, String resourceId, String safeDetailsJson, String ipAddress) {
        try {
            AuditLog auditLog = AuditLog.of(userId, action, resourceType, resourceId, safeDetailsJson, ipAddress);
            auditLogDao.insert(auditLog);
        } catch (SQLException e) {
            log.warn("Failed to write audit log for action {}: {}", action, e.getMessage());
        }
    }

    public void logAction(UUID userId, String action, String resourceType, String resourceId, String ipAddress) {
        logAction(userId, action, resourceType, resourceId, "{}", ipAddress);
    }
}

