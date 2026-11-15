-- V001: Initial Complete Schema for Fitwell Platform

-- Ensure UUID generation extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Schema migration history tracking
CREATE TABLE IF NOT EXISTS schema_version (
    version INT PRIMARY KEY,
    script VARCHAR(255) NOT NULL,
    installed_on TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 1. Allowed University Domains (Moroccan Universities)
CREATE TABLE IF NOT EXISTS allowed_university_domains (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    domain VARCHAR(100) UNIQUE NOT NULL,
    university_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Users (Student, Lab, Admin)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT', 'LAB', 'ADMIN')),
    email_verified_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- 3. Sessions (SecureRandom 256-bit token SHA-256 hashed)
CREATE TABLE IF NOT EXISTS sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) UNIQUE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sessions_token_hash ON sessions(token_hash);
CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);

-- 4. Login Attempts (Rate limiting tracking)
CREATE TABLE IF NOT EXISTS login_attempts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    attempted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    success BOOLEAN NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_login_attempts_ip_email ON login_attempts(ip_address, email, attempted_at);

-- 5. Email Verifications & Password Reset Tokens
CREATE TABLE IF NOT EXISTS email_verifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) UNIQUE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) UNIQUE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. Labs
CREATE TABLE IF NOT EXISTS labs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    university_entity VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    domain VARCHAR(255) NOT NULL,
    website VARCHAR(255),
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 7. Lab Members (Replaces single user binding; grants access via membership)
CREATE TABLE IF NOT EXISTS lab_members (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    lab_id UUID NOT NULL REFERENCES labs(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('HR', 'CEO', 'SUPERVISOR')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_lab UNIQUE (user_id, lab_id)
);
CREATE INDEX IF NOT EXISTS idx_lab_members_user ON lab_members(user_id);
CREATE INDEX IF NOT EXISTS idx_lab_members_lab ON lab_members(lab_id);

-- 8. Skills taxonomy with normalization
CREATE TABLE IF NOT EXISTS skills (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) UNIQUE NOT NULL,
    category VARCHAR(100) NOT NULL DEFAULT 'General',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_skills_normalized ON skills(normalized_name);

-- 9. Student Profiles
CREATE TABLE IF NOT EXISTS student_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    full_name VARCHAR(255) NOT NULL,
    university VARCHAR(255) NOT NULL,
    level VARCHAR(100) NOT NULL,
    languages TEXT[] DEFAULT '{}',
    interests TEXT[] DEFAULT '{}',
    availability_start DATE,
    availability_duration_months INT DEFAULT 4,
    photo_path VARCHAR(255),
    ai_assistance_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 10. Student Skills (Foreign Key to normalized skills)
CREATE TABLE IF NOT EXISTS student_skills (
    student_id UUID NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    level VARCHAR(50) DEFAULT 'Intermediate',
    PRIMARY KEY (student_id, skill_id)
);

-- 11. Student Projects
CREATE TABLE IF NOT EXISTS student_projects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    student_id UUID NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    skills TEXT[] DEFAULT '{}',
    result_summary TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 12. Uploaded CVs (Stored outside web root)
CREATE TABLE IF NOT EXISTS cvs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    student_id UUID NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    raw_text TEXT,
    parsed_json JSONB,
    is_master BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 13. External Sourcing Sources
CREATE TABLE IF NOT EXISTS sources (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    url VARCHAR(500) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('WEBSITE', 'RSS', 'POSTED')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('HEALTHY', 'WARNING', 'FAILING')),
    accuracy_pct INT DEFAULT 100,
    last_run_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 14. Extraction Runs (Sourcing run logs)
CREATE TABLE IF NOT EXISTS extraction_runs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_id UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS', 'WARNING', 'FAILING')),
    items_found INT DEFAULT 0,
    error_message TEXT
);

