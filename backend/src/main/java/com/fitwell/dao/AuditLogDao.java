package com.fitwell.dao;

import com.fitwell.core.db.ConnectionPool;
import com.fitwell.model.entity.AuditLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

public final class AuditLogDao {

    private final ConnectionPool pool;

    public AuditLogDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public void insert(AuditLog log) throws SQLException {
        String sql = """
            INSERT INTO audit_logs (id, user_id, action, resource_type, resource_id, details_json, ip_address, created_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, CURRENT_TIMESTAMP)
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, log.id() != null ? log.id() : UUID.randomUUID());
            if (log.userId() != null) {
                ps.setObject(2, log.userId());
            } else {
                ps.setNull(2, Types.OTHER);
            }
            ps.setString(3, log.action());
            ps.setString(4, log.resourceType());
            ps.setString(5, log.resourceId());
            ps.setString(6, log.detailsJson() != null ? log.detailsJson() : "{}");
            ps.setString(7, log.ipAddress());
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }
}

