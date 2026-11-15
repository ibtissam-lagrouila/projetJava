package com.fitwell.dao;

import com.fitwell.core.db.ConnectionPool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class AllowedUniversityDao {

    private final ConnectionPool pool;

    public AllowedUniversityDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public boolean isDomainAllowed(String domain) throws SQLException {
        if (domain == null) return false;
        String sql = "SELECT 1 FROM allowed_university_domains WHERE LOWER(domain) = LOWER(?)";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, domain.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } finally {
            pool.release(conn);
        }
    }

    public void addDomain(String domain, String universityName) throws SQLException {
        String sql = "INSERT INTO allowed_university_domains (id, domain, university_name) VALUES (?, LOWER(?), ?) ON CONFLICT (domain) DO NOTHING";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, domain.trim());
            ps.setString(3, universityName);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }
}

