package com.fitwell.dao;

import com.fitwell.core.db.ConnectionPool;
import com.fitwell.model.dto.OfferDtos.CreateLabOfferRequest;
import com.fitwell.model.dto.OfferDtos.SkillItem;
import com.fitwell.model.dto.OfferDtos.WeeklyActionItem;

import java.sql.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class OfferDao {

    private final ConnectionPool pool;
    private Clock clock = Clock.systemUTC();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public OfferDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public Clock getClock() {
        return this.clock;
    }

    public boolean isDevEnvironment() {
        String env = System.getenv().getOrDefault("APP_ENV", "dev");
        return "dev".equalsIgnoreCase(env) || "development".equalsIgnoreCase(env)
                || Boolean.parseBoolean(System.getenv().getOrDefault("ENABLE_DEV_SEED", "true"));
    }

    public boolean hasActiveExternalSources() {
        String sql = "SELECT COUNT(*) FROM sources WHERE is_allowed = TRUE AND status = 'ACTIVE'";
        try {
            Connection conn = pool.acquire();
            try (Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            } finally {
                pool.release(conn);
            }
        } catch (SQLException e) {
            return false;
        }
        return false;
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
            String sourceName,
            String sourceUrl,
            LocalDate closesAt,
            long daysRemaining,
            List<SkillItem> skills,
            String displayDate,
            Instant createdAt) {
        public OfferRecord(
                UUID id, UUID labId, String labName, String labUniversity, String labCity,
                String title, String domain, int durationMonths, LocalDate startDate, String location,
                String compensationType, String compensationAmount, String description, List<String> tasks,
                int places, String status, String origin, String sourceName, String sourceUrl,
                LocalDate closesAt, long daysRemaining, List<SkillItem> skills) {
            this(id, labId, labName, labUniversity, labCity, title, domain, durationMonths, startDate,
                    location, compensationType, compensationAmount, description, tasks, places, status,
                    origin, sourceName, sourceUrl, closesAt, daysRemaining, skills, null, null);
        }
    }

    public List<OfferRecord> findPublishedOffers() throws SQLException {
        return findPublishedOffers(isDevEnvironment());
    }

    public List<OfferRecord> findPublishedOffers(boolean includeDemo) throws SQLException {
        LocalDate today = LocalDate.now(clock);
        LocalDate sixtyDaysAgo = today.minusDays(60);
        LocalDate fortyFiveDaysAgo = today.minusDays(45);
        Instant fifteenMinutesAgo = Instant.now(clock).minus(Duration.ofMinutes(15));

        String originFilter = includeDemo
                ? "('LAB_POSTED', 'INTERNAL', 'DEMO')"
                : "('LAB_POSTED', 'INTERNAL')";

        String sql = String.format("""
            SELECT o.id, o.lab_id, COALESCE(l.name, o.employer, 'Research Partner') AS lab_name,
                   l.university_entity AS lab_university, l.city AS lab_city,
                   o.title, o.domain, o.duration_months, o.start_date, o.location,
                   o.compensation_type, o.compensation_amount, o.description, o.tasks,
                   o.places, o.status, o.origin, s.name AS source_name, o.source_url, o.closes_at,
                   o.created_at, o.fetched_at, o.dead_since
            FROM offers o
            LEFT JOIN labs l ON o.lab_id = l.id
            LEFT JOIN sources s ON o.source_id = s.id
            WHERE (
                -- (a) Internal & Demo offers
                (
                    o.origin IN %s
                    AND o.status IN ('PUBLISHED', 'ACTIVE')
                    AND (
                        (o.closes_at IS NOT NULL AND o.closes_at >= ?)
                        OR (o.closes_at IS NULL AND o.created_at >= ?)
                    )
                )
                OR
                -- (b) External sourced offers
                (
                    o.origin = 'SOURCED'
                    AND o.status IN ('PUBLISHED', 'ACTIVE', 'MAY_BE_CLOSED')
                    AND (
                        (? = TRUE)
                        OR (o.fetched_at IS NOT NULL AND o.fetched_at >= ?)
                    )
                    AND (
                        (o.start_date IS NOT NULL AND o.start_date >= ?)
                        OR (o.created_at >= ?)
                        OR (o.dead_since IS NULL)
                    )
                )
            )
            ORDER BY
                CASE WHEN o.status = 'MAY_BE_CLOSED' THEN 1 ELSE 0 END ASC,
                o.created_at DESC
        """, originFilter);

        List<OfferRecord> results = new ArrayList<>();
        Connection conn = pool.acquire();
        try {
            Map<UUID, List<SkillItem>> skillsByOffer = loadAllOfferSkills(conn);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDate(1, java.sql.Date.valueOf(today));
                ps.setTimestamp(2, java.sql.Timestamp.valueOf(sixtyDaysAgo.atStartOfDay()));
                ps.setBoolean(3, includeDemo);
                ps.setTimestamp(4, java.sql.Timestamp.from(fifteenMinutesAgo));
                ps.setDate(5, java.sql.Date.valueOf(fortyFiveDaysAgo));
                ps.setTimestamp(6, java.sql.Timestamp.valueOf(fortyFiveDaysAgo.atStartOfDay()));

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapResultSetToOffer(rs, skillsByOffer));
                    }
                }
            }
        } finally {
            pool.release(conn);
        }
        return results;
    }

    public Optional<OfferRecord> findOfferById(UUID offerId) throws SQLException {
        String sql = """
                    SELECT o.id, o.lab_id, COALESCE(l.name, o.employer, 'Research Partner') AS lab_name,
                           l.university_entity AS lab_university, l.city AS lab_city,
                           o.title, o.domain, o.duration_months, o.start_date, o.location,
                           o.compensation_type, o.compensation_amount, o.description, o.tasks,
                           o.places, o.status, o.origin, s.name AS source_name, o.source_url, o.closes_at,
                           o.created_at, o.fetched_at, o.dead_since
                    FROM offers o
                    LEFT JOIN labs l ON o.lab_id = l.id
                    LEFT JOIN sources s ON o.source_id = s.id
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
        if (offerId == null) return;
        String sql = """
            INSERT INTO offer_views (id, offer_id, student_id, viewed_at, ip_hash)
            SELECT ?, ?, ?, CURRENT_TIMESTAMP, ?
            WHERE EXISTS (
                SELECT 1 FROM offers WHERE id = ? AND origin IN ('LAB_POSTED', 'INTERNAL', 'DEMO')
            )
            AND NOT EXISTS (
                SELECT 1 FROM offer_views
                WHERE offer_id = ?
                  AND (
                      (?::uuid IS NOT NULL AND student_id = ?)
                      OR (?::uuid IS NULL AND ip_hash IS NOT NULL AND ip_hash = ?)
                  )
                  AND (viewed_at AT TIME ZONE 'Africa/Casablanca')::date = (CURRENT_TIMESTAMP AT TIME ZONE 'Africa/Casablanca')::date
            )
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, offerId);
            ps.setObject(3, studentProfileId);
            ps.setString(4, ipHash);
            ps.setObject(5, offerId);
            ps.setObject(6, offerId);
            ps.setObject(7, studentProfileId);
            ps.setObject(8, studentProfileId);
            ps.setObject(9, studentProfileId);
            ps.setString(10, ipHash);
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
                    if ("REVIEWED".equalsIgnoreCase(status) || "SHORTLISTED".equalsIgnoreCase(status)
                            || "SENT".equalsIgnoreCase(status)) {
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

    public List<WeeklyActionItem> getWeeklyActionsForStudent(UUID studentProfileId) throws SQLException {
        if (studentProfileId == null) {
            return List.of();
        }

        String sql = """
                    SELECT a.id, a.status, a.next_step_calc, a.created_at,
                           o.title AS offer_title, o.closes_at,
                           l.name AS lab_name,
                           i.scheduled_time
                    FROM applications a
                    JOIN offers o ON a.offer_id = o.id
                    JOIN labs l ON o.lab_id = l.id
                    LEFT JOIN interviews i ON i.application_id = a.id
                    WHERE a.student_id = ?
                    ORDER BY a.last_activity_at DESC
                    LIMIT 5
                """;

        List<WeeklyActionItem> actions = new ArrayList<>();
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, studentProfileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    String offerTitle = rs.getString("offer_title");
                    String labName = rs.getString("lab_name");
                    java.sql.Timestamp scheduled = rs.getTimestamp("scheduled_time");
                    java.sql.Date closesAt = rs.getDate("closes_at");

                    if ("INTERVIEW".equalsIgnoreCase(status)) {
                        String deadline = scheduled != null
                                ? new java.text.SimpleDateFormat("EEE d", Locale.ENGLISH).format(scheduled)
                                : "soon";
                        actions.add(new WeeklyActionItem(
                                UUID.randomUUID().toString(),
                                "Confirm interview slot, " + labName,
                                deadline,
                                "INTERVIEW",
                                false,
                                "/student/applications"));
                    } else if ("DRAFT".equalsIgnoreCase(status)) {
                        String deadline = closesAt != null
                                ? new java.text.SimpleDateFormat("EEE d", Locale.ENGLISH).format(closesAt)
                                : "Mon 30";
                        String shortTitle = offerTitle != null && offerTitle.length() > 20
                                ? offerTitle.substring(0, 20) + "..."
                                : (offerTitle != null ? offerTitle : "offer");
                        actions.add(new WeeklyActionItem(
                                UUID.randomUUID().toString(),
                                "Finish cover letter, " + shortTitle,
                                deadline,
                                "DRAFT",
                                true,
                                "/student/applications"));
                    }
                }
            }
        } finally {
            pool.release(conn);
        }

        return actions;
    }

    private OfferRecord mapResultSetToOffer(ResultSet rs, Map<UUID, List<SkillItem>> skillsByOffer)
            throws SQLException {
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
        String sourceName = rs.getString("source_name");
        String sourceUrl = rs.getString("source_url");

        if ("DEMO".equalsIgnoreCase(origin)) {
            sourceName = "DEMO · fictional";
        } else if ("LAB_POSTED".equalsIgnoreCase(origin) || "INTERNAL".equalsIgnoreCase(origin)) {
            if (sourceName == null || sourceName.isBlank()) {
                sourceName = labName != null ? labName : "Internal Lab";
            }
        } else if ("SOURCED".equalsIgnoreCase(origin)) {
            if (sourceName == null || sourceName.isBlank()) {
                if (sourceUrl != null && !sourceUrl.isBlank()) {
                    try {
                        java.net.URI uri = java.net.URI.create(sourceUrl);
                        String host = uri.getHost();
                        sourceName = (host != null && !host.isBlank()) ? host.replaceFirst("^www\\.", "")
                                : "Company Career Site";
                    } catch (Exception e) {
                        sourceName = "Company Career Site";
                    }
                } else {
                    sourceName = "External Source";
                }
            }
        }

        java.sql.Date closesAtSql = rs.getDate("closes_at");
        LocalDate closesAt = closesAtSql != null ? closesAtSql.toLocalDate() : null;

        java.sql.Timestamp createdAtTs = rs.getTimestamp("created_at");
        Instant createdAt = createdAtTs != null ? createdAtTs.toInstant() : Instant.now(clock);

        String displayDate;
        if (closesAt != null) {
            displayDate = "Closes " + DATE_FORMATTER.format(closesAt);
        } else if (startDate != null) {
            displayDate = "Starts " + DATE_FORMATTER.format(startDate);
        } else {
            LocalDate createdDate = LocalDate.ofInstant(createdAt, clock.getZone());
            displayDate = "Posted " + DATE_FORMATTER.format(createdDate);
        }

        long daysRemaining = 0;
        if (closesAt != null) {
            daysRemaining = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(clock), closesAt));
        }

        List<SkillItem> skills = skillsByOffer.getOrDefault(id, List.of());

        return new OfferRecord(
                id, labId, labName, labUniversity, labCity,
                title, domain, durationMonths, startDate, location,
                compensationType, compensationAmount, description, tasks,
                places, status, origin, sourceName, sourceUrl, closesAt, daysRemaining, skills,
                displayDate, createdAt);
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

    public record StudentApplicationItem(
            UUID id,
            UUID offerId,
            String offerTitle,
            String labName,
            String location,
            int durationMonths,
            String status,
            String nextStepCalc,
            String sourceName,
            String sourceUrl,
            java.time.Instant createdAt,
            java.time.Instant sentAt) {
    }

    public StudentApplicationItem submitApplication(UUID studentProfileId, UUID offerId, String coverLetter)
            throws SQLException {
        return submitApplication(studentProfileId, offerId, coverLetter, false, true);
    }

    public StudentApplicationItem submitApplication(UUID studentProfileId, UUID offerId, String coverLetter,
            boolean isManualExternal, boolean confirmed) throws SQLException {
        return submitApplication(studentProfileId, offerId, coverLetter, isManualExternal, confirmed, null);
    }

    public StudentApplicationItem submitApplication(UUID studentProfileId, UUID offerId, String coverLetter,
            boolean isManualExternal, boolean confirmed, String profileSnapshotJson) throws SQLException {
        if (!isManualExternal && !confirmed) {
            throw new IllegalArgumentException("Explicit confirmation is required before applying with Fitwell.");
        }
        UUID id = UUID.randomUUID();
        String nextStep = isManualExternal
                ? "Self-reported external application (tracked manually)"
                : "Application submitted via Fitwell to lab recruiter";
        String letterText = isManualExternal
                ? (coverLetter != null && !coverLetter.isBlank() ? coverLetter
                        : "Applied directly on external website.")
                : (coverLetter != null && !coverLetter.isBlank() ? coverLetter
                        : "Fitwell application with verified CV and profile.");

        String sql = """
                    INSERT INTO applications (id, student_id, offer_id, status, cover_letter, next_step_calc, profile_snapshot_json, sent_at, last_activity_at)
                    VALUES (?, ?, ?, 'SENT', ?, ?, ?::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (student_id, offer_id) DO UPDATE SET
                        status = 'SENT',
                        cover_letter = EXCLUDED.cover_letter,
                        next_step_calc = EXCLUDED.next_step_calc,
                        profile_snapshot_json = COALESCE(EXCLUDED.profile_snapshot_json, applications.profile_snapshot_json),
                        last_activity_at = CURRENT_TIMESTAMP
                    RETURNING id
                """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, studentProfileId);
            ps.setObject(3, offerId);
            ps.setString(4, letterText);
            ps.setString(5, nextStep);
            ps.setString(6, profileSnapshotJson);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    id = (UUID) rs.getObject(1);
                }
            }
        } finally {
            pool.release(conn);
        }

        List<StudentApplicationItem> list = getStudentApplicationsList(studentProfileId);
        for (var item : list) {
            if (item.id().equals(id))
                return item;
        }
        return list.isEmpty() ? null : list.get(0);
    }

    public void runDailyExpirationJob(LocalDate today) throws SQLException {
        // 1. Close internal & demo offers whose closes_at < today
        String sqlInternal = """
                    UPDATE offers
                    SET status = 'CLOSED', updated_at = CURRENT_TIMESTAMP
                    WHERE origin IN ('LAB_POSTED', 'INTERNAL', 'DEMO')
                      AND status IN ('PUBLISHED', 'ACTIVE')
                      AND closes_at IS NOT NULL
                      AND closes_at < ?
                """;
        // 2. Mark "MAY_BE_CLOSED" external cached offers that are older than 45 days
        String sqlExternal = """
                    UPDATE offers
                    SET status = 'MAY_BE_CLOSED', dead_since = COALESCE(dead_since, ?), updated_at = CURRENT_TIMESTAMP
                    WHERE origin = 'SOURCED'
                      AND status IN ('PUBLISHED', 'ACTIVE')
                      AND (
                          (start_date IS NOT NULL AND start_date < ?)
                          OR (created_at < ?)
                      )
                """;
        Connection conn = pool.acquire();
        try {
            try (PreparedStatement ps = conn.prepareStatement(sqlInternal)) {
                ps.setDate(1, java.sql.Date.valueOf(today));
                ps.executeUpdate();
            }
            LocalDate fortyFiveDaysAgo = today.minusDays(45);
            try (PreparedStatement ps = conn.prepareStatement(sqlExternal)) {
                ps.setDate(1, java.sql.Date.valueOf(today));
                ps.setDate(2, java.sql.Date.valueOf(fortyFiveDaysAgo));
                ps.setTimestamp(3, java.sql.Timestamp.valueOf(fortyFiveDaysAgo.atStartOfDay()));
                ps.executeUpdate();
            }
        } finally {
            pool.release(conn);
        }
    }

    public List<StudentApplicationItem> getStudentApplicationsList(UUID studentProfileId) throws SQLException {
        if (studentProfileId == null)
            return List.of();
        String sql = """
                    SELECT a.id, a.offer_id, a.status, a.next_step_calc, a.created_at, a.sent_at,
                           o.title AS offer_title, o.location, o.duration_months,
                           COALESCE(l.name, 'Recruteur') AS lab_name,
                           s.name AS source_name, o.source_url
                    FROM applications a
                    JOIN offers o ON a.offer_id = o.id
                    LEFT JOIN labs l ON o.lab_id = l.id
                    LEFT JOIN sources s ON o.source_id = s.id
                    WHERE a.student_id = ?
                    ORDER BY a.last_activity_at DESC
                """;
        List<StudentApplicationItem> list = new ArrayList<>();
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, studentProfileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new StudentApplicationItem(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("offer_id"),
                            rs.getString("offer_title"),
                            rs.getString("lab_name"),
                            rs.getString("location"),
                            rs.getInt("duration_months"),
                            rs.getString("status"),
                            rs.getString("next_step_calc"),
                            rs.getString("source_name"),
                            rs.getString("source_url"),
                            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                            rs.getTimestamp("sent_at") != null ? rs.getTimestamp("sent_at").toInstant() : null));
                }
            }
        } finally {
            pool.release(conn);
        }
        return list;
    }

    public UUID insertSourcedOffer(UUID sourceId, String title, String employer, String location,
            int durationMonths, String excerpt, String canonicalUrl,
            String domain, String compensationType, String contentHash) throws SQLException {
        UUID id = UUID.randomUUID();
        String sql = """
                    INSERT INTO offers (id, source_id, title, employer, location, duration_months,
                                        description, excerpt, source_url, canonical_url, domain,
                                        compensation_type, content_hash, status, validation_status,
                                        origin, fetched_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 'APPROVED', 'SOURCED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (content_hash) DO UPDATE
                    SET fetched_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    RETURNING id
                """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, sourceId);
            ps.setString(3, title);
            ps.setString(4, employer);
            ps.setString(5, location);
            ps.setInt(6, durationMonths);
            ps.setString(7, excerpt);
            ps.setString(8, excerpt);
            ps.setString(9, canonicalUrl);
            ps.setString(10, canonicalUrl);
            ps.setString(11, domain != null ? domain : "General");
            ps.setString(12, compensationType != null ? compensationType : "Funded");
            ps.setString(13, contentHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("id");
                }
            }
            return id;
        } finally {
            pool.release(conn);
        }
    }

    public Optional<UUID> findOfferByHash(String contentHash) throws SQLException {
        String sql = "SELECT id FROM offers WHERE content_hash = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, contentHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of((UUID) rs.getObject("id"));
                }
            }
        } finally {
            pool.release(conn);
        }
        return Optional.empty();
    }

    public void markOfferDead(UUID id, LocalDate deadSince) throws SQLException {
        String sql = "UPDATE offers SET status = 'MAY_BE_CLOSED', dead_since = COALESCE(dead_since, ?) WHERE id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(deadSince));
            ps.setObject(2, id);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public void hideExpiredDeadOffers(int daysThreshold) throws SQLException {
        String sql = "UPDATE offers SET status = 'CLOSED' WHERE dead_since IS NOT NULL AND dead_since <= (CURRENT_DATE - ? * INTERVAL '1 day')";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, daysThreshold);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public void markOfferLive(UUID id) throws SQLException {
        String sql = "UPDATE offers SET status = 'ACTIVE', dead_since = NULL WHERE id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
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

    public UUID createLabOffer(UUID labId, CreateLabOfferRequest req) throws SQLException {
        UUID offerId = UUID.randomUUID();
        LocalDate today = LocalDate.now(clock);

        // 1. Duration calculation
        int durationMonths = 5;
        if (req.durationMonths() != null && req.durationMonths() > 0) {
            durationMonths = req.durationMonths();
        } else if (req.duration() != null && !req.duration().isBlank()) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(req.duration());
            if (m.find()) {
                try {
                    durationMonths = Integer.parseInt(m.group());
                } catch (NumberFormatException ignored) {}
            }
        }

        // 2. Start date parsing
        LocalDate start = null;
        if (req.startDate() != null && !req.startDate().isBlank()) {
            String sd = req.startDate().trim();
            try {
                start = LocalDate.parse(sd);
            } catch (Exception e1) {
                try {
                    DateTimeFormatter dtf = new java.time.format.DateTimeFormatterBuilder()
                            .parseCaseInsensitive()
                            .appendPattern("[MMMM yyyy][MMM yyyy][MM/yyyy][yyyy-MM]")
                            .parseDefaulting(java.time.temporal.ChronoField.DAY_OF_MONTH, 1)
                            .toFormatter(Locale.ENGLISH);
                    start = LocalDate.parse(sd, dtf);
                } catch (Exception ignored) {
                    start = today.plusMonths(2);
                }
            }
        }
        if (start == null) {
            start = today.plusMonths(2);
        }

        LocalDate closesAt = (start.isAfter(today)) ? start : today.plusMonths(3);

        String title = (req.title() != null && !req.title().isBlank()) ? req.title().trim() : "Research Internship";
        String domain = (req.domain() != null && !req.domain().isBlank()) ? req.domain().trim() : "Biology";
        String location = (req.location() != null && !req.location().isBlank()) ? req.location().trim() : "On site";
        String compType = (req.compensationType() != null && !req.compensationType().isBlank()) ? req.compensationType().trim() : "Paid";
        String compAmount = (req.compensationAmount() != null && !req.compensationAmount().isBlank()) ? req.compensationAmount().trim() : compType;

        List<String> tasks = req.tasks() != null ? req.tasks() : List.of();
        String description = (req.description() != null && !req.description().isBlank())
                ? req.description().trim()
                : (!tasks.isEmpty() ? tasks.get(0) : title);

        String status = Boolean.TRUE.equals(req.isDraft()) ? "DRAFT" : "PUBLISHED";
        String origin = "LAB_POSTED";
        String contentHash;
        try {
            byte[] hashBytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((UUID.randomUUID() + ":" + title + ":" + labId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            contentHash = java.util.HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            contentHash = UUID.randomUUID().toString().replace("-", "");
        }

        String sql = """
            INSERT INTO offers (id, lab_id, title, domain, duration_months, start_date, location,
                                compensation_type, compensation_amount, description, tasks, places,
                                status, origin, content_hash, closes_at, created_at, updated_at, fetched_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """;

        Connection conn = pool.acquire();
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, offerId);
                ps.setObject(2, labId);
                ps.setString(3, title);
                ps.setString(4, domain);
                ps.setInt(5, durationMonths);
                ps.setDate(6, java.sql.Date.valueOf(start));
                ps.setString(7, location);
                ps.setString(8, compType);
                ps.setString(9, compAmount);
                ps.setString(10, description);
                ps.setArray(11, conn.createArrayOf("text", tasks.toArray(new String[0])));
                ps.setString(12, status);
                ps.setString(13, origin);
                ps.setString(14, contentHash);
                ps.setDate(15, java.sql.Date.valueOf(closesAt));
                ps.executeUpdate();
            }

            if (req.skills() != null && !req.skills().isEmpty()) {
                String insertSkillSql = """
                    INSERT INTO skills (id, name, normalized_name, category)
                    VALUES (uuid_generate_v4(), ?, ?, 'Technical')
                    ON CONFLICT (normalized_name) DO NOTHING
                """;
                String linkSkillSql = """
                    INSERT INTO offer_skills (offer_id, skill_id, is_required)
                    SELECT ?, id, TRUE FROM skills WHERE normalized_name = ?
                    ON CONFLICT DO NOTHING
                """;

                try (PreparedStatement psSkill = conn.prepareStatement(insertSkillSql);
                     PreparedStatement psLink = conn.prepareStatement(linkSkillSql)) {
                    for (String skillName : req.skills()) {
                        if (skillName == null || skillName.isBlank()) continue;
                        String trimmed = skillName.trim();
                        String normalized = trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#.-]", "");
                        if (normalized.isBlank()) normalized = trimmed.toLowerCase(Locale.ROOT);

                        psSkill.setString(1, trimmed);
                        psSkill.setString(2, normalized);
                        psSkill.executeUpdate();

                        psLink.setObject(1, offerId);
                        psLink.setString(2, normalized);
                        psLink.executeUpdate();
                    }
                }
            }

            conn.commit();
            return offerId;
        } catch (Exception e) {
            conn.rollback();
            throw new SQLException("Failed to create lab offer: " + e.getMessage(), e);
        } finally {
            conn.setAutoCommit(true);
            pool.release(conn);
        }
    }

    public record LabOfferItem(
            UUID id,
            String title,
            String domain,
            int durationMonths,
            LocalDate startDate,
            String location,
            String compensationType,
            String compensationAmount,
            String status,
            LocalDate closesAt,
            Instant createdAt,
            int applicationsCount,
            List<String> skills
    ) {}

    public List<LabOfferItem> findOffersByLab(UUID labId) throws SQLException {
        if (labId == null) return List.of();
        String sql = """
            SELECT o.id, o.title, o.domain, o.duration_months, o.start_date, o.location,
                   o.compensation_type, o.compensation_amount, o.status, o.closes_at, o.created_at,
                   (SELECT COUNT(*) FROM applications a WHERE a.offer_id = o.id) as applications_count
            FROM offers o
            WHERE o.lab_id = ?
            ORDER BY o.created_at DESC
        """;
        List<LabOfferItem> list = new ArrayList<>();
        Connection conn = pool.acquire();
        try {
            Map<UUID, List<SkillItem>> skillsMap = loadAllOfferSkills(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, labId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID id = (UUID) rs.getObject("id");
                        List<String> skList = skillsMap.getOrDefault(id, List.of())
                                .stream().map(SkillItem::name).toList();
                        java.sql.Date stDate = rs.getDate("start_date");
                        java.sql.Date clDate = rs.getDate("closes_at");
                        Timestamp crTs = rs.getTimestamp("created_at");
                        list.add(new LabOfferItem(
                                id,
                                rs.getString("title"),
                                rs.getString("domain"),
                                rs.getInt("duration_months"),
                                stDate != null ? stDate.toLocalDate() : null,
                                rs.getString("location"),
                                rs.getString("compensation_type"),
                                rs.getString("compensation_amount"),
                                rs.getString("status"),
                                clDate != null ? clDate.toLocalDate() : null,
                                crTs != null ? crTs.toInstant() : Instant.now(),
                                rs.getInt("applications_count"),
                                skList
                        ));
                    }
                }
            }
        } finally {
            pool.release(conn);
        }
        return list;
    }
}
