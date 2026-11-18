-- V006: Lab Dashboard, Profile Snapshots, and Performance Indexes

-- 1. Add status and rejection_reason to labs if not present
ALTER TABLE labs ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'APPROVED' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));
UPDATE labs SET status = CASE WHEN verified THEN 'APPROVED' ELSE 'PENDING' END WHERE status IS NULL;

-- 2. Add profile_snapshot_json to applications for frozen profile score calculation
ALTER TABLE applications ADD COLUMN IF NOT EXISTS profile_snapshot_json JSONB;

-- 3. Ensure offer_views table exists and has proper schema
CREATE TABLE IF NOT EXISTS offer_views (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    offer_id UUID NOT NULL REFERENCES offers(id) ON DELETE CASCADE,
    student_id UUID REFERENCES student_profiles(id) ON DELETE SET NULL,
    viewed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ip_hash VARCHAR(64)
);

-- 4. Performance Indexes for p95 < 500ms dashboard queries
CREATE INDEX IF NOT EXISTS idx_offers_lab_status ON offers(lab_id, status);
CREATE INDEX IF NOT EXISTS idx_apps_offer_status ON applications(offer_id, status);
CREATE INDEX IF NOT EXISTS idx_apps_sent_at ON applications(sent_at);
CREATE INDEX IF NOT EXISTS idx_offer_views_offer_viewed ON offer_views(offer_id, viewed_at);
CREATE INDEX IF NOT EXISTS idx_interviews_scheduled ON interviews(scheduled_time);
CREATE INDEX IF NOT EXISTS idx_interviews_app_status ON interviews(application_id, status);
CREATE INDEX IF NOT EXISTS idx_app_events_status_created ON application_events(to_status, created_at);
CREATE INDEX IF NOT EXISTS idx_lab_members_user_lab ON lab_members(user_id, lab_id);
