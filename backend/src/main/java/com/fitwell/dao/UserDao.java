package com.fitwell.dao;

import com.fitwell.core.db.ConnectionPool;
import com.fitwell.model.entity.User;
import com.fitwell.model.enums.Role;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class UserDao {

    private final ConnectionPool pool;

    public UserDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public User createUser(String email, String passwordHash, String salt, Role role, boolean emailVerified) throws SQLException {
        String sql = """
            INSERT INTO users (id, email, password_hash, salt, role, email_verified_at, is_active, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            RETURNING id, email, password_hash, salt, role, email_verified_at, is_active, created_at, updated_at
        """;

        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            UUID id = UUID.randomUUID();
            ps.setObject(1, id);
            ps.setString(2, email.toLowerCase().trim());
            ps.setString(3, passwordHash);
            ps.setString(4, salt);
            ps.setString(5, role.name());
            if (emailVerified) {
                ps.setTimestamp(6, Timestamp.from(Instant.now()));
            } else {
                ps.setNull(6, Types.TIMESTAMP);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
                throw new SQLException("Failed to insert user");
            }
        } finally {
            pool.release(conn);
        }
    }

    public Optional<User> findByEmail(String email) throws SQLException {
        if (email == null) return Optional.empty();
        String sql = "SELECT id, email, password_hash, salt, role, email_verified_at, is_active, created_at, updated_at FROM users WHERE LOWER(email) = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.toLowerCase().trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapUser(rs));
                }
                return Optional.empty();
            }
        } finally {
            pool.release(conn);
        }
    }

    public Optional<User> findById(UUID id) throws SQLException {
        if (id == null) return Optional.empty();
        String sql = "SELECT id, email, password_hash, salt, role, email_verified_at, is_active, created_at, updated_at FROM users WHERE id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapUser(rs));
                }
                return Optional.empty();
            }
        } finally {
            pool.release(conn);
        }
    }

    public boolean updatePassword(UUID userId, String newPasswordHash, String newSalt) throws SQLException {
        String sql = "UPDATE users SET password_hash = ?, salt = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPasswordHash);
            ps.setString(2, newSalt);
            ps.setObject(3, userId);
            return ps.executeUpdate() > 0;
        } finally {
            pool.release(conn);
        }
    }

    public boolean verifyEmail(UUID userId) throws SQLException {
        String sql = "UPDATE users SET email_verified_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            return ps.executeUpdate() > 0;
        } finally {
            pool.release(conn);
        }
    }

    public void createEmailVerificationToken(UUID userId, String tokenHash, Instant expiresAt) throws SQLException {
        String sql = "INSERT INTO email_verifications (id, user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, userId);
            ps.setString(3, tokenHash);
            ps.setTimestamp(4, Timestamp.from(expiresAt));
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public Optional<UUID> consumeVerificationToken(String tokenHash) throws SQLException {
        String query = """
            SELECT user_id FROM email_verifications
            WHERE token_hash = ? AND verified_at IS NULL AND expires_at > CURRENT_TIMESTAMP
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID userId = (UUID) rs.getObject("user_id");
                    // Mark verified
                    String update = "UPDATE email_verifications SET verified_at = CURRENT_TIMESTAMP WHERE token_hash = ?";
                    try (PreparedStatement ups = conn.prepareStatement(update)) {
                        ups.setString(1, tokenHash);
                        ups.executeUpdate();
                    }
                    verifyEmail(userId);
                    return Optional.of(userId);
                }
                return Optional.empty();
            }
        } finally {
            pool.release(conn);
        }
    }

    public void recordLoginAttempt(String email, String ipAddress, boolean success) throws SQLException {
        String sql = "INSERT INTO login_attempts (id, email, ip_address, attempted_at, success) VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, email != null ? email.toLowerCase().trim() : "unknown");
            ps.setString(3, ipAddress);
            ps.setBoolean(4, success);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public int countRecentFailedAttempts(String email, String ipAddress, int minutes) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM login_attempts
            WHERE (email = ? OR ip_address = ?)
              AND success = FALSE
              AND attempted_at >= (CURRENT_TIMESTAMP - (? || ' minutes')::INTERVAL)
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email != null ? email.toLowerCase().trim() : "");
            ps.setString(2, ipAddress != null ? ipAddress : "");
            ps.setString(3, String.valueOf(minutes));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } finally {
            pool.release(conn);
        }
    }

    public int countRecentRegistrations(String ipAddress, String email, int minutes) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM audit_logs
            WHERE action IN ('STUDENT_REGISTERED', 'LAB_ACCOUNT_REQUESTED', 'USER_REGISTERED')
              AND (ip_address = ? OR details_json->>'email' = ?)
              AND created_at >= (CURRENT_TIMESTAMP - (? || ' minutes')::INTERVAL)
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ipAddress != null ? ipAddress : "");
            ps.setString(2, email != null ? email.toLowerCase().trim() : "");
            ps.setString(3, String.valueOf(minutes));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } finally {
            pool.release(conn);
        }
    }

    public int countRecentVerificationResends(String ipAddress, String email, int minutes) throws SQLException {
        String sql = """
            SELECT COUNT(*) FROM audit_logs
            WHERE action = 'VERIFICATION_RESENT'
              AND (ip_address = ? OR details_json->>'email' = ?)
              AND created_at >= (CURRENT_TIMESTAMP - (? || ' minutes')::INTERVAL)
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ipAddress != null ? ipAddress : "");
            ps.setString(2, email != null ? email.toLowerCase().trim() : "");
            ps.setString(3, String.valueOf(minutes));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } finally {
            pool.release(conn);
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        Timestamp verifiedTs = rs.getTimestamp("email_verified_at");
        return new User(
                (UUID) rs.getObject("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("salt"),
                Role.fromString(rs.getString("role")),
                verifiedTs != null ? verifiedTs.toInstant() : null,
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
