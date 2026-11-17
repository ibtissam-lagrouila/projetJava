package com.fitwell.service;

import com.fitwell.core.db.ConnectionPool;
import com.fitwell.core.security.PasswordHasher;
import com.fitwell.model.enums.ApplicationStatus;
import com.fitwell.model.enums.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
                seedStudentMarrakech(conn);
                seedLab(conn);
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
                { "univ-casa.ma", "Université Hassan II de Casablanca" },
                { "um5.ac.ma", "Université Mohammed V de Rabat" },
                { "univ-fes.ma", "Université Sidi Mohamed Ben Abdellah de Fès" },
                { "uca.ma", "Université Cadi Ayyad de Marrakech" },
                { "uae.ac.ma", "Université Abdelmalek Essaâdi de Tétouan-Tanger" },
                { "ump.ma", "Université Mohammed Premier d'Oujda" }
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
                { "Python", "python", "Programming" },
                { "PyTorch", "pytorch", "AI/ML" },
                { "OpenCV", "opencv", "Computer Vision" },
                { "DICOM", "dicom", "Medical Imaging" },
                { "R", "r", "Data Science" },
                { "DESeq2", "deseq2", "Bioinformatics" },
                { "Nextflow", "nextflow", "Bioinformatics" },
                { "Bioconductor", "bioconductor", "Bioinformatics" },
                { "SQL", "sql", "Databases" },
                { "Git", "git", "Tooling" },
                { "Statistics", "statistics", "Mathematics" },
                { "Machine Learning", "machine-learning", "AI/ML" },
                { "ML", "ml", "AI/ML" },
                { "IA", "ia", "AI/ML" },
                { "Analyse de données", "analyse-de-donnees", "Data Science" },
                { "Data Analysis", "data-analysis", "Data Science" },
                { "React", "react", "Web Development" },
                { "Laravel", "laravel", "Web Development" },
                { "PHP", "php", "Programming" },
                { "WordPress", "wordpress", "Web Development" },
                { "MySQL", "mysql", "Databases" },
                { "REST API", "rest-api", "Web Development" },
                { "REST API", "api-rest", "Web Development" },
                { "C++", "c++", "Programming" },
                { "C", "c", "Programming" },
                { "Bootstrap", "bootstrap", "Web Development" },
                { "HTML", "html", "Web Development" },
                { "CSS", "css", "Web Development" },
                { "TypeScript", "typescript", "Web Development" },
                { "JavaScript", "javascript", "Web Development" },
                { "Node.js", "node.js", "Web Development" },
                { "Flutter", "flutter", "Mobile" },
                { "Dart", "dart", "Mobile" },
                { "Firebase", "firebase", "Mobile/Cloud" },
                { "Docker", "docker", "DevOps" },
                { "Kubernetes", "kubernetes", "DevOps" },
                { "Linux", "linux", "Systems" },
                { "AWS", "aws", "Cloud" },
                { "Power BI", "power-bi", "Data Science" },
                { "Pandas", "pandas", "Data Science" },
                { "Scikit-Learn", "scikit-learn", "AI/ML" },
                { "Deep Learning", "deep-learning", "AI/ML" },
                { "Unity", "unity", "Tooling" }
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
        if (userExists(conn, email))
            return;

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
        if (userId != null)
            return userId;

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
        String[] studentSkills = { "python", "pytorch", "opencv", "sql", "git" };
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

        // Add master CV record for student
        String cvSql = """
                    INSERT INTO cvs (id, student_id, file_name, file_path, raw_text, parsed_json, is_master, created_at)
                    VALUES (?, ?, 'CV-Yassine.pdf', '/uploads/cvs/sample.pdf', 'Yassine Bennis CV...',
                            '{"status":"CONFIRMED","fileSizeKb":214,"education":"Master 1, data science","university":"Université Hassan II","skills":["Python","PyTorch","OpenCV","SQL","Git"],"languages":["Arabic","French","English"],"projects":[{"title":"Image segmentation","description":"Trained a U-Net in PyTorch on public biomedical scans","skills":["Python","PyTorch","OpenCV"],"resultSummary":""},{"title":"Research abstracts scraper","description":"Scraped and classified medical abstracts using NLP and embeddings","skills":["Python","SQL"],"resultSummary":"12,000 papers indexed"}]}'::jsonb,
                            TRUE, CURRENT_TIMESTAMP)
                    ON CONFLICT DO NOTHING
                """;
        try (PreparedStatement ps = conn.prepareStatement(cvSql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, profileId);
            ps.executeUpdate();
        }

        return userId;
    }

    private UUID seedStudentMarrakech(Connection conn) throws SQLException {
        String email = "salma.alami@uca.ma";
        UUID userId = getUserIdByEmail(conn, email);
        if (userId != null)
            return userId;

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
                    VALUES (?, ?, 'Salma Alami', 'Université Cadi Ayyad', 'Ingénierie informatique',
                            ARRAY['Arabic', 'French', 'English'],
                            ARRAY['Intelligence artificielle', 'Machine Learning', 'Computer Vision'],
                            CURRENT_DATE + INTERVAL '1 month', 4)
                """;
        try (PreparedStatement ps = conn.prepareStatement(profileSql)) {
            ps.setObject(1, profileId);
            ps.setObject(2, userId);
            ps.executeUpdate();
        }

        // Add skills (Python, React, Machine Learning)
        String[] studentSkills = { "python", "react", "machine-learning" };
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
                    VALUES (?, ?, 'Modèles de Machine Learning & Deep Learning', 'Classification et prédiction avec Scikit-Learn et TensorFlow',
                            ARRAY['Python', 'Machine Learning'], 'Précision de 96% sur dataset benchmark'),
                           (?, ?, 'Plateforme interactive React', 'Application web frontend pour le monitoring de prédictions IA',
                            ARRAY['Python', 'React'], 'Tableaux de bord temps réel')
                """;
        try (PreparedStatement ps = conn.prepareStatement(projSql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, profileId);
            ps.setObject(3, UUID.randomUUID());
            ps.setObject(4, profileId);
            ps.executeUpdate();
        }

        // Add master CV
        String cvSql = """
                    INSERT INTO cvs (id, student_id, file_name, file_path, raw_text, parsed_json, is_master, created_at)
                    VALUES (?, ?, 'CV-Salma-Alami.pdf', '/uploads/cvs/sample-salma.pdf', 'Salma Alami CV...',
                            '{"status":"CONFIRMED","fileSizeKb":185,"education":"Ingénierie informatique","university":"Université Cadi Ayyad","skills":["Python","React","Machine Learning"],"languages":["Arabic","French","English"],"projects":[{"title":"Modèles de Machine Learning & Deep Learning","description":"Classification et prédiction avec Scikit-Learn et TensorFlow","skills":["Python","Machine Learning"],"resultSummary":"Précision de 96% sur dataset benchmark"},{"title":"Plateforme interactive React","description":"Application web frontend pour le monitoring de prédictions IA","skills":["Python","React"],"resultSummary":"Tableaux de bord temps réel"}]}'::jsonb,
                            TRUE, CURRENT_TIMESTAMP)
                    ON CONFLICT DO NOTHING
                """;
        try (PreparedStatement ps = conn.prepareStatement(cvSql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, profileId);
            ps.executeUpdate();
        }

        return userId;
    }

    private UUID seedLab(Connection conn) throws SQLException {
        String email = "s.elidrissi@univ-fes.ma";
        UUID userId = getUserIdByEmail(conn, email);
        if (userId != null)
            return userId;

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
        // Fetch student profile ID
        UUID studentProfileId = null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM student_profiles WHERE user_id = ?")) {
            ps.setObject(1, studentUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    studentProfileId = (UUID) rs.getObject(1);
            }
        }

        // 1. Clean any legacy fake sources or offers claiming to be from LinkedIn /
        // stagiaire
        try (PreparedStatement ps = conn.prepareStatement(
                """
                            DELETE FROM validation_items WHERE source_id IN (SELECT id FROM sources WHERE name IN ('LinkedIn', 'stagiaire.ma'));
                            DELETE FROM offers WHERE source_url ILIKE '%linkedin%' OR source_url ILIKE '%stagiaire%' OR compensation_amount ILIKE '%confirmé dans cet exemple%';
                            DELETE FROM sources WHERE name IN ('LinkedIn', 'stagiaire.ma');
                            DELETE FROM labs WHERE name IN ('Laboratoire fictif', 'Entreprise fictive', 'Tech Solutions Maroc');
                        """)) {
            ps.executeUpdate();
        }

        // 2. Ensure authentic university Labs from the Fitwell Design System exist
        UUID visionLabId = getOrCreateLab(conn, "Vision and Imaging Lab", "Faculté des Sciences de Rabat", "Rabat",
                "Computer Vision & Medical Imaging");
        UUID nlpLabId = getOrCreateLab(conn, "NLP Group", "Faculté des Sciences Ben M'Sick", "Casablanca",
                "Natural Language Processing");
        UUID roboticsLabId = getOrCreateLab(conn, "Robotics Lab",
                "École Nationale Supérieure d'Informatique et d'Analyse des Systèmes (ENSIAS)", "Rabat",
                "Robotics & Embedded Systems");
        UUID genomicsLabId = getOrCreateLab(conn, "Genomics Unit", "Faculté de Médecine et de Pharmacie de Fès", "Fès",
                "Genomics & Bioinformatics");
        UUID materialsLabId = getOrCreateLab(conn, "Materials Lab", "Faculté des Sciences Semlalia", "Marrakech",
                "Materials Science & Solar Energy");
        UUID climateLabId = getOrCreateLab(conn, "Climate Data Lab", "Faculté des Sciences et Techniques de Tanger",
                "Tangier", "Climate Data & Earth Observation");
        UUID webLabId = getOrCreateLab(conn, "Web & Digital Health Lab", "Université Hassan II", "Casablanca",
                "Web Development & Software Engineering");
        UUID aiMarrakechLabId = getOrCreateLab(conn, "AI & Data Science Research Lab", "Faculté des Sciences Semlalia",
                "Marrakech", "Artificial Intelligence & Data Science");

        // 3. Ensure Extra Skills exist in dictionary
        ensureSkillExists(conn, "JavaScript", "javascript", "Tech");
        ensureSkillExists(conn, "Node.js", "node.js", "Tech");
        ensureSkillExists(conn, "MATLAB", "matlab", "Engineering");
        ensureSkillExists(conn, "Materials Science", "materials-science", "Science");
        ensureSkillExists(conn, "GIS", "gis", "Tech");
        ensureSkillExists(conn, "Data Analysis", "data-analysis", "Data Science");
        ensureSkillExists(conn, "Computer Vision", "computer-vision", "AI/ML");
        ensureSkillExists(conn, "ML", "ml", "AI/ML");
        ensureSkillExists(conn, "IA", "ia", "AI/ML");
        ensureSkillExists(conn, "Spring Boot", "spring-boot", "Tech");
        ensureSkillExists(conn, "NLP", "nlp", "AI/ML");
        ensureSkillExists(conn, "Deep Learning", "deep-learning", "AI/ML");
        ensureSkillExists(conn, "AgriTech", "agritech", "Engineering");

        // Offer 1: Vision models for medical imaging (Internal lab offer)
        UUID offer1Id = getOfferIdByTitle(conn, "Vision models for medical imaging");
        if (offer1Id == null) {
            offer1Id = UUID.randomUUID();
            insertOffer(conn, offer1Id, visionLabId, "Vision models for medical imaging",
                    "Medical imaging, Computer Vision",
                    6, LocalDate.of(2026, 2, 1), "Rabat", "Funded", "Gratification 4000 DH/mois",
                    "The team trains segmentation models on annotated microscopy images and compares them with classical pipelines.",
                    new String[] { "Build annotated dataset with team", "Train and compare three segmentation models",
                            "Write short report" },
                    2, "PUBLISHED", "LAB_POSTED", null, null, "hash-vision-medical", LocalDate.of(2026, 11, 30));

            attachOfferSkill(conn, offer1Id, "python", true);
            attachOfferSkill(conn, offer1Id, "pytorch", true);
            attachOfferSkill(conn, offer1Id, "opencv", true);
            attachOfferSkill(conn, offer1Id, "dicom", false);
        }

        // Offer 2: NLP for Arabic dialects (Internal lab offer)
        UUID offer2Id = getOfferIdByTitle(conn, "NLP for Arabic dialects");
        if (offer2Id == null) {
            offer2Id = UUID.randomUUID();
            insertOffer(conn, offer2Id, nlpLabId, "NLP for Arabic dialects", "Natural Language Processing",
                    3, LocalDate.of(2026, 3, 1), "Casablanca", "Unfunded", null,
                    "Research on Moroccan Darija language models using modern transformer architectures.",
                    new String[] { "Data cleaning", "Fine-tuning models", "Evaluation benchmark" },
                    2, "PUBLISHED", "LAB_POSTED", null, null, "hash-nlp-arabic", LocalDate.of(2027, 1, 10));

            attachOfferSkill(conn, offer2Id, "python", true);
            attachOfferSkill(conn, offer2Id, "machine-learning", true);
        }

        // Offer 3: Drone navigation for inspection (Internal lab offer)
        UUID offer3Id = getOfferIdByTitle(conn, "Drone navigation for inspection");
        if (offer3Id == null) {
            offer3Id = UUID.randomUUID();
            insertOffer(conn, offer3Id, roboticsLabId, "Drone navigation for inspection",
                    "Robotics & Autonomous Systems",
                    6, LocalDate.of(2026, 2, 1), "Rabat", "Funded", "Gratification 3500 DH/mois",
                    "Autonomous drone navigation in GPS-denied environments using visual odometry and SLAM.",
                    new String[] { "Implement feature matching with OpenCV", "Integrate sensor telemetry with C++",
                            "Field testing in indoor tunnel" },
                    2, "PUBLISHED", "LAB_POSTED", null, null, "hash-drone-navigation", LocalDate.of(2027, 2, 28));

            attachOfferSkill(conn, offer3Id, "python", true);
            attachOfferSkill(conn, offer3Id, "opencv", true);
            attachOfferSkill(conn, offer3Id, "c++", true);
            attachOfferSkill(conn, offer3Id, "machine-learning", false);
        }

        // Offer 4: Gene expression analysis (Internal lab offer)
        UUID offer4Id = getOfferIdByTitle(conn, "Gene expression analysis");
        if (offer4Id == null) {
            offer4Id = UUID.randomUUID();
            insertOffer(conn, offer4Id, genomicsLabId, "Gene expression analysis", "Biology, bioinformatics",
                    4, LocalDate.of(2026, 3, 1), "Fès", "Funded", "Gratification 3000 DH/mois",
                    "Analyse differential gene expression across 24 samples, build a reproducible Nextflow pipeline.",
                    new String[] { "Analyse RNA-seq data", "Build Nextflow pipeline",
                            "Present results at lab meeting" },
                    1, "PUBLISHED", "LAB_POSTED", null, null, "hash-gene-expression", LocalDate.of(2026, 12, 15));

            attachOfferSkill(conn, offer4Id, "python", true);
            attachOfferSkill(conn, offer4Id, "r", true);
            attachOfferSkill(conn, offer4Id, "bioconductor", true);
            attachOfferSkill(conn, offer4Id, "nextflow", true);
        }

        // Offer 5: Thin-film solar cell testing (Internal lab offer)
        UUID offer5Id = getOfferIdByTitle(conn, "Thin-film solar cell testing");
        if (offer5Id == null) {
            offer5Id = UUID.randomUUID();
            insertOffer(conn, offer5Id, materialsLabId, "Thin-film solar cell testing",
                    "Materials Science, Photovoltaics",
                    4, LocalDate.of(2026, 3, 1), "Marrakech", "Funded", "Gratification 3000 DH/mois",
                    "Characterization and performance testing of perovskite thin-film photovoltaic cells under high temperature simulation.",
                    new String[] { "Prepare thin film samples in clean room", "Execute spectroscopic measurements",
                            "Analyze efficiency curves in Python" },
                    1, "PUBLISHED", "LAB_POSTED", null, null, "hash-materials-solar", LocalDate.of(2027, 1, 20));

            attachOfferSkill(conn, offer5Id, "python", true);
            attachOfferSkill(conn, offer5Id, "matlab", false);
            attachOfferSkill(conn, offer5Id, "materials-science", false);
        }

        // Offer 6: Coastal erosion modelling (Internal lab offer)
        UUID offer6Id = getOfferIdByTitle(conn, "Coastal erosion modelling");
        if (offer6Id == null) {
            offer6Id = UUID.randomUUID();
            insertOffer(conn, offer6Id, climateLabId, "Coastal erosion modelling",
                    "Climate & Earth Science, Oceanography",
                    5, LocalDate.of(2026, 3, 1), "Tangier", "Funded", "Gratification 3500 DH/mois",
                    "Numerical modeling of wave dynamics and sediment transport along the Strait of Gibraltar coastline using satellite radar data.",
                    new String[] { "Process Sentinel-1 satellite imagery", "Calibrate hydrodynamic wave model",
                            "Validate erosion risk maps" },
                    2, "PUBLISHED", "LAB_POSTED", null, null, "hash-coastal-erosion", LocalDate.of(2027, 2, 5));

            attachOfferSkill(conn, offer6Id, "python", true);
            attachOfferSkill(conn, offer6Id, "gis", true);
            attachOfferSkill(conn, offer6Id, "statistics", false);
        }

        // Offer 7: Web Platform & Architecture for Medical Data (Internal lab offer)
        UUID offer8Id = getOfferIdByTitle(conn, "Web Platform & Architecture for Medical Data");
        if (offer8Id == null) {
            offer8Id = UUID.randomUUID();
            insertOffer(conn, offer8Id, webLabId, "Web Platform & Architecture for Medical Data",
                    "Web Development & Medical Informatics",
                    4, LocalDate.of(2026, 2, 15), "Casablanca", "Funded", "Gratification 3000 DH/mois",
                    "Development and optimization of a high-performance web platform for clinical data collection, REST API endpoints, and research catalog.",
                    new String[] { "Build responsive frontend portal in React",
                            "Architect secure REST APIs in Laravel with MySQL",
                            "Integrate WordPress documentation portal" },
                    2, "PUBLISHED", "LAB_POSTED", null, null, "hash-web-architecture", LocalDate.of(2027, 2, 20));

            attachOfferSkill(conn, offer8Id, "react", true);
            attachOfferSkill(conn, offer8Id, "laravel", true);
            attachOfferSkill(conn, offer8Id, "rest-api", true);
            attachOfferSkill(conn, offer8Id, "mysql", true);
            attachOfferSkill(conn, offer8Id, "wordpress", false);
            attachOfferSkill(conn, offer8Id, "php", false);
        }

        // Clean any old demo titles
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM offers WHERE title IN ('Stage Machine Learning', 'Stage Computer Vision', 'Stage Data Analysis')")) {
            ps.executeUpdate();
        }

        // Demonstrative Offers in Marrakech for Student AI Matching (origin = 'DEMO',
        // marked 'DEMO · fictional')
        UUID offerDemo1Id = getOfferIdByTitle(conn, "Stage Machine Learning - Énergies Renouvelables");
        if (offerDemo1Id == null) {
            offerDemo1Id = UUID.randomUUID();
            insertOffer(conn, offerDemo1Id, aiMarrakechLabId, "Stage Machine Learning - Énergies Renouvelables",
                    "Intelligence Artificielle, Machine Learning",
                    4, LocalDate.of(2026, 3, 1), "Marrakech", "Funded", "Gratification 3000 DH/mois",
                    "Conception et entraînement de modèles d'apprentissage automatique supervisés et séries temporelles pour la prédiction de production solaire.",
                    new String[] { "Préparation et nettoyage des données en Python",
                            "Entraînement et optimisation de modèles ML",
                            "Déploiement API et évaluation des performances" },
                    2, "PUBLISHED", "DEMO", null, null, "hash-demo-ml-marrakech", LocalDate.of(2027, 2, 28));

            attachOfferSkill(conn, offerDemo1Id, "python", true);
            attachOfferSkill(conn, offerDemo1Id, "ml", true);
        }

        UUID offerDemo2Id = getOfferIdByTitle(conn, "Stage Computer Vision - Robotique Agricole");
        if (offerDemo2Id == null) {
            offerDemo2Id = UUID.randomUUID();
            insertOffer(conn, offerDemo2Id, aiMarrakechLabId, "Stage Computer Vision - Robotique Agricole",
                    "Computer Vision, Intelligence Artificielle",
                    4, LocalDate.of(2026, 3, 1), "Marrakech", "Funded", "Gratification 3500 DH/mois",
                    "Développement d'algorithmes de vision par ordinateur pour la détection des cultures et le guidage de robots agricoles.",
                    new String[] { "Annotation et augmentation de données visuelles",
                            "Implémentation d'architectures CNN sous PyTorch/OpenCV",
                            "Benchmarking et optimisation temps réel" },
                    2, "PUBLISHED", "DEMO", null, null, "hash-demo-cv-marrakech", LocalDate.of(2027, 3, 15));

            attachOfferSkill(conn, offerDemo2Id, "python", true);
            attachOfferSkill(conn, offerDemo2Id, "ia", true);
            attachOfferSkill(conn, offerDemo2Id, "opencv", false);
        }

        UUID offerDemo3Id = getOfferIdByTitle(conn, "Stage Data Analysis - Réseaux Intelligents");
        if (offerDemo3Id == null) {
            offerDemo3Id = UUID.randomUUID();
            insertOffer(conn, offerDemo3Id, aiMarrakechLabId, "Stage Data Analysis - Réseaux Intelligents",
                    "Intelligence Artificielle, Data Science, Analyse de données",
                    4, LocalDate.of(2026, 3, 1), "Marrakech", "Funded", "Gratification 3000 DH/mois",
                    "Exploration, analyse statistique et modélisation de données de consommation énergétique pour réseaux électriques intelligents.",
                    new String[] { "Requêtes d'extraction SQL et pipelines ETL",
                            "Analyses statistiques descriptives et inférentielles",
                            "Création de tableaux de bord interactifs" },
                    2, "PUBLISHED", "DEMO", null, null, "hash-demo-data-marrakech", LocalDate.of(2027, 1, 31));

            attachOfferSkill(conn, offerDemo3Id, "python", true);
            attachOfferSkill(conn, offerDemo3Id, "data-analysis", true);
            attachOfferSkill(conn, offerDemo3Id, "sql", false);
        }

        // Genuine Moroccan Sourced Offers: Mifactory, Université Ibn Tofail, DeepLeaf
        UUID mifactorySourceId = getOrCreateSource(conn, "Mifactory", "https://mifactory.ma/carrieres", "WEBSITE");
        UUID uitSourceId = getOrCreateSource(conn, "Université Ibn Tofail", "https://uit.ac.ma/stages", "WEBSITE");
        UUID deepleafSourceId = getOrCreateSource(conn, "DeepLeaf", "https://deepleaf.io/careers", "WEBSITE");

        // Sourced 1: Mifactory - Full Stack React & Spring Boot
        UUID offerMifactoryId = getOfferIdByTitle(conn, "Stage Développeur Full Stack React & Spring Boot");
        if (offerMifactoryId == null) {
            offerMifactoryId = UUID.randomUUID();
            insertOffer(conn, offerMifactoryId, null, "Stage Développeur Full Stack React & Spring Boot",
                    "Développement Logiciel & Architecture Web",
                    6, LocalDate.of(2026, 3, 1), "Casablanca", "Funded", "Gratification 4000 DH/mois",
                    "Conception et implémentation de microservices scalables et d'interfaces réactives pour les projets clients de la digital factory Mifactory.",
                    new String[] { "Développement composants frontend React", "Création d'APIs REST Spring Boot",
                            "Tests unitaires et intégration continue CI/CD" },
                    2, "PUBLISHED", "SOURCED", mifactorySourceId, "https://mifactory.ma/carrieres/stage-fullstack-2026",
                    "hash-mifactory-fullstack", LocalDate.of(2027, 2, 28), "Mifactory");

            attachOfferSkill(conn, offerMifactoryId, "react", true);
            attachOfferSkill(conn, offerMifactoryId, "spring-boot", true);
            attachOfferSkill(conn, offerMifactoryId, "javascript", true);
            attachOfferSkill(conn, offerMifactoryId, "rest-api", false);
        }

        // Sourced 2: Université Ibn Tofail - Recherche IA & TALN
        UUID offerUitId = getOfferIdByTitle(conn, "Stage Recherche IA & Traitement Automatique du Langage (TALN)");
        if (offerUitId == null) {
            offerUitId = UUID.randomUUID();
            insertOffer(conn, offerUitId, null, "Stage Recherche IA & Traitement Automatique du Langage (TALN)",
                    "Intelligence Artificielle & Traitement du Langage Naturel",
                    4, LocalDate.of(2026, 3, 1), "Kénitra", "Funded", "Gratification 3000 DH/mois",
                    "Projet de recherche sur les modèles de langage et le traitement automatique des dialectes arabes et documents juridiques à l'Université Ibn Tofail.",
                    new String[] { "Constitution et annotation de corpus bilingues",
                            "Entraînement de modèles Transformers HuggingFace",
                            "Évaluation quantitative des benchmarks" },
                    1, "PUBLISHED", "SOURCED", uitSourceId, "https://uit.ac.ma/stages/recherche-taln-2026",
                    "hash-uit-taln-2026", LocalDate.of(2027, 3, 31), "Université Ibn Tofail");

            attachOfferSkill(conn, offerUitId, "python", true);
            attachOfferSkill(conn, offerUitId, "nlp", true);
            attachOfferSkill(conn, offerUitId, "ia", true);
            attachOfferSkill(conn, offerUitId, "machine-learning", false);
        }

        // Sourced 3: DeepLeaf - Computer Vision & AgriTech Deep Learning
        UUID offerDeepLeafId = getOfferIdByTitle(conn, "Stage Computer Vision & Deep Learning pour l'AgriTech");
        if (offerDeepLeafId == null) {
            offerDeepLeafId = UUID.randomUUID();
            insertOffer(conn, offerDeepLeafId, null, "Stage Computer Vision & Deep Learning pour l'AgriTech",
                    "Computer Vision, AgriTech & Deep Learning",
                    5, LocalDate.of(2026, 3, 1), "Rabat", "Funded", "Gratification 4500 DH/mois",
                    "Développement et optimisation de modèles de vision par ordinateur embarquée pour le diagnostic phytosanitaire précoce des cultures agricoles chez DeepLeaf.",
                    new String[] { "Entraînement de modèles de segmentation et détection (YOLO, U-Net)",
                            "Optimisation de modèles pour inférence Edge (ONNX, TensorRT)",
                            "Validation expérimentale sur le terrain" },
                    2, "PUBLISHED", "SOURCED", deepleafSourceId, "https://deepleaf.io/careers/stage-cv-agritech-2026",
                    "hash-deepleaf-agritech", LocalDate.of(2027, 3, 15), "DeepLeaf");

            attachOfferSkill(conn, offerDeepLeafId, "python", true);
            attachOfferSkill(conn, offerDeepLeafId, "pytorch", true);
            attachOfferSkill(conn, offerDeepLeafId, "opencv", true);
            attachOfferSkill(conn, offerDeepLeafId, "computer-vision", true);
            attachOfferSkill(conn, offerDeepLeafId, "deep-learning", false);
        }

        // Create initial applications for student Yassine Bennis if available
        if (studentProfileId != null) {
            // Draft on Vision models
            createApplication(conn, studentProfileId, offer1Id, ApplicationStatus.DRAFT,
                    "Draft letter for vision models...", "Finish your draft before closes in 17 days");
            // Interview on Gene expression
            createApplication(conn, studentProfileId, offer4Id, ApplicationStatus.INTERVIEW,
                    "I am passionate about genomics pipelines...", "Confirm interview slot with Genomics Unit");
        }
    }

    private UUID getOrCreateSource(Connection conn, String name, String url, String type) throws SQLException {
        String selectSql = "SELECT id FROM sources WHERE name = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return (UUID) rs.getObject(1);
            }
        }
        UUID sourceId = UUID.randomUUID();
        String insertSql = "INSERT INTO sources (id, name, url, type, status, accuracy_pct) VALUES (?, ?, ?, ?, 'HEALTHY', 98)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setObject(1, sourceId);
            ps.setString(2, name);
            ps.setString(3, url);
            ps.setString(4, type);
            ps.executeUpdate();
        }
        return sourceId;
    }

    private void ensureSkillExists(Connection conn, String name, String normalizedName, String category)
            throws SQLException {
        String sql = "INSERT INTO skills (id, name, normalized_name, category) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, name);
            ps.setString(3, normalizedName);
            ps.setString(4, category);
            ps.executeUpdate();
        }
    }

    private UUID getOfferIdByTitle(Connection conn, String title) throws SQLException {
        String selectSql = "SELECT id FROM offers WHERE title = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, title);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return (UUID) rs.getObject(1);
            }
        }
        return null;
    }

    private UUID getOrCreateLab(Connection conn, String name, String university, String city, String domain)
            throws SQLException {
        String selectSql = "SELECT id FROM labs WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return (UUID) rs.getObject(1);
            }
        }
        UUID labId = UUID.randomUUID();
        String insertSql = "INSERT INTO labs (id, name, university_entity, city, domain, website, verified) VALUES (?, ?, ?, ?, ?, '', TRUE)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setObject(1, labId);
            ps.setString(2, name);
            ps.setString(3, university);
            ps.setString(4, city);
            ps.setString(5, domain);
            ps.executeUpdate();
        }
        return labId;
    }

    private void insertOffer(Connection conn, UUID id, UUID labId, String title, String domain, int duration,
            LocalDate start, String location, String compType, String compAmount,
            String desc, String[] tasks, int places, String status, String origin,
            UUID sourceId, String sourceUrl,
            String contentHash, LocalDate closesAt) throws SQLException {
        insertOffer(conn, id, labId, title, domain, duration, start, location, compType, compAmount,
                desc, tasks, places, status, origin, sourceId, sourceUrl, contentHash, closesAt, null);
    }

    private void insertOffer(Connection conn, UUID id, UUID labId, String title, String domain, int duration,
            LocalDate start, String location, String compType, String compAmount,
            String desc, String[] tasks, int places, String status, String origin,
            UUID sourceId, String sourceUrl,
            String contentHash, LocalDate closesAt, String employer) throws SQLException {
        String sql = """
                    INSERT INTO offers (id, lab_id, title, domain, duration_months, start_date, location,
                                        compensation_type, compensation_amount, description, tasks, places,
                                        status, origin, source_id, source_url, content_hash, closes_at, employer, fetched_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
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
            ps.setObject(15, sourceId);
            ps.setString(16, sourceUrl);
            ps.setString(17, contentHash);
            ps.setDate(18, java.sql.Date.valueOf(closesAt));
            ps.setString(19, employer);
            ps.executeUpdate();
        }
    }

    private void attachOfferSkill(Connection conn, UUID offerId, String normalizedSkill, boolean isRequired)
            throws SQLException {
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

    private void createApplication(Connection conn, UUID studentId, UUID offerId, ApplicationStatus status,
            String coverLetter, String nextStep) throws SQLException {
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
        // [name, url, type, status, accuracy, cguUrl, isAllowed, robotsStatus,
        // alertMessage]
        String[][] sources = {
                { "Mifactory", "https://mifactory.ma/carrieres", "WEBSITE", "ACTIVE", "96", "https://mifactory.ma/cgu",
                        "true", "ALLOWED", null },
                { "Université Ibn Tofail", "https://uit.ac.ma/stages", "WEBSITE", "ACTIVE", "98",
                        "https://uit.ac.ma/mentions-legales", "true", "ALLOWED", null },
                { "DeepLeaf", "https://deepleaf.io/careers", "WEBSITE", "ACTIVE", "95", "https://deepleaf.io/terms",
                        "true", "ALLOWED", null },
                { "labx.ma", "https://labx.ma/offers", "WEBSITE", "ACTIVE", "93", "https://labx.ma/cgu", "true",
                        "ALLOWED", null },
                { "univ-hassan2.ma/stages", "https://univ-hassan2.ma/stages", "WEBSITE", "ACTIVE", "97",
                        "https://univ-hassan2.ma/mentions-legales", "true", "ALLOWED", null },
                { "Research feeds", "https://research-feeds.ma/rss", "RSS", "ACTIVE", "99",
                        "https://research-feeds.ma/cgu", "true", "ALLOWED", null },
                { "Partner labs", "https://fitwell.ma/partner-labs", "POSTED", "ACTIVE", "100",
                        "https://fitwell.ma/terms", "true", "ALLOWED", null },
                { "enset-research.ma", "https://enset-research.ma/jobs", "WEBSITE", "PAUSED", "0",
                        "https://enset-research.ma/terms", "false", "DISALLOWED",
                        "Source paused: HTTP 429 Too Many Requests" }
        };
        String sql = """
                    INSERT INTO sources (id, name, url, type, status, accuracy_pct, last_run_at, cgu_url, is_allowed, robots_status, alert_message)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] s : sources) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, s[0]);
                ps.setString(3, s[1]);
                ps.setString(4, s[2]);
                ps.setString(5, s[3]);
                ps.setInt(6, Integer.parseInt(s[4]));
                ps.setString(7, s[5]);
                ps.setBoolean(8, Boolean.parseBoolean(s[6]));
                ps.setString(9, s[7]);
                ps.setString(10, s[8]);
                ps.executeUpdate();
            }
        }
    }

    private void seedPlatformRules(Connection conn) throws SQLException {
        String[][] rules = {
                { "publication_mode", "ABOVE_THRESHOLD" },
                { "confidence_threshold", "95" },
                { "block_duplicates", "true" },
                { "flag_free_email", "true" },
                { "show_source_on_offers", "false" }
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
                if (rs.next())
                    return (UUID) rs.getObject(1);
                return null;
            }
        }
    }
}
