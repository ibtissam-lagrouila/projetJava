package com.fitwell.dao;

import com.fitwell.core.db.ConnectionPool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class LabDao {

    private final ConnectionPool pool;

    public LabDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public LabInfo createLab(String name, String universityEntity, String city, String domain, String website, boolean verified) throws SQLException {
        String sql = """
            INSERT INTO labs (id, name, university_entity, city, domain, website, verified, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            RETURNING id, name, university_entity, city, domain, website, verified
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            UUID id = UUID.randomUUID();
            ps.setObject(1, id);
            ps.setString(2, name);
            ps.setString(3, universityEntity);
            ps.setString(4, city);
            ps.setString(5, domain);
            ps.setString(6, website);
            ps.setBoolean(7, verified);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new LabInfo(
                            (UUID) rs.getObject("id"),
                            rs.getString("name"),
                            rs.getString("university_entity"),
                            rs.getString("city"),
                            rs.getString("domain"),
                            rs.getString("website"),
                            rs.getBoolean("verified")
                    );
                }
                throw new SQLException("Failed to create lab");
            }
        } finally {
            pool.release(conn);
        }
    }

    public Optional<LabInfo> findById(UUID id) throws SQLException {
        if (id == null) return Optional.empty();
        String sql = "SELECT id, name, university_entity, city, domain, website, verified FROM labs WHERE id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new LabInfo(
                            (UUID) rs.getObject("id"),
                            rs.getString("name"),
                            rs.getString("university_entity"),
                            rs.getString("city"),
                            rs.getString("domain"),
                            rs.getString("website"),
                            rs.getBoolean("verified")
                    ));
                }
                return Optional.empty();
            }
        } finally {
            pool.release(conn);
        }
    }

    public boolean isLabApproved(UUID labId) throws SQLException {
        if (labId == null) return false;
        String sql = "SELECT verified FROM labs WHERE id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, labId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("verified");
                }
                return false;
            }
        } finally {
            pool.release(conn);
        }
    }

    public void approveLab(UUID labId) throws SQLException {
        String labSql = "UPDATE labs SET verified = TRUE, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        String claimSql = "UPDATE claim_requests SET status = 'APPROVED' WHERE lab_id = ?";
        Connection conn = pool.acquire();
        try {
            boolean originalAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps1 = conn.prepareStatement(labSql)) {
                    ps1.setObject(1, labId);
                    ps1.executeUpdate();
                }
                try (PreparedStatement ps2 = conn.prepareStatement(claimSql)) {
                    ps2.setObject(1, labId);
                    ps2.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAuto);
            }
        } finally {
            pool.release(conn);
        }
    }

    public void createClaimRequest(UUID labId, UUID requesterUserId, String requesterEmail, boolean domainMatches) throws SQLException {
        String sql = """
            INSERT INTO claim_requests (id, lab_id, requester_user_id, requester_email, domain_matches, status, created_at)
            VALUES (?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, labId);
            ps.setObject(3, requesterUserId);
            ps.setString(4, requesterEmail);
            ps.setBoolean(5, domainMatches);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public record LabInfo(UUID id, String name, String universityEntity, String city, String domain, String website, boolean verified) {}
}
