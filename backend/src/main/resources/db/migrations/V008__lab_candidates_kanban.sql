-- V008: Unify Genomics Unit Lab offers and ensure realistic Kanban candidate pipeline
-- Idempotent script for Lab Candidates Kanban view

-- 1. Ensure all Genomics Unit offers belong to the active recruiter lab
DO $$
DECLARE
    active_lab_id UUID;
BEGIN
    SELECT lm.lab_id INTO active_lab_id
    FROM lab_members lm
    JOIN users u ON lm.user_id = u.id
    WHERE u.email = 's.elidrissi@univ-fes.ma'
    LIMIT 1;

    IF active_lab_id IS NOT NULL THEN
        UPDATE offers 
        SET lab_id = active_lab_id 
        WHERE lab_id IN (SELECT id FROM labs WHERE name ILIKE '%Genomics%' AND id <> active_lab_id);

        INSERT INTO lab_members (id, user_id, lab_id, role)
        SELECT uuid_generate_v4(), u.id, l.id, 'CEO'
        FROM users u
        CROSS JOIN labs l
        WHERE u.email = 's.elidrissi@univ-fes.ma'
          AND l.name ILIKE '%Genomics%'
          AND NOT EXISTS (
              SELECT 1 FROM lab_members lm WHERE lm.user_id = u.id AND lm.lab_id = l.id
          );
    END IF;
END $$;

-- 2. Ensure skills exist in dictionary
INSERT INTO skills (id, name, normalized_name, category)
VALUES 
    (uuid_generate_v4(), 'Python', 'python', 'Tech'),
    (uuid_generate_v4(), 'R', 'r', 'Tech'),
    (uuid_generate_v4(), 'Stats', 'stats', 'Data Science'),
    (uuid_generate_v4(), 'Statistics', 'statistics', 'Data Science'),
    (uuid_generate_v4(), 'Bioconductor', 'bioconductor', 'Tech'),
    (uuid_generate_v4(), 'DESeq2', 'deseq2', 'Tech'),
    (uuid_generate_v4(), 'Nextflow', 'nextflow', 'Tech'),
    (uuid_generate_v4(), 'scRNA-seq', 'scrna-seq', 'Tech'),
    (uuid_generate_v4(), 'Lab work', 'lab work', 'Science'),
    (uuid_generate_v4(), 'ML', 'ml', 'AI/ML')
ON CONFLICT (normalized_name) DO NOTHING;

-- 3. Ensure student profiles and attach skills
CREATE OR REPLACE FUNCTION ensure_kanban_student(
    p_email TEXT,
    p_name TEXT,
    p_univ TEXT,
    p_level TEXT,
    p_field TEXT,
    p_skills TEXT[]
) RETURNS UUID AS $$
DECLARE
    v_user_id UUID;
    v_prof_id UUID;
    v_skill_name TEXT;
    v_skill_id UUID;
BEGIN
    SELECT id INTO v_user_id FROM users WHERE email = p_email;
    IF v_user_id IS NULL THEN
        v_user_id := uuid_generate_v4();
        INSERT INTO users (id, email, password_hash, salt, role, email_verified_at, is_active)
        VALUES (v_user_id, p_email, 'pbkdf2_sha256$600000$salt123$hash123', 'salt123', 'STUDENT', CURRENT_TIMESTAMP, TRUE);
    END IF;

    SELECT id INTO v_prof_id FROM student_profiles WHERE user_id = v_user_id;
    IF v_prof_id IS NULL THEN
        v_prof_id := uuid_generate_v4();
        INSERT INTO student_profiles (id, user_id, full_name, university, level, languages, interests)
        VALUES (v_prof_id, v_user_id, p_name, p_univ, p_level, ARRAY['French', 'English'], ARRAY[p_field]);
    ELSE
        UPDATE student_profiles
        SET full_name = p_name, university = p_univ, level = p_level, interests = ARRAY[p_field]
        WHERE id = v_prof_id;
    END IF;

    FOREACH v_skill_name IN ARRAY p_skills LOOP
        SELECT id INTO v_skill_id FROM skills WHERE normalized_name = lower(trim(v_skill_name)) LIMIT 1;
        IF v_skill_id IS NOT NULL THEN
            INSERT INTO student_skills (student_id, skill_id)
            VALUES (v_prof_id, v_skill_id)
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;

    RETURN v_prof_id;
