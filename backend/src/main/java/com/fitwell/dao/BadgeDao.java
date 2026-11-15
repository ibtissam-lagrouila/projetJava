package com.fitwell.dao;

import com.fitwell.core.db.ConnectionPool;
import com.fitwell.model.enums.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BadgeDao {

    private final ConnectionPool pool;

    public BadgeDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public Map<String, Integer> getBadgesForStudent(UUID userId) throws SQLException {
        Map<String, Integer> badges = new HashMap<>();
        Connection conn = pool.acquire();
        try {
            // Count active applications
            String appSql = """
                SELECT COUNT(*) FROM applications a
                JOIN student_profiles sp ON a.student_id = sp.id
                WHERE sp.user_id = ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(appSql)) {
                ps.setObject(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) badges.put("applications", rs.getInt(1));
                }
            }

            // Count unread messages
            String msgSql = """
                SELECT COUNT(*) FROM messages m
                JOIN thread_participants tp ON m.thread_id = tp.thread_id
                WHERE tp.user_id = ? AND m.sender_id != ? AND m.read_at IS NULL
            """;
            try (PreparedStatement ps = conn.prepareStatement(msgSql)) {
                ps.setObject(1, userId);
                ps.setObject(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) badges.put("messages", rs.getInt(1));
                }
            }
        } finally {
            pool.release(conn);
        }
        return badges;
    }

    public Map<String, Integer> getBadgesForLab(UUID labId) throws SQLException {
        Map<String, Integer> badges = new HashMap<>();
        if (labId == null) {
            badges.put("candidates", 0);
            badges.put("interviews", 0);
            return badges;
        }

        Connection conn = pool.acquire();
        try {
            // Count candidates to review
            String candSql = """
                SELECT COUNT(*) FROM applications a
                JOIN offers o ON a.offer_id = o.id
                WHERE o.lab_id = ? AND a.status IN ('SENT', 'REVIEWED')
            """;
            try (PreparedStatement ps = conn.prepareStatement(candSql)) {
                ps.setObject(1, labId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) badges.put("candidates", rs.getInt(1));
                }
            }

            // Count interviews this week
            String intSql = """
                SELECT COUNT(*) FROM interviews i
                JOIN applications a ON i.application_id = a.id
                JOIN offers o ON a.offer_id = o.id
                WHERE o.lab_id = ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(intSql)) {
                ps.setObject(1, labId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) badges.put("interviews", rs.getInt(1));
                }
            }
        } finally {
            pool.release(conn);
        }
        return badges;
    }

    public Map<String, Integer> getBadgesForAdmin() throws SQLException {
        Map<String, Integer> badges = new HashMap<>();
        Connection conn = pool.acquire();
        try {
            // Count pending validations
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM validation_items WHERE status = 'PENDING'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) badges.put("validation", rs.getInt(1));
                }
            }

            // Count pending claim requests
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM claim_requests WHERE status = 'PENDING'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) badges.put("claims", rs.getInt(1));
                }
            }
        } finally {
            pool.release(conn);
        }
        return badges;
    }
}

