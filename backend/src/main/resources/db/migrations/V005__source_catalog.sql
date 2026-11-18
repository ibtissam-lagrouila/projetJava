-- V005: Source Catalog & Access Modes (INTERNAL, API, DEEPLINK, CAREER_PAGE, REMOTE_FEED)

-- 1. Ensure required columns exist on sources
ALTER TABLE sources ADD COLUMN IF NOT EXISTS domain VARCHAR(255);
ALTER TABLE sources ADD COLUMN IF NOT EXISTS access_type VARCHAR(50) DEFAULT 'CAREER_PAGE';
ALTER TABLE sources ADD COLUMN IF NOT EXISTS tos_url VARCHAR(500);
ALTER TABLE sources ADD COLUMN IF NOT EXISTS search_url_template VARCHAR(1000);
ALTER TABLE sources ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE sources ALTER COLUMN type DROP NOT NULL;
ALTER TABLE sources DROP CONSTRAINT IF EXISTS sources_type_check;

-- Synchronize cgu_url and tos_url if either is present
UPDATE sources SET tos_url = cgu_url WHERE tos_url IS NULL AND cgu_url IS NOT NULL;
UPDATE sources SET cgu_url = tos_url WHERE cgu_url IS NULL AND tos_url IS NOT NULL;

-- 2. Update existing Moroccan demo/real sources from previous migrations
UPDATE sources 
SET domain = 'mifactory.ma', 
    access_type = 'CAREER_PAGE',
    type = 'CAREER_PAGE',
    tos_url = COALESCE(tos_url, 'https://mifactory.ma/cgu'),
    cgu_url = COALESCE(cgu_url, 'https://mifactory.ma/cgu')
WHERE id = 'a1111111-1111-1111-1111-111111111111' OR name = 'Mifactory';

UPDATE sources 
SET domain = 'uit.ac.ma', 
    access_type = 'CAREER_PAGE',
    type = 'CAREER_PAGE',
    tos_url = COALESCE(tos_url, 'https://uit.ac.ma/mentions-legales'),
    cgu_url = COALESCE(cgu_url, 'https://uit.ac.ma/mentions-legales')
WHERE id = 'a2222222-2222-2222-2222-222222222222' OR name = 'Université Ibn Tofail';

UPDATE sources 
SET domain = 'deepleaf.io', 
    access_type = 'CAREER_PAGE',
    type = 'CAREER_PAGE',
    tos_url = COALESCE(tos_url, 'https://deepleaf.io/terms'),
    cgu_url = COALESCE(cgu_url, 'https://deepleaf.io/terms')
WHERE id = 'a3333333-3333-3333-3333-333333333333' OR name = 'DeepLeaf';

-- 3. Insert Catalog Sources (Idempotent: checked by name or domain)

-- 3.1 INTERNAL: Fitwell labs and companies (Primary source, always active)
INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Fitwell labs and companies', 'fitwell.ma', 'INTERNAL', 'INTERNAL', 'https://fitwell.ma', true, 'ACTIVE', 100,
       'Platform internal postings published directly by partner labs, universities, and companies.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'fitwell.ma' OR name = 'Fitwell labs and companies');

-- 3.2 API: Jooble (Allowed=false by default, server-side key only)
INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Jooble', 'jooble.org', 'API', 'API', 'https://jooble.org', false, 'PENDING', 90,
       'Morocco coverage not confirmed. Server-side JOOBLE_API_KEY required.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'jooble.org' OR name = 'Jooble');

