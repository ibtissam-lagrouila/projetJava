-- V004: Clean fake sources, support DEMO origin, and enforce offer freshness rules

-- 1. Drop check constraint on offers.origin to allow 'DEMO', 'INTERNAL', 'LAB_POSTED', 'SOURCED'
ALTER TABLE offers DROP CONSTRAINT IF EXISTS offers_origin_check;
ALTER TABLE offers ADD CONSTRAINT offers_origin_check
    CHECK (origin IN ('LAB_POSTED', 'INTERNAL', 'SOURCED', 'DEMO'));

-- 2. Remove fake sources claiming to be LinkedIn or stagiaire.ma without connector
DELETE FROM validation_items WHERE source_id IN (
    SELECT id FROM sources WHERE name IN ('LinkedIn', 'stagiaire.ma')
);
DELETE FROM offers WHERE source_url ILIKE '%linkedin%' OR source_url ILIKE '%stagiaire%'
    OR compensation_amount ILIKE '%confirmé dans cet exemple%'
    OR title ILIKE '%confirmé dans cet exemple%';
DELETE FROM sources WHERE name IN ('LinkedIn', 'stagiaire.ma');

-- 3. Clean any fake labs
DELETE FROM labs WHERE name IN ('Laboratoire fictif', 'Entreprise fictive', 'Tech Solutions Maroc');

-- 4. Add index for liveness and freshness query optimization
CREATE INDEX IF NOT EXISTS idx_offers_origin_status ON offers(origin, status);
CREATE INDEX IF NOT EXISTS idx_offers_closes_at ON offers(closes_at);
CREATE INDEX IF NOT EXISTS idx_offers_fetched_at ON offers(fetched_at);
