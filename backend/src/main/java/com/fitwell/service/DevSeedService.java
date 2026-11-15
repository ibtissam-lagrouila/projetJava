package com.fitwell.service;

import com.fitwell.core.db.ConnectionPool;
import com.fitwell.core.security.PasswordHasher;
import com.fitwell.model.enums.ApplicationStatus;
import com.fitwell.model.enums.LabRole;
import com.fitwell.model.enums.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class DevSeedService {

    private static final Logger log = LoggerFactory.getLogger(DevSeedService.class);

    private final ConnectionPool pool;

    public DevSeedService(ConnectionPool pool) {
        this.pool = pool;
    }

    public void seedDevData() {
        log.info("Checking and seeding realistic development demo data...");
        try (Connection conn = pool.acquire()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                seedUniversityDomains(conn);
                seedSkills(conn);
                seedAdmin(conn);
                UUID studentUserId = seedStudent(conn);
                UUID labUserId = seedLab(conn);
                seedOffersAndApplications(conn, studentUserId);
                seedSources(conn);
                seedPlatformRules(conn);

                conn.commit();
                log.info("Development demo data successfully seeded!");
            } catch (Exception e) {
                conn.rollback();
                log.error("Failed to seed development data: {}", e.getMessage(), e);
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            log.error("Database connection error during seeding: {}", e.getMessage(), e);
        }
    }

    private void seedUniversityDomains(Connection conn) throws SQLException {
        String[][] domains = {
                {"univ-casa.ma", "Université Hassan II de Casablanca"},
                {"um5.ac.ma", "Université Mohammed V de Rabat"},
                {"univ-fes.ma", "Université Sidi Mohamed Ben Abdellah de Fès"},
                {"uca.ma", "Université Cadi Ayyad de Marrakech"},
                {"uae.ac.ma", "Université Abdelmalek Essaâdi de Tétouan-Tanger"},
                {"ump.ma", "Université Mohammed Premier d'Oujda"}
        };
        String sql = "INSERT INTO allowed_university_domains (id, domain, university_name) VALUES (?, ?, ?) ON CONFLICT (domain) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] d : domains) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, d[0]);
                ps.setString(3, d[1]);
                ps.executeUpdate();
            }
        }
    }

    private void seedSkills(Connection conn) throws SQLException {
        String[][] skills = {
                {"Python", "python", "Programming"},
                {"PyTorch", "pytorch", "AI/ML"},
                {"OpenCV", "opencv", "Computer Vision"},
                {"DICOM", "dicom", "Medical Imaging"},
                {"R", "r", "Data Science"},
                {"DESeq2", "deseq2", "Bioinformatics"},
                {"Nextflow", "nextflow", "Bioinformatics"},
                {"Bioconductor", "bioconductor", "Bioinformatics"},
                {"SQL", "sql", "Databases"},
                {"Git", "git", "Tooling"},
                {"Statistics", "statistics", "Mathematics"},
                {"Machine Learning", "machine-learning", "AI/ML"}
        };
        String sql = "INSERT INTO skills (id, name, normalized_name, category) VALUES (?, ?, ?, ?) ON CONFLICT (normalized_name) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] s : skills) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, s[0]);
                ps.setString(3, s[1]);
                ps.setString(4, s[2]);
                ps.executeUpdate();
            }
        }
    }

    private void seedAdmin(Connection conn) throws SQLException {
        String email = "admin@fitwell.ma";
        if (userExists(conn, email)) return;

        String hash = PasswordHasher.hashPassword("AdminSecure2026!");
        String salt = hash.split("\\$")[2];

        String sql = "INSERT INTO users (id, email, password_hash, salt, role, email_verified_at, is_active) VALUES (?, ?, ?, ?, 'ADMIN', CURRENT_TIMESTAMP, TRUE)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, email);
            ps.setString(3, hash);
            ps.setString(4, salt);
            ps.executeUpdate();
        }
    }

    private UUID seedStudent(Connection conn) throws SQLException {
        String email = "yassine.bennis@univ-casa.ma";
        UUID userId = getUserIdByEmail(conn, email);
        if (userId != null) return userId;

        userId = UUID.randomUUID();
        String hash = PasswordHasher.hashPassword("StudentSecure2026!");
        String salt = hash.split("\\$")[2];

        String userSql = "INSERT INTO users (id, email, password_hash, salt, role, email_verified_at, is_active) VALUES (?, ?, ?, ?, 'STUDENT', CURRENT_TIMESTAMP, TRUE)";
        try (PreparedStatement ps = conn.prepareStatement(userSql)) {
            ps.setObject(1, userId);
            ps.setString(2, email);
            ps.setString(3, hash);
            ps.setString(4, salt);
            ps.executeUpdate();
        }

        UUID profileId = UUID.randomUUID();
        String profileSql = """
            INSERT INTO student_profiles (id, user_id, full_name, university, level, languages, interests, availability_start, availability_duration_months)
            VALUES (?, ?, 'Yassine Bennis', 'Université Hassan II', 'Master 1, data science',
                    ARRAY['Arabic', 'French', 'English'],
                    ARRAY['Medical imaging', 'Machine Learning', 'Computer Vision'],
                    CURRENT_DATE + INTERVAL '1 month', 6)
        """;
        try (PreparedStatement ps = conn.prepareStatement(profileSql)) {
            ps.setObject(1, profileId);
            ps.setObject(2, userId);
            ps.executeUpdate();
        }

        // Add skills (Python, PyTorch, OpenCV, SQL, Git)
        String[] studentSkills = {"python", "pytorch", "opencv", "sql", "git"};
        for (String sName : studentSkills) {
            String skillSql = """
                INSERT INTO student_skills (student_id, skill_id, level)
                SELECT ?, id, 'Advanced' FROM skills WHERE normalized_name = ?
                ON CONFLICT DO NOTHING
            """;
            try (PreparedStatement ps = conn.prepareStatement(skillSql)) {
                ps.setObject(1, profileId);
                ps.setString(2, sName);
                ps.executeUpdate();
            }
        }

        // Add student projects
        String projSql = """
            INSERT INTO student_projects (id, student_id, title, description, skills, result_summary)
            VALUES (?, ?, 'Image segmentation', 'Trained a U-Net in PyTorch on public biomedical scans',
                    ARRAY['Python', 'PyTorch', 'OpenCV'], 'Outperformed baseline with 0.89 Dice score'),
                   (?, ?, 'Research abstracts scraper', 'Scraped and classified medical abstracts using NLP and embeddings',
                    ARRAY['Python', 'SQL'], 'Indexed 12,000 papers with 94% precision')
        """;
        try (PreparedStatement ps = conn.prepareStatement(projSql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, profileId);
            ps.setObject(3, UUID.randomUUID());
            ps.setObject(4, profileId);
            ps.executeUpdate();
        }

        return userId;
    }

    private UUID seedLab(Connection conn) throws SQLException {
        String email = "s.elidrissi@univ-fes.ma";
        UUID userId = getUserIdByEmail(conn, email);
        if (userId != null) return userId;

        userId = UUID.randomUUID();
        String hash = PasswordHasher.hashPassword("LabSecure2026!");
        String salt = hash.split("\\$")[2];

        String userSql = "INSERT INTO users (id, email, password_hash, salt, role, email_verified_at, is_active) VALUES (?, ?, ?, ?, 'LAB', CURRENT_TIMESTAMP, TRUE)";
        try (PreparedStatement ps = conn.prepareStatement(userSql)) {
            ps.setObject(1, userId);
            ps.setString(2, email);
            ps.setString(3, hash);
            ps.setString(4, salt);
            ps.executeUpdate();
        }

        UUID labId = UUID.randomUUID();
        String labSql = """
            INSERT INTO labs (id, name, university_entity, city, domain, website, verified)
            VALUES (?, 'Genomics Unit, Fès', 'Faculté de Médecine et de Pharmacie de Fès', 'Fès', 'Genomics & Bioinformatics', 'http://genomics.univ-fes.ma', TRUE)
        """;
        try (PreparedStatement ps = conn.prepareStatement(labSql)) {
            ps.setObject(1, labId);
            ps.executeUpdate();
        }

        String memberSql = "INSERT INTO lab_members (id, user_id, lab_id, role) VALUES (?, ?, ?, 'CEO')";
        try (PreparedStatement ps = conn.prepareStatement(memberSql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, userId);
            ps.setObject(3, labId);
            ps.executeUpdate();
        }

        return userId;
    }

    private void seedOffersAndApplications(Connection conn, UUID studentUserId) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM offers";
        try (PreparedStatement ps = conn.prepareStatement(checkSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getInt(1) > 0) return;
        }

        // Fetch student profile ID
        UUID studentProfileId = null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM student_profiles WHERE user_id = ?")) {
            ps.setObject(1, studentUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) studentProfileId = (UUID) rs.getObject(1);
            }
        }

        // Get lab ID
        UUID labId = null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM labs LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) labId = (UUID) rs.getObject(1);
            }
        }

        // Offer 1: Vision models for medical imaging
        UUID offer1Id = UUID.randomUUID();
        insertOffer(conn, offer1Id, labId, "Vision models for medical imaging", "Medical imaging, Computer Vision",
                6, LocalDate.now().plusMonths(1), "Rabat", "Funded", "Gratification mensuelle",
                "The team trains segmentation models on annotated microscopy images and compares them with classical pipelines.",
                new String[]{"Build annotated dataset with team", "Train and compare three segmentation models", "Write short report"},
                2, "PUBLISHED", "LAB_POSTED", "hash-vision-medical", LocalDate.now().plusDays(17));

        attachOfferSkill(conn, offer1Id, "python", true);
        attachOfferSkill(conn, offer1Id, "pytorch", true);
        attachOfferSkill(conn, offer1Id, "opencv", true);
        attachOfferSkill(conn, offer1Id, "dicom", false);

        // Offer 2: Gene expression analysis
        UUID offer2Id = UUID.randomUUID();
        insertOffer(conn, offer2Id, labId, "Gene expression analysis", "Biology, bioinformatics",
                4, LocalDate.now().plusMonths(2), "Fès", "Funded", "Paid",
                "Analyse differential gene expression across 24 samples, build a reproducible Nextflow pipeline.",
                new String[]{"Analyse RNA-seq data", "Build Nextflow pipeline", "Present results at lab meeting"},
                1, "PUBLISHED", "LAB_POSTED", "hash-gene-expression", LocalDate.now().plusDays(30));

        attachOfferSkill(conn, offer2Id, "python", true);
        attachOfferSkill(conn, offer2Id, "r", true);
        attachOfferSkill(conn, offer2Id, "bioconductor", true);
        attachOfferSkill(conn, offer2Id, "nextflow", true);

        // Offer 3: NLP for Arabic dialects
        UUID offer3Id = UUID.randomUUID();
        insertOffer(conn, offer3Id, null, "NLP for Arabic dialects", "Natural Language Processing",
                3, LocalDate.now().plusMonths(1), "Casablanca", "Unfunded", null,
                "Research on Moroccan Darija language models using modern transformer architectures.",
                new String[]{"Data cleaning", "Fine-tuning models", "Evaluation benchmark"},
                2, "PUBLISHED", "LAB_POSTED", "hash-nlp-arabic", LocalDate.now().plusDays(45));

        // Create initial applications for student Yassine Bennis
        if (studentProfileId != null) {
            // Draft on Vision models
            createApplication(conn, studentProfileId, offer1Id, ApplicationStatus.DRAFT, "Draft letter for vision models...", "Finish your draft before closes in 17 days");
            // Interview on Gene expression
            createApplication(conn, studentProfileId, offer2Id, ApplicationStatus.INTERVIEW, "I am passionate about genomics pipelines...", "Confirm interview slot with Genomics Unit");
        }
    }

    private void insertOffer(Connection conn, UUID id, UUID labId, String title, String domain, int duration,
                             LocalDate start, String location, String compType, String compAmount,
                             String desc, String[] tasks, int places, String status, String origin,
                             String contentHash, LocalDate closesAt) throws SQLException {
        String sql = """
            INSERT INTO offers (id, lab_id, title, domain, duration_months, start_date, location,
                                compensation_type, compensation_amount, description, tasks, places,
                                status, origin, content_hash, closes_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, labId);
            ps.setString(3, title);
            ps.setString(4, domain);
            ps.setInt(5, duration);
            ps.setDate(6, java.sql.Date.valueOf(start));
            ps.setString(7, location);
            ps.setString(8, compType);
            ps.setString(9, compAmount);
            ps.setString(10, desc);
            ps.setArray(11, conn.createArrayOf("text", tasks));
            ps.setInt(12, places);
            ps.setString(13, status);
            ps.setString(14, origin);
            ps.setString(15, contentHash);
            ps.setDate(16, java.sql.Date.valueOf(closesAt));
            ps.executeUpdate();
        }
    }

    private void attachOfferSkill(Connection conn, UUID offerId, String normalizedSkill, boolean isRequired) throws SQLException {
        String sql = """
            INSERT INTO offer_skills (offer_id, skill_id, is_required)
            SELECT ?, id, ? FROM skills WHERE normalized_name = ?
            ON CONFLICT DO NOTHING
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, offerId);
            ps.setBoolean(2, isRequired);
            ps.setString(3, normalizedSkill);
            ps.executeUpdate();
        }
    }

    private void createApplication(Connection conn, UUID studentId, UUID offerId, ApplicationStatus status, String coverLetter, String nextStep) throws SQLException {
        String sql = """
            INSERT INTO applications (id, student_id, offer_id, status, cover_letter, next_step_calc, sent_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (student_id, offer_id) DO NOTHING
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, studentId);
            ps.setObject(3, offerId);
            ps.setString(4, status.name());
            ps.setString(5, coverLetter);
            ps.setString(6, nextStep);
            ps.executeUpdate();
        }
    }

    private void seedSources(Connection conn) throws SQLException {
        String[][] sources = {
                {"labx.ma", "https://labx.ma/offers", "WEBSITE", "WARNING", "93"},
                {"univ-hassan2.ma/stages", "https://univ-hassan2.ma/stages", "WEBSITE", "HEALTHY", "97"},
                {"Research feeds", "https://research-feeds.ma/rss", "RSS", "HEALTHY", "99"},
                {"Partner labs", "https://fitwell.ma/partner-labs", "POSTED", "HEALTHY", "100"},
                {"enset-research.ma", "https://enset-research.ma/jobs", "WEBSITE", "FAILING", "0"}
        };
        String sql = "INSERT INTO sources (id, name, url, type, status, accuracy_pct, last_run_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] s : sources) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, s[0]);
                ps.setString(3, s[1]);
                ps.setString(4, s[2]);
                ps.setString(5, s[3]);
                ps.setInt(6, Integer.parseInt(s[4]));
                ps.executeUpdate();
            }
        }
    }

    private void seedPlatformRules(Connection conn) throws SQLException {
        String[][] rules = {
                {"publication_mode", "ABOVE_THRESHOLD"},
                {"confidence_threshold", "95"},
                {"block_duplicates", "true"},
                {"flag_free_email", "true"},
                {"show_source_on_offers", "false"}
        };
        String sql = "INSERT INTO platform_rules (rule_key, rule_value) VALUES (?, ?) ON CONFLICT (rule_key) DO UPDATE SET rule_value = EXCLUDED.rule_value";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] r : rules) {
                ps.setString(1, r[0]);
                ps.setString(2, r[1]);
                ps.executeUpdate();
            }
        }
    }

    private boolean userExists(Connection conn, String email) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE email = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private UUID getUserIdByEmail(Connection conn, String email) throws SQLException {
        String sql = "SELECT id FROM users WHERE email = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return (UUID) rs.getObject(1);
                return null;
            }
        }
    }
}

