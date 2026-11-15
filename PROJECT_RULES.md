# Fitwell — Project Rules & Guidelines

## 1. Core Principles
- **Target Platform**: Fitwell connects Moroccan university students and research laboratories for research internships.
- **Product Philosophy**: "AI PROPOSES, HUMAN DECIDES". Nothing is sent to a lab or student without explicit confirmation.
- **Strict Honesty & Calculation**: Zero hardcoded mock numbers in screens. Badges, counters, fit scores, and metrics are dynamically computed from DB state. Unavailable data displays "not available" or "no data yet" with an honest reason.
- **Privacy & Ethics**:
  - Full compliance with Moroccan Law 09-08 (data protection, account deletion, and data export).
  - Profile photo is hidden from labs until an application is submitted.
  - Compatibility score NEVER uses name, photo, gender, university, or geographic origin.
- **Workflow & Phases**:
  - Phase 1A: Monorepo, Docker, mini-framework, DB & migrations, auth (PBKDF2 600k, cookies HttpOnly SameSite=Strict), roles, backend security & authorization tests, dev seed.
  - Phase 1B: Frontend design tokens, theme, login page, 3 space shells with real dynamic counters.
  - Phases 2 to 7 proceed sequentially with a stop and validation after each phase.

## 2. Technical Stack & Architectural Constraints
- **Backend**: Pure **Java 21 SE** ONLY.
  - **STRICTLY FORBIDDEN**: Spring, Spring Boot, Jakarta EE / Java EE (servlets, JSP, EJB, JPA, JAX-RS, CDI), Hibernate. Enforced at build time via `maven-enforcer-plugin`.
  - **HTTP Server**: `com.sun.net.httpserver.HttpServer` with Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`).
  - **Database Access**: Pure JDBC (`java.sql.PreparedStatement` exclusively, zero string concatenation), DAO pattern.
  - **Mini-Framework**:
    - `Router`: Dynamic route matching with path params (`/api/offers/{id}`).
    - `Handler`: `(RequestContext ctx) -> HttpResponse`.
    - `MiddlewareChain`: Logging, central exception handling, security headers, rate limiting (trusted proxy IP), auth, role validation, payload size limit.
  - **Allowed Libraries**:
    - `org.postgresql:postgresql` (JDBC Driver)
    - `com.fasterxml.jackson.core:jackson-databind` + `jackson-datatype-jsr310` (JSON)
    - `org.apache.pdfbox:pdfbox` (PDF text extraction with page limit & timeout)
    - `org.jsoup:jsoup` (HTML parsing for sourcing with SSRF protection)
    - `org.slf4j:slf4j-api` & `ch.qos.logback:logback-classic` (Logging)
    - `org.junit.jupiter:junit-jupiter` & `org.mockito:mockito-core` (Unit tests)
    - `org.testcontainers:postgresql` (Integration testing)
  - **Build**: Docker build does not run tests (`-DskipTests`). Tests run via `mvn test` / `mvn verify`.

## 3. Database Schema Requirements
- `lab_members (id UUID, user_id FK, lab_id FK, role ENUM('HR', 'CEO', 'SUPERVISOR'), created_at)`: Replaces single user lab binding. Lab permissions are derived from lab membership.
- `skills (id UUID, name, normalized_name, category)`: Normalization (lowercase, trim, accent strip, synonyms). `student_skills` and `offer_skills` reference `skill_id`. `offer_skills` flags `is_required BOOLEAN`.
- `applications`: `UNIQUE(student_id, offer_id)`, `sent_at TIMESTAMP NULL`, paired with `application_events (id UUID, application_id FK, from_status, to_status, note, created_at)` for timeline & metrics.
- `threads (id UUID, application_id FK NULL, offer_id FK NULL, created_at)`, `thread_participants (thread_id, user_id)`, `messages (id UUID, thread_id FK, sender_id FK, content, read_at, created_at)`.
  - Drafts are NOT in `messages`. AI suggestions are NEVER persisted in DB.
- `interview_slots (id UUID, interview_id FK, slot_start, slot_end, is_chosen BOOLEAN, created_at)`.
- `extraction_runs`, `offer_views`, `password_reset_tokens`, `email_verifications`, `allowed_university_domains`.
- `claim_requests`: Linked to requesting user and claimed offers.
- `offers.content_hash`: SHA-256 of normalized title, lab, and description for duplicate prevention.

## 4. Security & Authorization Rules
- **Passwords**: `PBKDF2WithHmacSHA256` with at least 600,000 iterations.
  - Storage format: `pbkdf2$600000$salt$hash`.
  - Constant-time verification (`MessageDigest.isEqual`).
  - Identical generic error messages for login and forgot-password regardless of email existence.
  - All active sessions revoked upon password reset/change.
- **Sessions**:
  - SecureRandom 256-bit token stored SHA-256 hashed in database with expiration.
  - Preferred transport: `HttpOnly`, `SameSite=Strict`, `Secure` (in prod) cookie named `fitwell_session`. Support `Authorization: Bearer <token>` for API/testing.
- **Access Control**:
  - Dual-layer backend authorization: Middleware (role check) + Service layer (resource ownership check).
  - Return **404 Not Found** (NOT 403) when attempting to access another user's private resource (e.g. lab A accessing lab B's candidate) to prevent ID enumeration.
  - Application state machine: Only offer-owning lab can move application to `REVIEWED`, `SHORTLISTED`, `INTERVIEW`, `DECISION_*`. Student can only transition `DRAFT -> SENT` or withdraw.
- **File Uploads**:
  - Validate magic bytes (`%PDF-`), random UUID filename, stored in volume outside public web root, max 10 pages, timeout for PDFBox parsing.
- **SSRF Defense for Sourcing**:
  - Allow only `http` and `https`. Reject loopback (`127.0.0.1`), private ranges (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), link-local (`169.254.0.0/16`), and Docker internal subnets.
- **Data Protection (Moroccan Law 09-08)**:
  - Account deletion and complete data export endpoints.
  - AuditLog never stores passwords, tokens, or raw CV text.

## 5. Scoring & Matching Engine
- **Formula**:
  $$\text{Coverage}_{\text{demanded}} = \frac{\sum_{s \in S_{\text{owned}} \cap S_{\text{req}}} w(s) + \sum_{s \in S_{\text{owned}} \cap S_{\text{opt}}} w(s)}{\sum_{s \in S_{\text{req}}} w(s) + \sum_{s \in S_{\text{opt}}} w(s)}$$
  *(Strictly coverage of demanded skills, NOT Jaccard index).*
- Weighting: Required skills carry 2x or 3x the weight of optional skills.
- Labels:
  - $\ge 90\%$: Excellent match
  - $75\% - 89\%$: Strong match
  - $60\% - 74\%$: Good match
  - $< 60\%$: Moderate / Low match
- Explore query parser: Deterministic extraction of Moroccan cities (Rabat, Casablanca, Fès, Marrakech, Tangier, Oujda), duration ("4 months", "3 to 5 months"), and domains before falling back to optional AI.

## 6. Git Hygiene
- `.env`, `target/`, `node_modules/`, `uploads/`, `logs/` are strictly ignored.