-- 3.3 DEEPLINK: Search outbound links only (Zero crawling, allowed=true)
-- 1. LinkedIn Jobs
INSERT INTO sources (id, name, domain, access_type, type, url, search_url_template, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'LinkedIn Jobs', 'linkedin.com', 'DEEPLINK', 'DEEPLINK', 'https://www.linkedin.com/jobs',
       'https://www.linkedin.com/jobs/search/?keywords={keywords}&location={city}', true, 'ACTIVE', 100,
       'Direct outbound search link. Zero crawling/scraping.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'linkedin.com' OR name = 'LinkedIn Jobs');

-- 2. Rekrute
INSERT INTO sources (id, name, domain, access_type, type, url, search_url_template, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Rekrute', 'rekrute.com', 'DEEPLINK', 'DEEPLINK', 'https://www.rekrute.com',
       'https://www.rekrute.com/offres-emploi-maroc.html?keyword={keywords}&region={city}', true, 'ACTIVE', 100,
       'Outbound search link for Moroccan opportunities.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'rekrute.com' OR name = 'Rekrute');

-- 3. Emploi.ma
INSERT INTO sources (id, name, domain, access_type, type, url, search_url_template, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Emploi.ma', 'emploi.ma', 'DEEPLINK', 'DEEPLINK', 'https://www.emploi.ma',
       'https://www.emploi.ma/recherche-jobs-maroc?motcle={keywords}&f%5B0%5D=im_field_offre_region%3A{city}', true, 'ACTIVE', 100,
       'Outbound search link for Morocco jobs portal.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'emploi.ma' OR name = 'Emploi.ma');

-- 4. Stagiaires.ma
INSERT INTO sources (id, name, domain, access_type, type, url, search_url_template, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Stagiaires.ma', 'stagiaires.ma', 'DEEPLINK', 'DEEPLINK', 'https://www.stagiaires.ma',
       'https://www.stagiaires.ma/offres-stages?q={keywords}&ville={city}', true, 'ACTIVE', 100,
       'Outbound search link dedicated to internships in Morocco.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'stagiaires.ma' OR name = 'Stagiaires.ma');

-- 5. ANAPEC (Unverified template -> fallback to site URL, notes marked)
INSERT INTO sources (id, name, domain, access_type, type, url, search_url_template, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'ANAPEC', 'anapec.org', 'DEEPLINK', 'DEEPLINK', 'http://www.anapec.org',
       '', true, 'ACTIVE', 100,
       'template to verify', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'anapec.org' OR name = 'ANAPEC');

-- 6. Emploi-public.ma (Unverified template -> fallback to site URL, notes marked)
INSERT INTO sources (id, name, domain, access_type, type, url, search_url_template, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Emploi-public.ma', 'emploi-public.ma', 'DEEPLINK', 'DEEPLINK', 'https://www.emploi-public.ma',
       '', true, 'ACTIVE', 100,
       'template to verify', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'emploi-public.ma' OR name = 'Emploi-public.ma');

-- 7. Bayt
INSERT INTO sources (id, name, domain, access_type, type, url, search_url_template, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Bayt', 'bayt.com', 'DEEPLINK', 'DEEPLINK', 'https://www.bayt.com/en/morocco/',
       'https://www.bayt.com/en/morocco/jobs/?q={keywords}&loc={city}', true, 'ACTIVE', 100,
       'Outbound search link for Bayt Morocco portal.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'bayt.com' OR name = 'Bayt');

-- 8. Indeed Maroc
INSERT INTO sources (id, name, domain, access_type, type, url, search_url_template, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Indeed Maroc', 'ma.indeed.com', 'DEEPLINK', 'DEEPLINK', 'https://ma.indeed.com',
       'https://ma.indeed.com/jobs?q={keywords}&l={city}', true, 'ACTIVE', 100,
       'Outbound search link for Indeed Morocco.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'ma.indeed.com' OR name = 'Indeed Maroc');

-- 9. Jobsquare (Unverified template -> fallback to site URL, notes marked)
INSERT INTO sources (id, name, domain, access_type, type, url, search_url_template, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Jobsquare', 'jobsquare.ma', 'DEEPLINK', 'DEEPLINK', 'https://jobsquare.ma',
       '', true, 'ACTIVE', 100,
       'template to verify', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'jobsquare.ma' OR name = 'Jobsquare');

-- 10. Optioncarriere
INSERT INTO sources (id, name, domain, access_type, type, url, search_url_template, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Optioncarriere', 'optioncarriere.ma', 'DEEPLINK', 'DEEPLINK', 'https://www.optioncarriere.ma',
       'https://www.optioncarriere.ma/emploi?s={keywords}&l={city}', true, 'ACTIVE', 100,
       'Outbound search link for Optioncarriere Morocco.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'optioncarriere.ma' OR name = 'Optioncarriere');

-- 11. Dreamjob (Blog republishing offers - search link only, never crawl)
INSERT INTO sources (id, name, domain, access_type, type, url, search_url_template, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Dreamjob', 'dreamjob.ma', 'DEEPLINK', 'DEEPLINK', 'https://dreamjob.ma',
       'https://dreamjob.ma/?s={keywords}', true, 'ACTIVE', 100,
       'Blog republishing offers - search link only, never crawl', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'dreamjob.ma' OR name = 'Dreamjob');

-- 3.4 CAREER_PAGE: Moroccan companies & institutions (allowed=false by default)
-- Companies
INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'OCP Group Careers', 'jobs.ocpgroup.ma', 'CAREER_PAGE', 'CAREER_PAGE', 'https://jobs.ocpgroup.ma', false, 'PENDING', 95,
       'OCP Group career portal. Requires ToS URL & robots check before activation.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'jobs.ocpgroup.ma' OR name = 'OCP Group Careers');

INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Orange Maroc', 'orange.jobs', 'CAREER_PAGE', 'CAREER_PAGE', 'https://orange.jobs/jobs/v2/search/search-jobs.html?country=Morocco', false, 'PENDING', 95,
       'Orange Morocco career portal. Requires ToS URL & robots check before activation.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'orange.jobs' OR name = 'Orange Maroc');

INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Inwi Carrières', 'carrieres.inwi.ma', 'CAREER_PAGE', 'CAREER_PAGE', 'https://carrieres.inwi.ma', false, 'PENDING', 95,
       'Inwi telecom career portal. Requires ToS URL & robots check before activation.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'carrieres.inwi.ma' OR name = 'Inwi Carrières');

INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Maroc Telecom', 'iam.ma', 'CAREER_PAGE', 'CAREER_PAGE', 'https://www.iam.ma/groupe-maroc-telecom/recrutement.aspx', false, 'PENDING', 95,
       'Maroc Telecom recruitment page. Requires ToS URL & robots check before activation.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'iam.ma' OR name = 'Maroc Telecom');

INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Attijariwafa bank', 'recrutement.attijariwafa.com', 'CAREER_PAGE', 'CAREER_PAGE', 'https://recrutement.attijariwafa.com', false, 'PENDING', 95,
       'Attijariwafa bank recruitment portal. Requires ToS URL & robots check before activation.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'recrutement.attijariwafa.com' OR name = 'Attijariwafa bank');

INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Sofrecom Maroc', 'carrieres.sofrecom.com', 'CAREER_PAGE', 'CAREER_PAGE', 'https://carrieres.sofrecom.com/maroc', false, 'PENDING', 95,
       'Sofrecom Orange Group IT portal. Requires ToS URL & robots check before activation.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'carrieres.sofrecom.com' OR name = 'Sofrecom Maroc');

INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Capgemini Maroc', 'capgemini.com', 'CAREER_PAGE', 'CAREER_PAGE', 'https://www.capgemini.com/careers', false, 'PENDING', 95,
       'Capgemini engineering & cloud portal. Requires ToS URL & robots check before activation.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'capgemini.com' OR name = 'Capgemini Maroc');

INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Renault Group Maroc', 'renaultgroup.com', 'CAREER_PAGE', 'CAREER_PAGE', 'https://www.renaultgroup.com/carrieres', false, 'PENDING', 95,
       'Renault Group Tangier/Casablanca. Requires ToS URL & robots check before activation.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'renaultgroup.com' OR name = 'Renault Group Maroc');

-- Institutions & Universities
INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'UM6P Careers', 'um6p.ma', 'CAREER_PAGE', 'CAREER_PAGE', 'https://um6p.ma/en/careers', false, 'PENDING', 95,
       'UM6P research and teaching career openings. Requires ToS URL & robots check before activation.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'um6p.ma' OR name = 'UM6P Careers');

INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'ENSIAS', 'ensias.um5.ac.ma', 'CAREER_PAGE', 'CAREER_PAGE', 'http://ensias.um5.ac.ma', false, 'PENDING', 95,
       'ENSIAS engineering school opportunities. Requires ToS URL & robots check before activation.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'ensias.um5.ac.ma' OR name = 'ENSIAS');

INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'INPT', 'inpt.ac.ma', 'CAREER_PAGE', 'CAREER_PAGE', 'https://www.inpt.ac.ma', false, 'PENDING', 95,
       'National Institute of Posts and Telecommunications. Requires ToS URL & robots check before activation.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'inpt.ac.ma' OR name = 'INPT');

INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'CNRST', 'cnrst.ma', 'CAREER_PAGE', 'CAREER_PAGE', 'https://www.cnrst.ma', false, 'PENDING', 95,
       'National Center for Scientific and Technical Research. Requires ToS URL & robots check before activation.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'cnrst.ma' OR name = 'CNRST');

INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'MAScIR', 'mascir.com', 'CAREER_PAGE', 'CAREER_PAGE', 'https://www.mascir.com', false, 'PENDING', 95,
       'Moroccan Foundation for Advanced Science, Innovation and Research. Requires ToS URL & robots check before activation.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'mascir.com' OR name = 'MAScIR');

-- 3.5 REMOTE_FEED: Optional remote job feeds (allowed=false by default)
INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'RemoteOK', 'remoteok.com', 'REMOTE_FEED', 'REMOTE_FEED', 'https://remoteok.com', false, 'PENDING', 90,
       'Remote tech internships feed. Terms allow display with source attribution.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'remoteok.com' OR name = 'RemoteOK');

INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Jobicy', 'jobicy.com', 'REMOTE_FEED', 'REMOTE_FEED', 'https://jobicy.com', false, 'PENDING', 90,
       'Remote work platform feed. Attribution required.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'jobicy.com' OR name = 'Jobicy');

INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'We Work Remotely', 'weworkremotely.com', 'REMOTE_FEED', 'REMOTE_FEED', 'https://weworkremotely.com', false, 'PENDING', 90,
       'Remote jobs feed. Attribution and link required.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'weworkremotely.com' OR name = 'We Work Remotely');

INSERT INTO sources (id, name, domain, access_type, type, url, is_allowed, status, accuracy_pct, notes, created_at)
SELECT gen_random_uuid(), 'Arbeitnow', 'arbeitnow.com', 'REMOTE_FEED', 'REMOTE_FEED', 'https://www.arbeitnow.com', false, 'PENDING', 90,
       'Job board with open API/feed. Attribution required.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE domain = 'arbeitnow.com' OR name = 'Arbeitnow');
