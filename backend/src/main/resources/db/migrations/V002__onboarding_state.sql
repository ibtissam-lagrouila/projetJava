-- V002: Add onboarding status flags to student_profiles
ALTER TABLE student_profiles ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN DEFAULT FALSE;
ALTER TABLE student_profiles ADD COLUMN IF NOT EXISTS onboarding_skipped BOOLEAN DEFAULT FALSE;