-- 15. Offers (With content_hash for duplicate prevention)
CREATE TABLE IF NOT EXISTS offers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    lab_id UUID REFERENCES labs(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    domain VARCHAR(255) NOT NULL,
    duration_months INT NOT NULL DEFAULT 4,
    start_date DATE,
    location VARCHAR(255) NOT NULL,
    compensation_type VARCHAR(50) DEFAULT 'Funded',
    compensation_amount VARCHAR(100),
    description TEXT NOT NULL,
    tasks TEXT[] DEFAULT '{}',
    places INT DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    origin VARCHAR(20) NOT NULL DEFAULT 'LAB_POSTED' CHECK (origin IN ('LAB_POSTED', 'SOURCED')),
    source_id UUID REFERENCES sources(id) ON DELETE SET NULL,
    source_url VARCHAR(500),
    content_hash VARCHAR(64) NOT NULL,
    closes_at DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_offers_status ON offers(status);
CREATE INDEX IF NOT EXISTS idx_offers_lab ON offers(lab_id);
CREATE INDEX IF NOT EXISTS idx_offers_content_hash ON offers(content_hash);

-- 16. Offer Skills (Normalized with is_required flag)
CREATE TABLE IF NOT EXISTS offer_skills (
    offer_id UUID NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (offer_id, skill_id)
);

-- 17. Offer Views Tracking
CREATE TABLE IF NOT EXISTS offer_views (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    offer_id UUID NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
    student_id UUID REFERENCES student_profiles(id) ON DELETE SET NULL,
    viewed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ip_hash VARCHAR(64)
);

-- 18. Tailored CVs
CREATE TABLE IF NOT EXISTS tailored_cvs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    student_id UUID NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
    offer_id UUID NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
    tailored_text TEXT NOT NULL,
    diff_suggestions_json JSONB,
    cv_score INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 19. Applications (UNIQUE student_id, offer_id)
CREATE TABLE IF NOT EXISTS applications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    student_id UUID NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
    offer_id UUID NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' CHECK (
        status IN ('DRAFT', 'SENT', 'REVIEWED', 'SHORTLISTED', 'INTERVIEW', 'DECISION_ACCEPTED', 'DECISION_REJECTED', 'WITHDRAWN')
    ),
    cover_letter TEXT,
    documents_json JSONB DEFAULT '[]'::jsonb,
    next_step_calc VARCHAR(255),
    sent_at TIMESTAMP WITH TIME ZONE,
    last_activity_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_student_offer_application UNIQUE (student_id, offer_id)
);
CREATE INDEX IF NOT EXISTS idx_apps_student ON applications(student_id);
CREATE INDEX IF NOT EXISTS idx_apps_offer ON applications(offer_id);
CREATE INDEX IF NOT EXISTS idx_apps_status ON applications(status);

-- 20. Application Events (Timeline & History)
CREATE TABLE IF NOT EXISTS application_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    note TEXT,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_app_events ON application_events(application_id, created_at);

-- 21. Interviews & Time Slots
CREATE TABLE IF NOT EXISTS interviews (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    scheduled_time TIMESTAMP WITH TIME ZONE,
    location_or_link VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED' CHECK (status IN ('PROPOSED', 'CONFIRMED', 'CANCELLED')),
    preparation_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_slots (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    interview_id UUID NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    slot_start TIMESTAMP WITH TIME ZONE NOT NULL,
    slot_end TIMESTAMP WITH TIME ZONE NOT NULL,
    is_chosen BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 22. Messaging (Threads & Messages; drafts and AI suggestions NOT stored here)
CREATE TABLE IF NOT EXISTS threads (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    application_id UUID REFERENCES applications(id) ON DELETE SET NULL,
    offer_id UUID REFERENCES offers(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS thread_participants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    thread_id UUID NOT NULL REFERENCES threads(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_thread_user UNIQUE (thread_id, user_id)
);

CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    thread_id UUID NOT NULL REFERENCES threads(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_messages_thread ON messages(thread_id, created_at);

-- 23. Validation Queue (Admin)
CREATE TABLE IF NOT EXISTS validation_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_id UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    raw_page_text TEXT NOT NULL,
    extracted_fields_json JSONB NOT NULL,
    confidence_score INT NOT NULL,
    duplicate_of_offer_id UUID REFERENCES offers(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_validation_status ON validation_items(status);

-- 24. Claim Requests (Labs claiming sourced offers)
CREATE TABLE IF NOT EXISTS claim_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    lab_id UUID NOT NULL REFERENCES labs(id) ON DELETE CASCADE,
    requester_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requester_email VARCHAR(255) NOT NULL,
    domain_matches BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS claim_request_offers (
    claim_request_id UUID NOT NULL REFERENCES claim_requests(id) ON DELETE CASCADE,
    offer_id UUID NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
    PRIMARY KEY (claim_request_id, offer_id)
);

-- 25. Platform Rules (Admin configurable publication & verification thresholds)
CREATE TABLE IF NOT EXISTS platform_rules (
    rule_key VARCHAR(50) PRIMARY KEY,
    rule_value TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 26. Audit Logs (Sensitive actions, NEVER containing passwords, tokens, or raw CVs)
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(100),
    details_json JSONB,
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action, created_at);

