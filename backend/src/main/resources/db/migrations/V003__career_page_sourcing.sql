-- V003: CareerPageSource (Phase C Sourcing Connector) & Validation Queue Enhancements

-- 1. Alter sources table
ALTER TABLE sources DROP CONSTRAINT IF EXISTS sources_status_check;
ALTER TABLE sources ADD COLUMN IF NOT EXISTS cgu_url VARCHAR(500);
ALTER TABLE sources ADD COLUMN IF NOT EXISTS is_allowed BOOLEAN DEFAULT FALSE;
ALTER TABLE sources ADD COLUMN IF NOT EXISTS robots_checked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE sources ADD COLUMN IF NOT EXISTS robots_status VARCHAR(50);
ALTER TABLE sources ADD COLUMN IF NOT EXISTS crawl_delay_seconds INT DEFAULT 5;
ALTER TABLE sources ADD COLUMN IF NOT EXISTS css_selectors JSONB;
ALTER TABLE sources ADD COLUMN IF NOT EXISTS auto_publish BOOLEAN DEFAULT FALSE;
ALTER TABLE sources ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(255);
ALTER TABLE sources ADD COLUMN IF NOT EXISTS alert_message VARCHAR(255);

-- 2. Alter offers table
ALTER TABLE offers DROP CONSTRAINT IF EXISTS offers_status_check;
ALTER TABLE offers ADD COLUMN IF NOT EXISTS excerpt TEXT;
ALTER TABLE offers ADD COLUMN IF NOT EXISTS canonical_url VARCHAR(500);
ALTER TABLE offers ADD COLUMN IF NOT EXISTS employer VARCHAR(255);
ALTER TABLE offers ADD COLUMN IF NOT EXISTS fetched_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE offers ADD COLUMN IF NOT EXISTS dead_since DATE;
ALTER TABLE offers ADD COLUMN IF NOT EXISTS validation_status VARCHAR(30) DEFAULT 'APPROVED';

-- Ensure indexing for fast lookups
CREATE INDEX IF NOT EXISTS idx_sources_allowed ON sources(is_allowed);
CREATE INDEX IF NOT EXISTS idx_offers_canonical_url ON offers(canonical_url);
CREATE INDEX IF NOT EXISTS idx_offers_dead_since ON offers(dead_since);
