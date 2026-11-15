package com.fitwell.dao;

import com.fitwell.core.db.ConnectionPool;
import com.fitwell.model.dto.OfferDtos.SkillItem;

import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class OfferDao {

    private final ConnectionPool pool;

    public OfferDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public record OfferRecord(
            UUID id,
            UUID labId,
            String labName,
            String labUniversity,
            String labCity,
            String title,
            String domain,
            int durationMonths,
            LocalDate startDate,
            String location,
            String compensationType,
            String compensationAmount,
            String description,
            List<String> tasks,
            int places,
            String status,
            String origin,
            LocalDate closesAt,
            long daysRemaining,
            List<SkillItem> skills
    ) {}

    public List<OfferRecord> findPublishedOffers() throws SQLException {
        String sql = """
            SELECT o.id, o.lab_id, l.name AS lab_name, l.university_entity AS lab_university, l.city AS lab_city,
                   o.title, o.domain, o.duration_months, o.start_date, o.location,
                   o.compensation_type, o.compensation_amount, o.description, o.tasks,
                   o.places, o.status, o.origin, o.closes_at
            FROM offers o
            LEFT JOIN labs l ON o.lab_id = l.id
            WHERE o.status = 'PUBLISHED'
            ORDER BY o.created_at DESC
        """;

        List<OfferRecord> results = new ArrayList<>();
        Connection conn = pool.acquire();
        try {
            Map<UUID, List<SkillItem>> skillsByOffer = loadAllOfferSkills(conn);

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToOffer(rs, skillsByOffer));
                }
            }
        } finally {
            pool.release(conn);
        }
        return results;
    }

    public Optional<OfferRecord> findOfferById(UUID offerId) throws SQLException {
        String sql = """
            SELECT o.id, o.lab_id, l.name AS lab_name, l.university_entity AS lab_university, l.city AS lab_city,
                   o.title, o.domain, o.duration_months, o.start_date, o.location,
                   o.compensation_type, o.compensation_amount, o.description, o.tasks,
                   o.places, o.status, o.origin, o.closes_at
            FROM offers o
            LEFT JOIN labs l ON o.lab_id = l.id
            WHERE o.id = ?
        """;

        Connection conn = pool.acquire();
        try {
            Map<UUID, List<SkillItem>> skillsByOffer = loadSkillsForOffer(conn, offerId);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, offerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToOffer(rs, skillsByOffer));
                    }
                }
            }
        } finally {
            pool.release(conn);
        }
        return Optional.empty();
    }

    public void recordOfferView(UUID offerId, UUID studentProfileId, String ipHash) throws SQLException {
        String sql = """
            INSERT INTO offer_views (id, offer_id, student_id, viewed_at, ip_hash)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, offerId);
            ps.setObject(3, studentProfileId);
            ps.setString(4, ipHash);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public Map<String, Integer> getApplicationCountsForStudent(UUID studentProfileId) throws SQLException {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("total", 0);
        counts.put("inReview", 0);
        counts.put("interview", 0);

        if (studentProfileId == null) {
            return counts;
        }

        String sql = """
            SELECT status, COUNT(*) AS cnt
            FROM applications
            WHERE student_id = ?
            GROUP BY status
        """;

        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, studentProfileId);
            try (ResultSet rs = ps.executeQuery()) {
                int total = 0;
                int inReview = 0;
                int interview = 0;
                while (rs.next()) {
                    String status = rs.getString("status");
                    int cnt = rs.getInt("cnt");
                    total += cnt;
                    if ("REVIEWED".equalsIgnoreCase(status) || "SHORTLISTED".equalsIgnoreCase(status) || "SENT".equalsIgnoreCase(status)) {
                        inReview += cnt;
                    } else if ("INTERVIEW".equalsIgnoreCase(status)) {
                        interview += cnt;
                    }
                }
                counts.put("total", total);
                counts.put("inReview", inReview);
                counts.put("interview", interview);
            }
        } finally {
            pool.release(conn);
        }
        return counts;
    }

    private OfferRecord mapResultSetToOffer(ResultSet rs, Map<UUID, List<SkillItem>> skillsByOffer) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        UUID labId = (UUID) rs.getObject("lab_id");
        String labName = rs.getString("lab_name");
        String labUniversity = rs.getString("lab_university");
        String labCity = rs.getString("lab_city");
        String title = rs.getString("title");
        String domain = rs.getString("domain");
        int durationMonths = rs.getInt("duration_months");

        java.sql.Date startDateSql = rs.getDate("start_date");
        LocalDate startDate = startDateSql != null ? startDateSql.toLocalDate() : null;

        String location = rs.getString("location");
        String compensationType = rs.getString("compensation_type");
        String compensationAmount = rs.getString("compensation_amount");
        String description = rs.getString("description");

        Array tasksArray = rs.getArray("tasks");
        List<String> tasks = tasksArray != null ? Arrays.asList((String[]) tasksArray.getArray()) : List.of();

        int places = rs.getInt("places");
        String status = rs.getString("status");
        String origin = rs.getString("origin");

        java.sql.Date closesAtSql = rs.getDate("closes_at");
        LocalDate closesAt = closesAtSql != null ? closesAtSql.toLocalDate() : null;

        long daysRemaining = 0;
        if (closesAt != null) {
            daysRemaining = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), closesAt));
        }

        List<SkillItem> skills = skillsByOffer.getOrDefault(id, List.of());

        return new OfferRecord(
                id, labId, labName, labUniversity, labCity,
                title, domain, durationMonths, startDate, location,
                compensationType, compensationAmount, description, tasks,
                places, status, origin, closesAt, daysRemaining, skills
        );
    }

    private Map<UUID, List<SkillItem>> loadAllOfferSkills(Connection conn) throws SQLException {
        String sql = """
            SELECT os.offer_id, s.id AS skill_id, s.name, s.normalized_name, os.is_required
            FROM offer_skills os
            JOIN skills s ON os.skill_id = s.id
        """;
        Map<UUID, List<SkillItem>> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID offerId = (UUID) rs.getObject("offer_id");
                UUID skillId = (UUID) rs.getObject("skill_id");
                String name = rs.getString("name");
                String normalized = rs.getString("normalized_name");
                boolean isReq = rs.getBoolean("is_required");

                map.computeIfAbsent(offerId, k -> new ArrayList<>())
                        .add(new SkillItem(skillId, name, normalized, isReq));
            }
        }
        return map;
    }

    private Map<UUID, List<SkillItem>> loadSkillsForOffer(Connection conn, UUID offerId) throws SQLException {
        String sql = """
            SELECT os.offer_id, s.id AS skill_id, s.name, s.normalized_name, os.is_required
            FROM offer_skills os
            JOIN skills s ON os.skill_id = s.id
            WHERE os.offer_id = ?
        """;
        Map<UUID, List<SkillItem>> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, offerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = (UUID) rs.getObject("offer_id");
                    UUID skillId = (UUID) rs.getObject("skill_id");
                    String name = rs.getString("name");
                    String normalized = rs.getString("normalized_name");
                    boolean isReq = rs.getBoolean("is_required");

                    map.computeIfAbsent(id, k -> new ArrayList<>())
                            .add(new SkillItem(skillId, name, normalized, isReq));
                }
            }
        }
        return map;
    }
}

