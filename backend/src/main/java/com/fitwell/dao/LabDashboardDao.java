package com.fitwell.dao;

import com.fitwell.core.db.ConnectionPool;
import com.fitwell.dao.OfferDao.OfferRecord;
import com.fitwell.dao.StudentProfileDao.StudentFullProfile;
import com.fitwell.model.dto.OfferDtos.SkillItem;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public final class LabDashboardDao {

    private final ConnectionPool pool;

    public LabDashboardDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public int countOpenOffers(UUID labId, LocalDate today) throws SQLException {
        if (labId == null) return 0;
        String sql = """
            SELECT COUNT(*) FROM offers
            WHERE lab_id = ?
              AND status = 'PUBLISHED'
              AND (
                  (closes_at IS NOT NULL AND closes_at >= ?)
                  OR (closes_at IS NULL AND created_at >= (?::timestamp - INTERVAL '60 days'))
              )
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, labId);
            ps.setObject(2, today);
            ps.setObject(3, today);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } finally {
            pool.release(conn);
        }
    }

    public int countNewApplicants(UUID labId, Instant sevenDaysAgo) throws SQLException {
        if (labId == null) return 0;
        String sql = """
            SELECT COUNT(*) FROM applications a
            JOIN offers o ON a.offer_id = o.id
            WHERE o.lab_id = ?
              AND a.sent_at >= ?
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, labId);
            ps.setTimestamp(2, Timestamp.from(sevenDaysAgo));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } finally {
            pool.release(conn);
        }
    }

    public int countToReview(UUID labId) throws SQLException {
        if (labId == null) return 0;
        String sql = """
            SELECT COUNT(*) FROM applications a
            JOIN offers o ON a.offer_id = o.id
            WHERE o.lab_id = ?
              AND a.status = 'SENT'
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, labId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } finally {
            pool.release(conn);
        }
    }

    public int countInterviewsBetween(UUID labId, Instant start, Instant end, String status) throws SQLException {
        if (labId == null) return 0;
        String sql = """
            SELECT COUNT(*) FROM interviews i
            JOIN applications a ON i.application_id = a.id
            JOIN offers o ON a.offer_id = o.id
            WHERE o.lab_id = ?
              AND i.status = ?
              AND i.scheduled_time >= ?
              AND i.scheduled_time < ?
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, labId);
            ps.setString(2, status);
            ps.setTimestamp(3, Timestamp.from(start));
            ps.setTimestamp(4, Timestamp.from(end));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } finally {
            pool.release(conn);
        }
    }

    public int countAwaitingConfirmationInterviews(UUID labId) throws SQLException {
        if (labId == null) return 0;
        String sql = """
            SELECT COUNT(*) FROM interviews i
            JOIN applications a ON i.application_id = a.id
            JOIN offers o ON a.offer_id = o.id
            WHERE o.lab_id = ?
              AND i.status = 'PROPOSED'
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, labId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } finally {
            pool.release(conn);
        }
    }

    public record MonthStats(int views, int applied, int shortlisted, int interviews) {}

    public MonthStats getMonthActivity(UUID labId, Instant monthStart, Instant monthEnd) throws SQLException {
        if (labId == null) return new MonthStats(0, 0, 0, 0);
        Connection conn = pool.acquire();
        try {
            int views = 0;
            String viewsSql = """
                SELECT COUNT(*) FROM offer_views v
                JOIN offers o ON v.offer_id = o.id
                WHERE o.lab_id = ?
                  AND v.viewed_at >= ? AND v.viewed_at < ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(viewsSql)) {
                ps.setObject(1, labId);
                ps.setTimestamp(2, Timestamp.from(monthStart));
                ps.setTimestamp(3, Timestamp.from(monthEnd));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) views = rs.getInt(1);
                }
            }

            int applied = 0;
            String appliedSql = """
                SELECT COUNT(*) FROM applications a
                JOIN offers o ON a.offer_id = o.id
                WHERE o.lab_id = ?
                  AND a.sent_at >= ? AND a.sent_at < ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(appliedSql)) {
                ps.setObject(1, labId);
                ps.setTimestamp(2, Timestamp.from(monthStart));
                ps.setTimestamp(3, Timestamp.from(monthEnd));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) applied = rs.getInt(1);
                }
            }

            int shortlisted = 0;
            String shortSql = """
                SELECT COUNT(*) FROM application_events ae
                JOIN applications a ON ae.application_id = a.id
                JOIN offers o ON a.offer_id = o.id
                WHERE o.lab_id = ?
                  AND ae.to_status = 'SHORTLISTED'
                  AND ae.created_at >= ? AND ae.created_at < ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(shortSql)) {
                ps.setObject(1, labId);
                ps.setTimestamp(2, Timestamp.from(monthStart));
                ps.setTimestamp(3, Timestamp.from(monthEnd));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) shortlisted = rs.getInt(1);
                }
            }

            int interviews = 0;
            String intSql = """
                SELECT COUNT(*) FROM interviews i
                JOIN applications a ON i.application_id = a.id
                JOIN offers o ON a.offer_id = o.id
                WHERE o.lab_id = ?
                  AND i.scheduled_time >= ? AND i.scheduled_time < ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(intSql)) {
                ps.setObject(1, labId);
                ps.setTimestamp(2, Timestamp.from(monthStart));
                ps.setTimestamp(3, Timestamp.from(monthEnd));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) interviews = rs.getInt(1);
                }
            }

            return new MonthStats(views, applied, shortlisted, interviews);
        } finally {
            pool.release(conn);
        }
    }

    public record RawApplicationCandidate(
            UUID applicationId,
            UUID studentId,
            String studentName,
            String level,
            String university,
            String fieldOfStudy,
            Instant appliedAt,
            String status,
            String profileSnapshotJson,
            UUID offerId,
            String offerTitle,
            String offerDomain,
            LocalDate offerStartDate,
            int offerDurationMonths,
            String offerLocation,
            List<SkillItem> offerSkills
    ) {}

    public List<RawApplicationCandidate> getUndecidedApplications(UUID labId, LocalDate today) throws SQLException {
        if (labId == null) return List.of();
        String sql = """
            SELECT a.id as application_id, a.student_id, COALESCE(sp.full_name, u.email) as student_name,
                   COALESCE(sp.level, 'Master') as student_level,
                   COALESCE(array_to_string(sp.interests, ', '), 'Science') as field_of_study,
                   COALESCE(sp.university, 'Université') as university, a.sent_at, a.status, a.profile_snapshot_json,
                   o.id as offer_id, o.title as offer_title, o.domain as offer_domain,
                   o.start_date as offer_start_date, o.duration_months as offer_duration_months,
                   o.location as offer_city
            FROM applications a
            JOIN offers o ON a.offer_id = o.id
            JOIN student_profiles sp ON a.student_id = sp.id
            JOIN users u ON sp.user_id = u.id
            WHERE o.lab_id = ?
              AND a.status IN ('SENT', 'REVIEWED')
              AND o.status = 'PUBLISHED'
              AND (
                  (o.closes_at IS NOT NULL AND o.closes_at >= ?)
                  OR (o.closes_at IS NULL AND o.created_at >= (?::timestamp - INTERVAL '60 days'))
              )
            ORDER BY a.sent_at DESC
        """;

        List<RawApplicationCandidate> list = new ArrayList<>();
        Connection conn = pool.acquire();
        try {
            Map<UUID, List<SkillItem>> skillsMap = fetchOfferSkillsForLab(conn, labId);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, labId);
                ps.setObject(2, today);
                ps.setObject(3, today);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID offerId = (UUID) rs.getObject("offer_id");
                        List<SkillItem> skills = skillsMap.getOrDefault(offerId, List.of());
                        Timestamp sentTs = rs.getTimestamp("sent_at");
                        Instant sentAt = sentTs != null ? sentTs.toInstant() : Instant.now();
                        java.sql.Date stDate = rs.getDate("offer_start_date");
                        LocalDate offerStartDate = stDate != null ? stDate.toLocalDate() : null;

                        list.add(new RawApplicationCandidate(
                                (UUID) rs.getObject("application_id"),
                                (UUID) rs.getObject("student_id"),
                                rs.getString("student_name"),
                                rs.getString("student_level"),
                                rs.getString("university"),
                                rs.getString("field_of_study"),
                                sentAt,
                                rs.getString("status"),
                                rs.getString("profile_snapshot_json"),
                                offerId,
                                rs.getString("offer_title"),
                                rs.getString("offer_domain"),
                                offerStartDate,
                                rs.getInt("offer_duration_months"),
                                rs.getString("offer_city"),
                                skills
                        ));
                    }
                }
            }
        } finally {
            pool.release(conn);
        }
        return list;
    }

    private Map<UUID, List<SkillItem>> fetchOfferSkillsForLab(Connection conn, UUID labId) throws SQLException {
        Map<UUID, List<SkillItem>> map = new HashMap<>();
        String sql = """
            SELECT os.offer_id, os.skill_id, s.name, s.normalized_name, os.is_required
            FROM offer_skills os
            JOIN skills s ON os.skill_id = s.id
            JOIN offers o ON os.offer_id = o.id
            WHERE o.lab_id = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, labId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID offerId = (UUID) rs.getObject("offer_id");
                    SkillItem item = new SkillItem(
                            (UUID) rs.getObject("skill_id"),
                            rs.getString("name"),
                            rs.getString("normalized_name"),
                            rs.getBoolean("is_required")
                    );
                    map.computeIfAbsent(offerId, k -> new ArrayList<>()).add(item);
                }
            }
        }
        return map;
    }

    public record LabOfferStats(
            UUID offerId,
            String title,
            LocalDate startDate,
            String compensation,
            int skillCount,
            Instant createdAt,
            int totalViews
    ) {}

    public List<LabOfferStats> getLabOfferStats(UUID labId) throws SQLException {
        if (labId == null) return List.of();
        String sql = """
            SELECT o.id, o.title, o.start_date, o.compensation_amount, o.created_at,
                   (SELECT COUNT(*) FROM offer_skills os WHERE os.offer_id = o.id) as skill_count,
                   (SELECT COUNT(*) FROM offer_views ov WHERE ov.offer_id = o.id) as total_views
            FROM offers o
            WHERE o.lab_id = ? AND o.status = 'PUBLISHED'
            ORDER BY o.created_at ASC
        """;
        List<LabOfferStats> list = new ArrayList<>();
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, labId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Date sd = rs.getDate("start_date");
                    Timestamp ct = rs.getTimestamp("created_at");
                    list.add(new LabOfferStats(
                            (UUID) rs.getObject("id"),
                            rs.getString("title"),
                            sd != null ? sd.toLocalDate() : null,
                            rs.getString("compensation_amount"),
                            rs.getInt("skill_count"),
                            ct != null ? ct.toInstant() : Instant.now(),
                            rs.getInt("total_views")
                    ));
                }
            }
        } finally {
            pool.release(conn);
        }
        return list;
    }

    public int countWaitingApplicantsOver5Days(UUID labId, Instant fiveDaysAgo) throws SQLException {
        if (labId == null) return 0;
        String sql = """
            SELECT COUNT(*) FROM applications a
            JOIN offers o ON a.offer_id = o.id
            WHERE o.lab_id = ?
              AND a.status = 'SENT'
              AND a.sent_at < ?
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, labId);
            ps.setTimestamp(2, Timestamp.from(fiveDaysAgo));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } finally {
            pool.release(conn);
        }
    }

    public int shortlistApplications(UUID labId, List<UUID> applicationIds, UUID actorUserId) throws SQLException {
        if (labId == null || applicationIds == null || applicationIds.isEmpty()) return 0;
        String updateSql = """
            UPDATE applications a
            SET status = 'SHORTLISTED', last_activity_at = CURRENT_TIMESTAMP
            FROM offers o
            WHERE a.offer_id = o.id
              AND o.lab_id = ?
              AND a.id = ?
              AND a.status IN ('SENT', 'REVIEWED')
        """;

        String eventSql = """
            INSERT INTO application_events (id, application_id, from_status, to_status, note, actor_user_id, created_at)
            VALUES (?, ?, 'SENT', 'SHORTLISTED', 'Candidate shortlisted from Lab Dashboard', ?, CURRENT_TIMESTAMP)
        """;

        int count = 0;
        Connection conn = pool.acquire();
        try {
            boolean originalAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement psUpdate = conn.prepareStatement(updateSql);
                     PreparedStatement psEvent = conn.prepareStatement(eventSql)) {
                    for (UUID appId : applicationIds) {
                        psUpdate.setObject(1, labId);
                        psUpdate.setObject(2, appId);
                        int affected = psUpdate.executeUpdate();
                        if (affected > 0) {
                            count++;
                            psEvent.setObject(1, UUID.randomUUID());
                            psEvent.setObject(2, appId);
                            psEvent.setObject(3, actorUserId);
                            psEvent.executeUpdate();
                        }
                    }
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
        return count;
    }
}
