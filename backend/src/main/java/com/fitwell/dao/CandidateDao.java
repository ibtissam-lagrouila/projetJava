package com.fitwell.dao;

import com.fitwell.core.db.ConnectionPool;
import com.fitwell.model.enums.ApplicationStatus;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class CandidateDao {

    private final ConnectionPool pool;

    public CandidateDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public record CandidateApplicationRecord(
            UUID applicationId,
            UUID studentId,
            UUID offerId,
            String offerTitle,
            String studentName,
            String level,
            String fieldOfStudy,
            String university,
            List<String> skills,
            List<String> offerSkills,
            String status,
            Instant sentAt,
            Instant lastActivityAt,
            String coverLetter
    ) {}

    public record OfferSummary(
            UUID offerId,
            String title,
            int candidateCount
    ) {}

    public List<OfferSummary> findLabOffersWithCounts(UUID labId) throws SQLException {
        if (labId == null) return List.of();
        String sql = """
            SELECT o.id, o.title, COUNT(a.id) as candidate_count
            FROM offers o
            LEFT JOIN applications a ON o.id = a.offer_id
            WHERE o.lab_id = ? AND o.status = 'PUBLISHED'
            GROUP BY o.id, o.title, o.created_at
            ORDER BY o.created_at DESC
        """;
        List<OfferSummary> list = new ArrayList<>();
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, labId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new OfferSummary(
                            (UUID) rs.getObject("id"),
                            rs.getString("title"),
                            rs.getInt("candidate_count")
                    ));
                }
            }
        } finally {
            pool.release(conn);
        }
        return list;
    }

    public List<CandidateApplicationRecord> findCandidates(UUID labId, UUID offerId) throws SQLException {
        if (labId == null) return List.of();

        String sql = """
            SELECT a.id as application_id, a.student_id, a.offer_id, a.status, a.sent_at, a.last_activity_at, a.cover_letter,
                   COALESCE(sp.full_name, u.email) as student_name,
                   COALESCE(sp.level, 'Master') as student_level,
                   COALESCE(array_to_string(sp.interests, ', '), 'Science') as student_interests,
                   COALESCE(sp.university, 'Université') as student_university,
                   o.title as offer_title,
                   ARRAY(SELECT s.name FROM student_skills ss JOIN skills s ON ss.skill_id = s.id WHERE ss.student_id = sp.id) as student_skills,
                   ARRAY(SELECT s.name FROM offer_skills os JOIN skills s ON os.skill_id = s.id WHERE os.offer_id = o.id) as offer_skills
            FROM applications a
            JOIN offers o ON a.offer_id = o.id
            JOIN student_profiles sp ON a.student_id = sp.id
            JOIN users u ON sp.user_id = u.id
            WHERE o.lab_id = ?
              AND (?::uuid IS NULL OR o.id = ?::uuid)
            ORDER BY a.sent_at DESC
        """;

        List<CandidateApplicationRecord> list = new ArrayList<>();
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, labId);
            ps.setObject(2, offerId);
            ps.setObject(3, offerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Array sArray = rs.getArray("student_skills");
                    List<String> studentSkills = new ArrayList<>();
                    if (sArray != null) {
                        String[] arr = (String[]) sArray.getArray();
                        if (arr != null) studentSkills.addAll(Arrays.asList(arr));
                    }

                    Array oArray = rs.getArray("offer_skills");
                    List<String> offerSkills = new ArrayList<>();
                    if (oArray != null) {
                        String[] arr = (String[]) oArray.getArray();
                        if (arr != null) offerSkills.addAll(Arrays.asList(arr));
                    }

                    Timestamp sentTs = rs.getTimestamp("sent_at");
                    Instant sentAt = sentTs != null ? sentTs.toInstant() : Instant.now();
                    Timestamp actTs = rs.getTimestamp("last_activity_at");
                    Instant lastActivityAt = actTs != null ? actTs.toInstant() : sentAt;

                    list.add(new CandidateApplicationRecord(
                            (UUID) rs.getObject("application_id"),
                            (UUID) rs.getObject("student_id"),
                            (UUID) rs.getObject("offer_id"),
                            rs.getString("offer_title"),
                            rs.getString("student_name"),
                            rs.getString("student_level"),
                            rs.getString("student_interests"),
                            rs.getString("student_university"),
                            studentSkills,
                            offerSkills,
                            rs.getString("status"),
                            sentAt,
                            lastActivityAt,
                            rs.getString("cover_letter")
                    ));
                }
            }
        } finally {
            pool.release(conn);
        }
        return list;
    }

    public boolean updateApplicationStatus(UUID labId, UUID applicationId, ApplicationStatus newStatus, UUID actorUserId) throws SQLException {
        if (labId == null || applicationId == null || newStatus == null) return false;

        String checkSql = """
            SELECT a.status, a.offer_id
            FROM applications a
            JOIN offers o ON a.offer_id = o.id
            WHERE a.id = ? AND o.lab_id = ?
        """;

        String updateSql = """
            UPDATE applications
            SET status = ?, last_activity_at = CURRENT_TIMESTAMP
            WHERE id = ?
        """;

        String eventSql = """
            INSERT INTO application_events (id, application_id, from_status, to_status, note, actor_user_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;

        Connection conn = pool.acquire();
        try {
            boolean originalAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String oldStatusStr = null;
                try (PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
                    psCheck.setObject(1, applicationId);
                    psCheck.setObject(2, labId);
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (!rs.next()) {
                            return false; // Not found or not owned by lab (404)
                        }
                        oldStatusStr = rs.getString("status");
                    }
                }

                ApplicationStatus oldStatus = ApplicationStatus.fromString(oldStatusStr);
                if (oldStatus != null && !oldStatus.canTransitionByLab(newStatus) && oldStatus != newStatus) {
                    // Log transition anyway if admin/lab overrides, but allow same status idempotency
                }

                try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                    psUpdate.setString(1, newStatus.name());
                    psUpdate.setObject(2, applicationId);
                    psUpdate.executeUpdate();
                }

                try (PreparedStatement psEvent = conn.prepareStatement(eventSql)) {
                    psEvent.setObject(1, UUID.randomUUID());
                    psEvent.setObject(2, applicationId);
                    psEvent.setString(3, oldStatusStr != null ? oldStatusStr : "UNKNOWN");
                    psEvent.setString(4, newStatus.name());
                    psEvent.setString(5, "Status changed to " + newStatus.name() + " via Kanban Board");
                    psEvent.setObject(6, actorUserId);
                    psEvent.executeUpdate();
                }

                conn.commit();
                return true;
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
}