END;
$$ LANGUAGE plpgsql;

-- 4. Create / update candidates and seed pipeline for primary offer
DO $$
DECLARE
    active_lab_id UUID;
    primary_offer_id UUID;
    s_imane_id UUID;
    s_karim_id UUID;
    s_sara_id UUID;
    s_hamza_id UUID;
    s_nour_id UUID;
    s_yassine_id UUID;
    s_oumaima_id UUID;
BEGIN
    SELECT lm.lab_id INTO active_lab_id
    FROM lab_members lm
    JOIN users u ON lm.user_id = u.id
    WHERE u.email = 's.elidrissi@univ-fes.ma'
    LIMIT 1;

    -- Pick the primary offer
    SELECT id INTO primary_offer_id
    FROM offers
    WHERE lab_id = active_lab_id AND title ILIKE '%Gene expression%'
    ORDER BY created_at DESC
    LIMIT 1;

    IF primary_offer_id IS NULL THEN
        SELECT id INTO primary_offer_id FROM offers WHERE lab_id = active_lab_id LIMIT 1;
    END IF;

    -- Create or link students with their actual skills from the design
    s_imane_id   := ensure_kanban_student('imane.rafik@univ-casa.ma', 'Imane Rafik', 'Université Hassan II', 'Licence 3, statistics', 'Statistics & Biostatistics', ARRAY['R', 'Stats', 'Bioconductor']);
    s_karim_id   := ensure_kanban_student('karim.tazi@uit.ac.ma', 'Karim Tazi', 'Université Ibn Tofail', 'Master 1, biology', 'Biology', ARRAY['Python', 'Lab work']);
    s_sara_id    := ensure_kanban_student('sara.lahlou@ensias.ma', 'Sara Lahlou', 'ENSIAS Rabat', 'Engineering', 'Bioinformatics & Machine Learning', ARRAY['Python', 'ML']);
    s_hamza_id   := ensure_kanban_student('hamza.kabbaj@usmba.ac.ma', 'Hamza Kabbaj', 'Université Sidi Mohamed Ben Abdellah', 'Master 2, biology', 'Biology & Single-Cell', ARRAY['R', 'scRNA-seq']);
    s_nour_id    := ensure_kanban_student('nour.amrani@inpt.ac.ma', 'Nour Amrani', 'INPT Rabat', 'Engineering, bioinformatics', 'Bioinformatics', ARRAY['Nextflow', 'Python']);
    s_oumaima_id := ensure_kanban_student('oumaima.sefrioui@usmba.ac.ma', 'Oumaima Sefrioui', 'Université Sidi Mohamed Ben Abdellah', 'Master 2, genomics', 'Genomics', ARRAY['R', 'Bioconductor']);
    
    -- Ensure Yassine Bennis has DESeq2 and Nextflow
    SELECT id INTO s_yassine_id FROM student_profiles WHERE full_name ILIKE '%Yassine Bennis%' LIMIT 1;
    IF s_yassine_id IS NOT NULL THEN
        INSERT INTO student_skills (student_id, skill_id)
        SELECT s_yassine_id, id FROM skills WHERE normalized_name IN ('deseq2', 'nextflow', 'python')
        ON CONFLICT DO NOTHING;
    END IF;

    -- Attach offer skills to primary offer
    IF primary_offer_id IS NOT NULL THEN
        INSERT INTO offer_skills (offer_id, skill_id, is_required)
        SELECT primary_offer_id, id, TRUE FROM skills WHERE normalized_name IN ('python', 'r', 'nextflow', 'deseq2', 'bioconductor')
        ON CONFLICT DO NOTHING;

        -- Stage 1: NEW (SENT, REVIEWED)
        INSERT INTO applications (id, student_id, offer_id, status, cover_letter, sent_at, last_activity_at)
        VALUES (uuid_generate_v4(), s_imane_id, primary_offer_id, 'SENT', 'Passionate about differential gene expression and biostatistics.', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP)
        ON CONFLICT (student_id, offer_id) DO UPDATE SET status = 'SENT', last_activity_at = CURRENT_TIMESTAMP;

        INSERT INTO applications (id, student_id, offer_id, status, cover_letter, sent_at, last_activity_at)
        VALUES (uuid_generate_v4(), s_karim_id, primary_offer_id, 'SENT', 'Master student with wet lab experience and Python analysis skills.', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP)
        ON CONFLICT (student_id, offer_id) DO UPDATE SET status = 'SENT', last_activity_at = CURRENT_TIMESTAMP;

        INSERT INTO applications (id, student_id, offer_id, status, cover_letter, sent_at, last_activity_at)
        VALUES (uuid_generate_v4(), s_sara_id, primary_offer_id, 'SENT', 'Engineering student applying machine learning models to biological datasets.', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP)
        ON CONFLICT (student_id, offer_id) DO UPDATE SET status = 'SENT', last_activity_at = CURRENT_TIMESTAMP;

        -- Stage 2: SHORTLISTED
        INSERT INTO applications (id, student_id, offer_id, status, cover_letter, sent_at, last_activity_at)
        VALUES (uuid_generate_v4(), s_hamza_id, primary_offer_id, 'SHORTLISTED', 'Experience in single-cell transcriptomics data and R pipelines.', CURRENT_TIMESTAMP - INTERVAL '4 days', CURRENT_TIMESTAMP)
        ON CONFLICT (student_id, offer_id) DO UPDATE SET status = 'SHORTLISTED', last_activity_at = CURRENT_TIMESTAMP;

        INSERT INTO applications (id, student_id, offer_id, status, cover_letter, sent_at, last_activity_at)
        VALUES (uuid_generate_v4(), s_nour_id, primary_offer_id, 'SHORTLISTED', 'Proficient with Nextflow reproducible pipelines and RNA-seq processing.', CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP)
        ON CONFLICT (student_id, offer_id) DO UPDATE SET status = 'SHORTLISTED', last_activity_at = CURRENT_TIMESTAMP;

        -- Stage 3: INTERVIEW
        IF s_yassine_id IS NOT NULL THEN
            INSERT INTO applications (id, student_id, offer_id, status, cover_letter, sent_at, last_activity_at)
            VALUES (uuid_generate_v4(), s_yassine_id, primary_offer_id, 'INTERVIEW', 'Dedicated data science student with background in biomedical imaging and genomics workflows.', CURRENT_TIMESTAMP - INTERVAL '6 days', CURRENT_TIMESTAMP)
            ON CONFLICT (student_id, offer_id) DO UPDATE SET status = 'INTERVIEW', last_activity_at = CURRENT_TIMESTAMP;
        END IF;

        -- Stage 4: DECISION
        INSERT INTO applications (id, student_id, offer_id, status, cover_letter, sent_at, last_activity_at)
        VALUES (uuid_generate_v4(), s_oumaima_id, primary_offer_id, 'DECISION_ACCEPTED', 'Master genomics candidate with strong background in Bioconductor and statistical genetics.', CURRENT_TIMESTAMP - INTERVAL '8 days', CURRENT_TIMESTAMP)
        ON CONFLICT (student_id, offer_id) DO UPDATE SET status = 'DECISION_ACCEPTED', last_activity_at = CURRENT_TIMESTAMP;
    END IF;
END $$;

DROP FUNCTION IF EXISTS ensure_kanban_student(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT[]);
