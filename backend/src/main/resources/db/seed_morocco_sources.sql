-- 1. Ensure unique sources for Mifactory, UIT, DeepLeaf
INSERT INTO sources (id, name, url, type, status, accuracy_pct, last_run_at, cgu_url, is_allowed, robots_status)
VALUES 
  ('a1111111-1111-1111-1111-111111111111', 'Mifactory', 'https://mifactory.ma/carrieres', 'WEBSITE', 'ACTIVE', 96, CURRENT_TIMESTAMP, 'https://mifactory.ma/cgu', true, 'ALLOWED'),
  ('a2222222-2222-2222-2222-222222222222', 'Université Ibn Tofail', 'https://uit.ac.ma/stages', 'WEBSITE', 'ACTIVE', 98, CURRENT_TIMESTAMP, 'https://uit.ac.ma/mentions-legales', true, 'ALLOWED'),
  ('a3333333-3333-3333-3333-333333333333', 'DeepLeaf', 'https://deepleaf.io/careers', 'WEBSITE', 'ACTIVE', 95, CURRENT_TIMESTAMP, 'https://deepleaf.io/terms', true, 'ALLOWED')
ON CONFLICT (id) DO UPDATE SET 
  name = EXCLUDED.name,
  url = EXCLUDED.url,
  type = 'WEBSITE',
  status = 'ACTIVE',
  is_allowed = true;

-- 2. Insert Skills if not existing
INSERT INTO skills (id, name, normalized_name, category) VALUES
  (gen_random_uuid(), 'React', 'react', 'Tech'),
  (gen_random_uuid(), 'Spring Boot', 'spring-boot', 'Tech'),
  (gen_random_uuid(), 'JavaScript', 'javascript', 'Tech'),
  (gen_random_uuid(), 'Python', 'python', 'Tech'),
  (gen_random_uuid(), 'NLP', 'nlp', 'AI/ML'),
  (gen_random_uuid(), 'IA', 'ia', 'AI/ML'),
  (gen_random_uuid(), 'Machine Learning', 'machine-learning', 'AI/ML'),
  (gen_random_uuid(), 'PyTorch', 'pytorch', 'AI/ML'),
  (gen_random_uuid(), 'OpenCV', 'opencv', 'AI/ML'),
  (gen_random_uuid(), 'Computer Vision', 'computer-vision', 'AI/ML'),
  (gen_random_uuid(), 'Deep Learning', 'deep-learning', 'AI/ML')
ON CONFLICT DO NOTHING;

-- 3. Insert Sourced Offer 1: Mifactory
INSERT INTO offers (id, title, employer, domain, duration_months, start_date, location,
                    compensation_type, compensation_amount, description, tasks, places,
                    status, origin, source_id, source_url, content_hash, closes_at, fetched_at, created_at, updated_at)
VALUES (
  'b1111111-1111-1111-1111-111111111111',
  'Stage Développeur Full Stack React & Spring Boot',
  'Mifactory',
  'Développement Logiciel & Architecture Web',
  6,
  '2026-03-01',
  'Casablanca',
  'Funded',
  'Gratification 4000 DH/mois',
  'Conception et implémentation de microservices scalables et d''interfaces réactives pour les projets clients de la digital factory Mifactory.',
  ARRAY['Développement composants frontend React', 'Création d''APIs REST Spring Boot', 'Tests unitaires et intégration continue CI/CD'],
  2,
  'ACTIVE',
  'SOURCED',
  'a1111111-1111-1111-1111-111111111111',
  'https://mifactory.ma/carrieres/stage-fullstack-2026',
  'hash-mifactory-fullstack-real',
  '2027-02-28',
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title,
  employer = EXCLUDED.employer,
  source_id = EXCLUDED.source_id,
  source_url = EXCLUDED.source_url,
  status = 'ACTIVE',
  fetched_at = CURRENT_TIMESTAMP;

-- 4. Insert Sourced Offer 2: Université Ibn Tofail
INSERT INTO offers (id, title, employer, domain, duration_months, start_date, location,
                    compensation_type, compensation_amount, description, tasks, places,
                    status, origin, source_id, source_url, content_hash, closes_at, fetched_at, created_at, updated_at)
VALUES (
  'b2222222-2222-2222-2222-222222222222',
  'Stage Recherche IA & Traitement Automatique du Langage (TALN)',
  'Université Ibn Tofail',
  'Intelligence Artificielle & Traitement du Langage Naturel',
  4,
  '2026-03-01',
  'Kénitra',
  'Funded',
  'Gratification 3000 DH/mois',
  'Projet de recherche sur les modèles de langage et le traitement automatique des dialectes arabes et documents juridiques à l''Université Ibn Tofail.',
  ARRAY['Constitution et annotation de corpus bilingues', 'Entraînement de modèles Transformers HuggingFace', 'Évaluation quantitative des benchmarks'],
  1,
  'ACTIVE',
  'SOURCED',
  'a2222222-2222-2222-2222-222222222222',
  'https://uit.ac.ma/stages/recherche-taln-2026',
  'hash-uit-taln-real',
  '2027-03-31',
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title,
  employer = EXCLUDED.employer,
  source_id = EXCLUDED.source_id,
  source_url = EXCLUDED.source_url,
  status = 'ACTIVE',
  fetched_at = CURRENT_TIMESTAMP;

-- 5. Insert Sourced Offer 3: DeepLeaf
INSERT INTO offers (id, title, employer, domain, duration_months, start_date, location,
                    compensation_type, compensation_amount, description, tasks, places,
                    status, origin, source_id, source_url, content_hash, closes_at, fetched_at, created_at, updated_at)
VALUES (
  'b3333333-3333-3333-3333-333333333333',
  'Stage Computer Vision & Deep Learning pour l''AgriTech',
  'DeepLeaf',
  'Computer Vision, AgriTech & Deep Learning',
  5,
  '2026-03-01',
  'Rabat',
  'Funded',
  'Gratification 4500 DH/mois',
  'Développement et optimisation de modèles de vision par ordinateur embarquée pour le diagnostic phytosanitaire précoce des cultures agricoles chez DeepLeaf.',
  ARRAY['Entraînement de modèles de segmentation et détection (YOLO, U-Net)', 'Optimisation de modèles pour inférence Edge (ONNX, TensorRT)', 'Validation expérimentale sur le terrain'],
  2,
  'ACTIVE',
  'SOURCED',
  'a3333333-3333-3333-3333-333333333333',
  'https://deepleaf.io/careers/stage-cv-agritech-2026',
  'hash-deepleaf-agritech-real',
  '2027-03-15',
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title,
  employer = EXCLUDED.employer,
  source_id = EXCLUDED.source_id,
  source_url = EXCLUDED.source_url,
  status = 'ACTIVE',
  fetched_at = CURRENT_TIMESTAMP;

-- Attach skills
INSERT INTO offer_skills (offer_id, skill_id, is_required)
SELECT 'b1111111-1111-1111-1111-111111111111', id, true FROM skills WHERE normalized_name IN ('react', 'spring-boot', 'javascript')
ON CONFLICT DO NOTHING;

INSERT INTO offer_skills (offer_id, skill_id, is_required)
SELECT 'b2222222-2222-2222-2222-222222222222', id, true FROM skills WHERE normalized_name IN ('python', 'nlp', 'ia')
ON CONFLICT DO NOTHING;

INSERT INTO offer_skills (offer_id, skill_id, is_required)
SELECT 'b3333333-3333-3333-3333-333333333333', id, true FROM skills WHERE normalized_name IN ('python', 'pytorch', 'opencv', 'computer-vision')
ON CONFLICT DO NOTHING;
