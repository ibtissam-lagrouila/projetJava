package com.fitwell.dao;

import com.fitwell.core.db.ConnectionPool;
import com.fitwell.model.entity.LabMember;
import com.fitwell.model.enums.LabRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class LabMemberDao {

    private final ConnectionPool pool;

    public LabMemberDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public void addMember(UUID userId, UUID labId, LabRole role) throws SQLException {
        String sql = "INSERT INTO lab_members (id, user_id, lab_id, role, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, userId);
            ps.setObject(3, labId);
            ps.setString(4, role.name());
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public Optional<LabMember> findByUserId(UUID userId) throws SQLException {
        String sql = "SELECT id, user_id, lab_id, role, created_at FROM lab_members WHERE user_id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new LabMember(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("user_id"),
                            (UUID) rs.getObject("lab_id"),
                            LabRole.fromString(rs.getString("role")),
                            rs.getTimestamp("created_at").toInstant()
                    ));
                }
                return Optional.empty();
            }
        } finally {
            pool.release(conn);
        }
    }
}

