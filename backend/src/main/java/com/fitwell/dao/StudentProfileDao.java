package com.fitwell.dao;

import com.fitwell.core.db.ConnectionPool;
import com.fitwell.core.json.JsonCodec;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class StudentProfileDao {

    private final ConnectionPool pool;

    public StudentProfileDao(ConnectionPool pool) {
        this.pool = pool;
    }

    public void createProfile(UUID userId, String fullName, String university, String level) throws SQLException {
        String sql = """
            INSERT INTO student_profiles (id, user_id, full_name, university, level, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id) DO NOTHING
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, userId);
            ps.setString(3, fullName);
            ps.setString(4, university);
            ps.setString(5, level);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public Optional<StudentFullProfile> findFullProfileByUserId(UUID userId) throws SQLException {
        String sql = """
            SELECT id, user_id, full_name, university, level, languages, interests,
                   availability_start, availability_duration_months, photo_path, ai_assistance_enabled
            FROM student_profiles WHERE user_id = ?
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                UUID profileId = (UUID) rs.getObject("id");
                String fullName = rs.getString("full_name");
                String university = rs.getString("university");
                String level = rs.getString("level");

                Array langArray = rs.getArray("languages");
                List<String> languages = langArray != null ? Arrays.asList((String[]) langArray.getArray()) : List.of();

                Array intArray = rs.getArray("interests");
                List<String> interests = intArray != null ? Arrays.asList((String[]) intArray.getArray()) : List.of();

                java.sql.Date availDate = rs.getDate("availability_start");
                LocalDate availabilityStart = availDate != null ? availDate.toLocalDate() : null;
                int duration = rs.getInt("availability_duration_months");
                String photoPath = rs.getString("photo_path");
                boolean aiEnabled = rs.getBoolean("ai_assistance_enabled");

                // Get skills
                List<String> skills = getSkillsForStudent(conn, profileId);

                // Get projects
                List<StudentProjectRecord> projects = getProjectsForStudent(conn, profileId);

                return Optional.of(new StudentFullProfile(
                        profileId, userId, fullName, university, level,
                        languages, interests, availabilityStart, duration,
                        photoPath, aiEnabled, skills, projects
                ));
            }
        } finally {
            pool.release(conn);
        }
    }

    public void updateProfileDetails(UUID userId, String fullName, String university, String level,
                                     List<String> languages, List<String> interests,
                                     LocalDate availabilityStart, int durationMonths) throws SQLException {
        String sql = """
            UPDATE student_profiles
            SET full_name = ?, university = ?, level = ?,
                languages = ?, interests = ?,
                availability_start = ?, availability_duration_months = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ?
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, university);
            ps.setString(3, level);
            ps.setArray(4, conn.createArrayOf("text", languages.toArray(new String[0])));
            ps.setArray(5, conn.createArrayOf("text", interests.toArray(new String[0])));
            ps.setDate(6, availabilityStart != null ? java.sql.Date.valueOf(availabilityStart) : null);
            ps.setInt(7, durationMonths > 0 ? durationMonths : 4);
            ps.setObject(8, userId);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public void updateAiAssistance(UUID userId, boolean enabled) throws SQLException {
        String sql = "UPDATE student_profiles SET ai_assistance_enabled = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, enabled);
            ps.setObject(2, userId);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public void updatePhoto(UUID userId, String photoPath) throws SQLException {
        String sql = "UPDATE student_profiles SET photo_path = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, photoPath);
            ps.setObject(2, userId);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public void replaceStudentSkills(UUID studentId, List<String> skillNames) throws SQLException {
        Connection conn = pool.acquire();
        try {
            // Delete existing
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM student_skills WHERE student_id = ?")) {
                del.setObject(1, studentId);
                del.executeUpdate();
            }

            // Insert new skills, resolving or creating normalized skill IDs
            for (String skill : skillNames) {
                String norm = skill.trim().toLowerCase().replaceAll("[^a-z0-9]", "-");
                // Ensure skill exists in master taxonomy
                String insTax = "INSERT INTO skills (id, name, normalized_name) VALUES (?, ?, ?) ON CONFLICT (normalized_name) DO NOTHING";
                try (PreparedStatement ps = conn.prepareStatement(insTax)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setString(2, skill.trim());
                    ps.setString(3, norm);
                    ps.executeUpdate();
                }

                // Link to student
                String linkSql = """
                    INSERT INTO student_skills (student_id, skill_id, level)
                    SELECT ?, id, 'Intermediate' FROM skills WHERE normalized_name = ?
                    ON CONFLICT DO NOTHING
                """;
                try (PreparedStatement ps = conn.prepareStatement(linkSql)) {
                    ps.setObject(1, studentId);
                    ps.setString(2, norm);
                    ps.executeUpdate();
                }
            }
        } finally {
            pool.release(conn);
        }
    }

    public void replaceStudentProjects(UUID studentId, List<StudentProjectRecord> projects) throws SQLException {
        Connection conn = pool.acquire();
        try {
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM student_projects WHERE student_id = ?")) {
                del.setObject(1, studentId);
                del.executeUpdate();
            }

            String ins = """
                INSERT INTO student_projects (id, student_id, title, description, skills, result_summary, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
            try (PreparedStatement ps = conn.prepareStatement(ins)) {
                for (StudentProjectRecord p : projects) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, studentId);
                    ps.setString(3, p.title());
                    ps.setString(4, p.description());
                    ps.setArray(5, conn.createArrayOf("text", p.skills().toArray(new String[0])));
                    ps.setString(6, p.resultSummary());
                    ps.executeUpdate();
                }
            }
        } finally {
            pool.release(conn);
        }
    }

    public void recordUploadedCv(UUID studentId, String fileName, String filePath, String rawText, Object parsedJson) throws SQLException {
        recordDraftCv(studentId, fileName, filePath, rawText, parsedJson);
    }

    public UUID recordDraftCv(UUID studentId, String fileName, String filePath, String rawText, Object parsedJson) throws SQLException {
        Connection conn = pool.acquire();
        try {
            // Delete previous draft file from disk and database if it exists (for "Replace file")
            String selectOldSql = "SELECT id, file_path FROM cvs WHERE student_id = ? AND parsed_json->>'status' = 'DRAFT'";
            try (PreparedStatement ps = conn.prepareStatement(selectOldSql)) {
                ps.setObject(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String oldPath = rs.getString("file_path");
                        if (oldPath != null && !oldPath.isBlank()) {
                            try {
                                java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(oldPath));
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM cvs WHERE student_id = ? AND parsed_json->>'status' = 'DRAFT'")) {
                ps.setObject(1, studentId);
                ps.executeUpdate();
            }

            UUID newCvId = UUID.randomUUID();
            String sql = """
                INSERT INTO cvs (id, student_id, file_name, file_path, raw_text, parsed_json, is_master, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, TRUE, CURRENT_TIMESTAMP)
            """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, newCvId);
                ps.setObject(2, studentId);
                ps.setString(3, fileName);
                ps.setString(4, filePath);
                ps.setString(5, rawText);
                ps.setString(6, JsonCodec.toJson(parsedJson));
                ps.executeUpdate();
            }
            return newCvId;
        } finally {
            pool.release(conn);
        }
    }

    public Optional<DraftCvRecord> findLatestDraftCv(UUID studentId) throws SQLException {
        String sql = """
            SELECT id, student_id, file_name, file_path, raw_text, parsed_json::text, created_at
            FROM cvs
            WHERE student_id = ? AND parsed_json->>'status' = 'DRAFT'
            ORDER BY created_at DESC LIMIT 1
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new DraftCvRecord(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("student_id"),
                            rs.getString("file_name"),
                            rs.getString("file_path"),
                            rs.getString("raw_text"),
                            rs.getString("parsed_json"),
                            rs.getTimestamp("created_at")
                    ));
                }
            }
        } finally {
            pool.release(conn);
        }
        return Optional.empty();
    }

    public Optional<DraftCvRecord> findDraftCvById(UUID cvId, UUID studentId) throws SQLException {
        String sql = """
            SELECT id, student_id, file_name, file_path, raw_text, parsed_json::text, created_at
            FROM cvs
            WHERE id = ? AND student_id = ?
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, cvId);
            ps.setObject(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new DraftCvRecord(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("student_id"),
                            rs.getString("file_name"),
                            rs.getString("file_path"),
                            rs.getString("raw_text"),
                            rs.getString("parsed_json"),
                            rs.getTimestamp("created_at")
                    ));
                }
            }
        } finally {
            pool.release(conn);
        }
        return Optional.empty();
    }

    public int countRecentCvUploads(UUID studentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM cvs WHERE student_id = ? AND created_at > CURRENT_TIMESTAMP - INTERVAL '1 hour'";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } finally {
            pool.release(conn);
        }
        return 0;
    }

    public void skipOnboarding(UUID userId) throws SQLException {
        String sql = "UPDATE student_profiles SET onboarding_skipped = TRUE, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public void confirmCvDraft(UUID cvId, UUID studentId, String education, String university,
                               List<String> skills, List<String> languages,
                               List<com.fitwell.service.CvExtractorService.ExtractedProject> projects,
                               Object confirmedJson) throws SQLException {
        Connection conn = pool.acquire();
        try {
            conn.setAutoCommit(false);
            try {
                // 1. Update profile
                String updateProfileSql = """
                    UPDATE student_profiles
                    SET level = ?, university = ?, languages = ?,
                        onboarding_completed = TRUE, onboarding_skipped = FALSE,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                """;
                try (PreparedStatement ps = conn.prepareStatement(updateProfileSql)) {
                    ps.setString(1, education != null ? education : "");
                    ps.setString(2, university != null ? university : "");
                    ps.setArray(3, conn.createArrayOf("text", (languages != null ? languages : List.<String>of()).toArray(new String[0])));
                    ps.setObject(4, studentId);
                    ps.executeUpdate();
                }

                // 2. Replace student skills
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM student_skills WHERE student_id = ?")) {
                    del.setObject(1, studentId);
                    del.executeUpdate();
                }
                if (skills != null) {
                    for (String sName : skills) {
                        if (sName == null || sName.isBlank()) continue;
                        String norm = sName.trim().toLowerCase(Locale.ROOT);
                        String insertSkillSql = """
                            INSERT INTO student_skills (student_id, skill_id, level)
                            SELECT ?, id, 'Intermediate' FROM skills WHERE normalized_name = ?
                            ON CONFLICT DO NOTHING
                        """;
                        try (PreparedStatement ps = conn.prepareStatement(insertSkillSql)) {
                            ps.setObject(1, studentId);
                            ps.setString(2, norm);
                            ps.executeUpdate();
                        }
                    }
                }

                // 3. Replace student projects
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM student_projects WHERE student_id = ?")) {
                    del.setObject(1, studentId);
                    del.executeUpdate();
                }
                if (projects != null) {
                    for (var proj : projects) {
                        String insertProjSql = """
                            INSERT INTO student_projects (id, student_id, title, description, skills, result_summary)
                            VALUES (?, ?, ?, ?, ?, ?)
                        """;
                        try (PreparedStatement ps = conn.prepareStatement(insertProjSql)) {
                            ps.setObject(1, UUID.randomUUID());
                            ps.setObject(2, studentId);
                            ps.setString(3, proj.title());
                            ps.setString(4, proj.description());
                            ps.setArray(5, conn.createArrayOf("text", (proj.skills() != null ? proj.skills() : List.<String>of()).toArray(new String[0])));
                            ps.setString(6, proj.resultSummary());
                            ps.executeUpdate();
                        }
                    }
                }

                // 4. Update CV row parsed_json with CONFIRMED status
                String updateCvSql = "UPDATE cvs SET parsed_json = ?::jsonb WHERE id = ? AND student_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateCvSql)) {
                    ps.setString(1, JsonCodec.toJson(confirmedJson));
                    ps.setObject(2, cvId);
                    ps.setObject(3, studentId);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } finally {
            pool.release(conn);
        }
    }

    public Map<String, Object> exportAllData(UUID userId) throws SQLException {
        Map<String, Object> archive = new LinkedHashMap<>();
        Connection conn = pool.acquire();
        try {
            // User info
            try (PreparedStatement ps = conn.prepareStatement("SELECT id, email, role, created_at FROM users WHERE id = ?")) {
                ps.setObject(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        archive.put("user", Map.of(
                                "id", rs.getObject("id").toString(),
                                "email", rs.getString("email"),
                                "role", rs.getString("role"),
                                "created_at", rs.getTimestamp("created_at").toString()
                        ));
                    }
                }
            }

            // Profile
            Optional<StudentFullProfile> profile = findFullProfileByUserId(userId);
            profile.ifPresent(p -> archive.put("student_profile", p));

            // Applications
            List<Map<String, Object>> apps = new ArrayList<>();
            String appSql = """
                SELECT a.id, o.title as offer_title, a.status, a.cover_letter, a.sent_at, a.created_at
                FROM applications a
                JOIN offers o ON a.offer_id = o.id
                JOIN student_profiles sp ON a.student_id = sp.id
                WHERE sp.user_id = ?
            """;
            try (PreparedStatement ps = conn.prepareStatement(appSql)) {
                ps.setObject(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        apps.add(Map.of(
                                "id", rs.getObject("id").toString(),
                                "offer_title", rs.getString("offer_title"),
                                "status", rs.getString("status"),
                                "cover_letter", rs.getString("cover_letter") != null ? rs.getString("cover_letter") : "",
                                "created_at", rs.getTimestamp("created_at").toString()
                        ));
                    }
                }
            }
            archive.put("applications", apps);

        } finally {
            pool.release(conn);
        }
        return archive;
    }

    public void deleteUserAccount(UUID userId) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        } finally {
            pool.release(conn);
        }
    }

    public record OnboardingStatus(boolean completed, boolean skipped) {}

    public OnboardingStatus getOnboardingStatus(UUID userId) throws SQLException {
        String sql = "SELECT onboarding_completed, onboarding_skipped FROM student_profiles WHERE user_id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new OnboardingStatus(rs.getBoolean("onboarding_completed"), rs.getBoolean("onboarding_skipped"));
                }
                return new OnboardingStatus(false, false);
            }
        } finally {
            pool.release(conn);
        }
    }

    public boolean hasCompletedOnboarding(UUID userId) throws SQLException {
        String sql = """
            SELECT 1 FROM student_profiles
            WHERE user_id = ? AND (onboarding_completed = TRUE OR onboarding_skipped = TRUE)
        """;
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } finally {
            pool.release(conn);
        }
    }

    public Optional<StudentProfileInfo> findByUserId(UUID userId) throws SQLException {
        String sql = "SELECT id, full_name, university, level FROM student_profiles WHERE user_id = ?";
        Connection conn = pool.acquire();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new StudentProfileInfo(
                            (UUID) rs.getObject("id"),
                            rs.getString("full_name"),
                            rs.getString("university"),
                            rs.getString("level")
                    ));
                }
                return Optional.empty();
            }
        } finally {
            pool.release(conn);
        }
    }

    private List<String> getSkillsForStudent(Connection conn, UUID profileId) throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT s.name FROM student_skills ss JOIN skills s ON ss.skill_id = s.id WHERE ss.student_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("name"));
                }
            }
        }
        return list;
    }

    private List<StudentProjectRecord> getProjectsForStudent(Connection conn, UUID profileId) throws SQLException {
        List<StudentProjectRecord> list = new ArrayList<>();
        String sql = "SELECT id, title, description, skills, result_summary FROM student_projects WHERE student_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Array sArray = rs.getArray("skills");
                    List<String> sList = sArray != null ? Arrays.asList((String[]) sArray.getArray()) : List.of();
                    list.add(new StudentProjectRecord(
                            (UUID) rs.getObject("id"),
                            rs.getString("title"),
                            rs.getString("description"),
                            sList,
                            rs.getString("result_summary")
                    ));
                }
            }
        }
        return list;
    }

    public record StudentProfileInfo(UUID id, String fullName, String university, String level) {}

    public record DraftCvRecord(
            UUID id,
            UUID studentId,
            String fileName,
            String filePath,
            String rawText,
            String parsedJson,
            Timestamp createdAt
    ) {}

    public record StudentProjectRecord(UUID id, String title, String description, List<String> skills, String resultSummary) {}

    public record StudentFullProfile(
            UUID id,
            UUID userId,
            String fullName,
            String university,
            String level,
            List<String> languages,
            List<String> interests,
            LocalDate availabilityStart,
            int availabilityDurationMonths,
            String photoPath,
            boolean aiAssistanceEnabled,
            List<String> skills,
            List<StudentProjectRecord> projects
    ) {}
}
