package com.fitwell.dao;

import com.fitwell.core.db.ConnectionPool;
import com.fitwell.service.sourcing.model.SourceRecord;

import java.net.URI;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SourceDao {

    private final ConnectionPool pool;

    public SourceDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public List<SourceRecord> findAll() throws SQLException {
        String sql = """
            SELECT id, name, domain, access_type, url, cgu_url, tos_url, type, status, accuracy_pct, is_allowed,
                   robots_checked_at, robots_status, crawl_delay_seconds, css_selectors, search_url_template,
                   notes, auto_publish, failure_reason, alert_message, last_run_at, created_at
            FROM sources
            ORDER BY 
                CASE access_type
                    WHEN 'INTERNAL' THEN 1
                    WHEN 'API' THEN 2
                    WHEN 'CAREER_PAGE' THEN 3
                    WHEN 'DEEPLINK' THEN 4
                    WHEN 'REMOTE_FEED' THEN 5
                    ELSE 6
                END,
                created_at DESC
        """;

        List<SourceRecord> list = new ArrayList<>();
        Connection conn = pool.acquire();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } finally {
            pool.release(conn);
        }
        return list;
    }

    public List<SourceRecord> findActiveDeeplinks() throws SQLException {
        String sql = """
            SELECT id, name, domain, access_type, url, cgu_url, tos_url, type, status, accuracy_pct, is_allowed,
                   robots_checked_at, robots_status, crawl_delay_seconds, css_selectors, search_url_template,
                   notes, auto_publish, failure_reason, alert_message, last_run_at, created_at
            FROM sources
            WHERE access_type = 'DEEPLINK' AND is_allowed = TRUE
            ORDER BY name ASC
        """;

        List<SourceRecord> list = new ArrayList<>();
        Connection conn = pool.acquire();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } finally {
            pool.release(conn);
        }
        return list;
    }

    public int countLiveExternalSources() throws SQLException {
        String sql = """
            SELECT COUNT(*) 
            FROM sources 
            WHERE access_type IN ('API', 'CAREER_PAGE', 'REMOTE_FEED') 
              AND is_allowed = TRUE
        """;
        Connection conn = pool.acquire();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } finally {
            pool.release(conn);
        }
        return 0;
    }

    public Optional<SourceRecord> findById(UUID id) throws SQLException {
        String sql = """
            SELECT id, name, domain, access_type, url, cgu_url, tos_url, type, status, accuracy_pct, is_allowed,
                   robots_checked_at, robots_status, crawl_delay_seconds, css_selectors, search_url_template,
                   notes, auto_publish, failure_reason, alert_message, last_run_at, created_at
            FROM sources
            WHERE id = ?
        """;

        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } finally {
            pool.release(conn);
        }
        return Optional.empty();
    }

    public SourceRecord create(String name, String url, String cguUrl, String type, String cssSelectors, boolean autoPublish) throws SQLException {
        return create(name, null, "CAREER_PAGE", url, cguUrl, null, null, type, cssSelectors, autoPublish);
    }

    public SourceRecord create(String name, String domain, String accessType, String url, String cguUrl,
                               String searchUrlTemplate, String notes, String type, String cssSelectors,
                               boolean autoPublish) throws SQLException {
        UUID id = UUID.randomUUID();
        if (domain == null || domain.isBlank()) {
            try {
                URI uri = URI.create(url);
                domain = uri.getHost();
            } catch (Exception ignored) {
                domain = "";
            }
        }
        String resolvedAccessType = (accessType != null && !accessType.isBlank()) ? accessType : "CAREER_PAGE";

        String sql = """
            INSERT INTO sources (id, name, domain, access_type, url, cgu_url, tos_url, search_url_template,
                                 notes, type, status, accuracy_pct, is_allowed, robots_status, crawl_delay_seconds,
                                 css_selectors, auto_publish, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 100, FALSE, 'PENDING_CHECK', 5, ?::jsonb, ?, CURRENT_TIMESTAMP)
            RETURNING id, name, domain, access_type, url, cgu_url, tos_url, type, status, accuracy_pct, is_allowed,
                      robots_checked_at, robots_status, crawl_delay_seconds, css_selectors, search_url_template,
                      notes, auto_publish, failure_reason, alert_message, last_run_at, created_at
        """;

        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, name);
            ps.setString(3, domain);
            ps.setString(4, resolvedAccessType);
            ps.setString(5, url);
            ps.setString(6, cguUrl);
            ps.setString(7, cguUrl);
            ps.setString(8, searchUrlTemplate);
            ps.setString(9, notes);
            ps.setString(10, type != null ? type : "WEBSITE");
            ps.setString(11, cssSelectors != null && !cssSelectors.isBlank() ? cssSelectors : null);
            ps.setBoolean(12, autoPublish);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } finally {
            pool.release(conn);
        }
        throw new SQLException("Failed to create source");
    }

    public void updateRobotsCheck(UUID id, String robotsStatus, int crawlDelaySeconds, String message) throws SQLException {
        String sql = """
            UPDATE sources
            SET robots_checked_at = CURRENT_TIMESTAMP,
                robots_status = ?,
                crawl_delay_seconds = ?,
                alert_message = ?
            WHERE id = ?
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, robotsStatus);
            ps.setInt(2, crawlDelaySeconds);
            ps.setString(3, message);
            ps.setObject(4, id);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public void updateDetails(UUID id, String tosUrl, String searchUrlTemplate, String notes) throws SQLException {
        String sql = """
            UPDATE sources
            SET tos_url = ?,
                cgu_url = ?,
                search_url_template = ?,
                notes = ?
            WHERE id = ?
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tosUrl);
            ps.setString(2, tosUrl);
            ps.setString(3, searchUrlTemplate);
            ps.setString(4, notes);
            ps.setObject(5, id);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public void setAllowed(UUID id, boolean isAllowed) throws SQLException {
        String sql = """
            UPDATE sources
            SET is_allowed = ?,
                status = CASE WHEN ? = TRUE THEN 'ACTIVE' ELSE 'PENDING' END,
                alert_message = CASE WHEN ? = TRUE THEN NULL ELSE alert_message END
            WHERE id = ?
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, isAllowed);
            ps.setBoolean(2, isAllowed);
            ps.setBoolean(3, isAllowed);
            ps.setObject(4, id);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public void pauseSource(UUID id, String alertMessage) throws SQLException {
        String sql = """
            UPDATE sources
            SET status = 'PAUSED',
                is_allowed = FALSE,
                alert_message = ?
            WHERE id = ?
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, alertMessage);
            ps.setObject(2, id);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public void updateStatus(UUID id, String status, String alertMessage, String failureReason) throws SQLException {
        String sql = """
            UPDATE sources
            SET status = ?,
                alert_message = ?,
                failure_reason = ?
            WHERE id = ?
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, alertMessage);
            ps.setString(3, failureReason);
            ps.setObject(4, id);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public void recordRun(UUID id, Instant runAt, int itemsFound) throws SQLException {
        String sql = """
            UPDATE sources
            SET last_run_at = ?,
                status = 'ACTIVE'
            WHERE id = ?
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(runAt));
            ps.setObject(2, id);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    private SourceRecord mapRow(ResultSet rs) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        String name = rs.getString("name");
        String domain = getNullableString(rs, "domain");
        String accessType = getNullableString(rs, "access_type");
        if (accessType == null || accessType.isBlank()) {
            accessType = "CAREER_PAGE";
        }
        String url = rs.getString("url");
        String cguUrl = getNullableString(rs, "cgu_url");
        String tosUrl = getNullableString(rs, "tos_url");
        if (tosUrl == null) {
            tosUrl = cguUrl;
        }
        if (cguUrl == null) {
            cguUrl = tosUrl;
        }
        String type = rs.getString("type");
        String status = rs.getString("status");
        int accuracyPct = rs.getInt("accuracy_pct");
        boolean isAllowed = rs.getBoolean("is_allowed");
        Timestamp robotsCheckedAt = rs.getTimestamp("robots_checked_at");
        String robotsStatus = rs.getString("robots_status");
        int crawlDelaySeconds = rs.getInt("crawl_delay_seconds");
        String cssSelectors = rs.getString("css_selectors");
        String searchUrlTemplate = getNullableString(rs, "search_url_template");
        String notes = getNullableString(rs, "notes");
        boolean autoPublish = rs.getBoolean("auto_publish");
        String failureReason = rs.getString("failure_reason");
        String alertMessage = rs.getString("alert_message");
        Timestamp lastRunAt = rs.getTimestamp("last_run_at");
        Timestamp createdAt = rs.getTimestamp("created_at");

        return new SourceRecord(
                id, name, domain, accessType, url, cguUrl, tosUrl, type, status, accuracyPct, isAllowed,
                robotsCheckedAt != null ? robotsCheckedAt.toInstant() : null,
                robotsStatus, crawlDelaySeconds, cssSelectors, searchUrlTemplate, notes, autoPublish,
                failureReason, alertMessage,
                lastRunAt != null ? lastRunAt.toInstant() : null,
                createdAt != null ? createdAt.toInstant() : null
        );
    }

    private String getNullableString(ResultSet rs, String colName) {
        try {
            return rs.getString(colName);
        } catch (SQLException ignored) {
            return null;
        }
    }
}
