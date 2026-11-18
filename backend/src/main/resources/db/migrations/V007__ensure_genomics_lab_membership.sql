-- V007: Ensure Genomics Unit Lab exists and demo recruiter has active membership

-- 1. Ensure Genomics Unit exists
INSERT INTO labs (id, name, university_entity, city, domain, website, verified, status)
VALUES (
    'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
    'Genomics Unit',
    'Faculté de Médecine et de Pharmacie de Fès',
    'Fès',
    'Genomics & Bioinformatics',
    'http://genomics.univ-fes.ma',
    TRUE,
    'APPROVED'
)
ON CONFLICT (id) DO UPDATE SET 
    name = 'Genomics Unit',
    city = 'Fès',
    status = 'APPROVED', 
    verified = TRUE;

UPDATE labs SET status = 'APPROVED', verified = TRUE WHERE name ILIKE '%Genomics Unit%';

-- 2. Ensure s.elidrissi user exists if not already present
INSERT INTO users (id, email, password_hash, salt, role, email_verified_at, is_active)
SELECT 
    'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
    's.elidrissi@univ-fes.ma',
    'pbkdf2_sha256$600000$salt123$hash123',
    'salt123',
    'LAB',
    CURRENT_TIMESTAMP,
    TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 's.elidrissi@univ-fes.ma');

-- 3. Ensure lab_members links s.elidrissi@univ-fes.ma and lab@fitwell.ma to Genomics Unit
INSERT INTO lab_members (id, user_id, lab_id, role)
SELECT 
    uuid_generate_v4(),
    u.id,
    l.id,
    'CEO'
FROM users u
CROSS JOIN (
    SELECT id FROM labs WHERE name ILIKE '%Genomics Unit%' ORDER BY created_at ASC LIMIT 1
) l
WHERE u.email IN ('s.elidrissi@univ-fes.ma', 'lab@fitwell.ma')
  AND NOT EXISTS (
      SELECT 1 FROM lab_members lm WHERE lm.user_id = u.id AND lm.lab_id = l.id
  );
