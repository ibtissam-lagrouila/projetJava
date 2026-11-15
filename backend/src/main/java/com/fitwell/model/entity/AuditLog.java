package com.fitwell.model.entity;

import java.time.Instant;
import java.util.UUID;

public record AuditLog(
        UUID id,
        UUID userId,
        String action,
        String resourceType,
        String resourceId,
        String detailsJson,
        String ipAddress,
        Instant createdAt
) {
    public static AuditLog of(UUID userId, String action, String resourceType, String resourceId, String detailsJson, String ipAddress) {
        return new AuditLog(UUID.randomUUID(), userId, action, resourceType, resourceId, detailsJson, ipAddress, Instant.now());
    }
}

