package com.fitwell.dao;

import com.fitwell.core.db.ConnectionPool;
import com.fitwell.model.entity.Session;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class SessionDao {

    private final ConnectionPool pool;

    public SessionDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public Session createSession(UUID userId, String tokenHash, Instant expiresAt) throws SQLException {
        String sql = """
            INSERT INTO sessions (id, user_id, token_hash, expires_at, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            RETURNING id, user_id, token_hash, expires_at, created_at
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            UUID id = UUID.randomUUID();
            ps.setObject(1, id);
            ps.setObject(2, userId);
            ps.setString(3, tokenHash);
            ps.setTimestamp(4, Timestamp.from(expiresAt));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Session(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("user_id"),
                            rs.getString("token_hash"),
                            rs.getTimestamp("expires_at").toInstant(),
                            rs.getTimestamp("created_at").toInstant()
                    );
                }
                throw new SQLException("Failed to create session");
            }
        } finally {
            pool.release(conn);
        }
    }

    public Optional<Session> findByTokenHash(String tokenHash) throws SQLException {
        String sql = "SELECT id, user_id, token_hash, expires_at, created_at FROM sessions WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Session(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("user_id"),
                            rs.getString("token_hash"),
                            rs.getTimestamp("expires_at").toInstant(),
                            rs.getTimestamp("created_at").toInstant()
                    ));
                }
                return Optional.empty();
            }
        } finally {
            pool.release(conn);
        }
    }

    public void deleteByTokenHash(String tokenHash) throws SQLException {
        String sql = "DELETE FROM sessions WHERE token_hash = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public void deleteAllForUser(UUID userId) throws SQLException {
        String sql = "DELETE FROM sessions WHERE user_id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }
}

