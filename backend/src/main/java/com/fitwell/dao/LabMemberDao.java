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
        String sql = "SELECT id, user_id, lab_id, role, created_at FROM lab_members WHERE user_id = ? ORDER BY created_at ASC";
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

    public Optional<LabMember> findByUserAndLab(UUID userId, UUID labId) throws SQLException {
        if (userId == null || labId == null) return Optional.empty();
        String sql = "SELECT id, user_id, lab_id, role, created_at FROM lab_members WHERE user_id = ? AND lab_id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setObject(2, labId);
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

    public record LabMembershipDetails(
            UUID labId,
            String labName,
            String city,
            String universityEntity,
            LabRole role,
            String status
    ) {}

    public java.util.List<LabMembershipDetails> findAllByUserId(UUID userId) throws SQLException {
        if (userId == null) return java.util.List.of();
        String sql = """
            SELECT lm.lab_id, l.name as lab_name, l.city, l.university_entity, lm.role,
                   COALESCE(l.status, CASE WHEN l.verified THEN 'APPROVED' ELSE 'PENDING' END) as status
            FROM lab_members lm
            JOIN labs l ON lm.lab_id = l.id
            WHERE lm.user_id = ?
            ORDER BY l.name ASC
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<LabMembershipDetails> list = new java.util.ArrayList<>();
                while (rs.next()) {
                    list.add(new LabMembershipDetails(
                            (UUID) rs.getObject("lab_id"),
                            rs.getString("lab_name"),
                            rs.getString("city"),
                            rs.getString("university_entity"),
                            LabRole.fromString(rs.getString("role")),
                            rs.getString("status")
                    ));
                }
                return list;
            }
        } finally {
            pool.release(conn);
        }
    }
}

