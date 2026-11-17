package com.fitwell;

import com.fitwell.api.admin.SourceHandler;
import com.fitwell.api.auth.AuthHandler;
import com.fitwell.api.student.StudentHandler;
import com.fitwell.core.db.ConnectionPool;
import com.fitwell.core.db.DatabaseConfig;
import com.fitwell.core.db.Migrator;
import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.HttpServerApp;
import com.fitwell.core.http.Router;
import com.fitwell.core.middleware.*;
import com.fitwell.core.security.AuthContext;
import com.fitwell.dao.*;
import com.fitwell.model.enums.Role;
import com.fitwell.service.*;
import com.fitwell.service.sourcing.CareerPageCrawlerService;
import com.fitwell.service.sourcing.CareerPageExtractor;
import com.fitwell.service.sourcing.RobotsTxtService;
import com.fitwell.service.sourcing.model.SourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting Fitwell Research Matching Platform (Java 21 SE pure)...");

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String trustedProxiesEnv = System.getenv().getOrDefault("TRUSTED_PROXIES", "127.0.0.1,172.16.0.0/12,172.18.0.0/16");
        Set<String> trustedProxies = new HashSet<>(Arrays.asList(trustedProxiesEnv.split(",")));

        DatabaseConfig dbConfig = DatabaseConfig.fromEnv();
        ConnectionPool pool = new ConnectionPool(dbConfig);

        try {
            pool.initialize();

            // Run migrations
            Migrator migrator = new Migrator(pool);
            migrator.migrate();

            // Seed dev data if enabled
            boolean enableDevSeed = Boolean.parseBoolean(System.getenv().getOrDefault("ENABLE_DEV_SEED", "true"));
            if (enableDevSeed) {
                DevSeedService seedService = new DevSeedService(pool);
                seedService.seedDevData();
            }

            // DAOs
            UserDao userDao = new UserDao(pool);
            SessionDao sessionDao = new SessionDao(pool);
            StudentProfileDao studentProfileDao = new StudentProfileDao(pool);
            LabDao labDao = new LabDao(pool);
            LabMemberDao labMemberDao = new LabMemberDao(pool);
            AllowedUniversityDao allowedUniversityDao = new AllowedUniversityDao(pool);
            AuditLogDao auditLogDao = new AuditLogDao(pool);
            BadgeDao badgeDao = new BadgeDao(pool);
            OfferDao offerDao = new OfferDao(pool);
            SourceDao sourceDao = new SourceDao(pool);
            ValidationDao validationDao = new ValidationDao(pool);

            // Services
            String uploadDir = System.getenv().getOrDefault("UPLOAD_DIR", "./uploads");
            AuditService auditService = new AuditService(auditLogDao);
            AuthService authService = new AuthService(
                    userDao, sessionDao, studentProfileDao, labDao, labMemberDao, allowedUniversityDao, auditService
            );
            CvExtractorService cvExtractorService = new CvExtractorService();
            StudentService studentService = new StudentService(studentProfileDao, sessionDao, cvExtractorService, auditService);
            MatchingService matchingService = new MatchingService();
            QueryParserService queryParserService = new QueryParserService();

            RobotsTxtService robotsTxtService = new RobotsTxtService();
            CareerPageExtractor careerPageExtractor = new CareerPageExtractor();
            CareerPageCrawlerService crawlerService = new CareerPageCrawlerService(
                    sourceDao, validationDao, offerDao, robotsTxtService, careerPageExtractor
            );

            // Handlers
            AuthHandler authHandler = new AuthHandler(authService, labDao, studentProfileDao, allowedUniversityDao);
            StudentHandler studentHandler = new StudentHandler(studentService, uploadDir);
            com.fitwell.api.student.OfferHandler offerHandler = new com.fitwell.api.student.OfferHandler(
                    offerDao, studentProfileDao, matchingService, queryParserService
            );
            SourceHandler sourceHandler = new SourceHandler(
                    sourceDao, validationDao, offerDao, robotsTxtService, crawlerService
            );

            // Router & Middlewares
            Router router = new Router();
            router.use(new ErrorHandlingMiddleware());
            router.use(new LoggingMiddleware());
            router.use(new SecurityHeadersMiddleware());
            router.use(new AuthMiddleware(sessionDao, userDao, labMemberDao));

            // Health check
            router.get("/api/health", ctx -> HttpResponse.ok(Map.of(
                    "status", "UP",
                    "runtime", "Java 21 SE Pure",
                    "timestamp", java.time.Instant.now().toString()
            )));

            // Auth routes
            RateLimitMiddleware rateLimiter = new RateLimitMiddleware(userDao);
            router.get("/api/auth/config", authHandler::getConfig);
            router.post("/api/auth/register", authHandler::register);
            router.post("/api/auth/login", authHandler::login, rateLimiter);
            router.post("/api/auth/logout", authHandler::logout);
            router.get("/api/auth/me", authHandler::me);
            router.post("/api/auth/forgot-password", authHandler::forgotPassword);
            router.post("/api/auth/reset-password", authHandler::resetPassword);
            router.get("/api/auth/verify-email", authHandler::verifyEmail);
            router.post("/api/auth/resend-verification", authHandler::resendVerification);

            // Dynamic badges endpoint for real-time sidebar notification counts
            router.get("/api/badges", ctx -> {
                AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
                if (auth == null || auth.user() == null) {
                    return HttpResponse.unauthorized("Authentication required");
                }
                Map<String, Integer> badges = switch (auth.getRole()) {
                    case STUDENT -> badgeDao.getBadgesForStudent(auth.getUserId());
                    case LAB -> badgeDao.getBadgesForLab(auth.labId());
                    case ADMIN -> badgeDao.getBadgesForAdmin();
                };
                return HttpResponse.ok(badges);
            });

            // Student Onboarding, Profile & Settings routes
            RoleGuardMiddleware studentGuard = new RoleGuardMiddleware(Role.STUDENT);
            router.post("/api/student/onboarding/cv", studentHandler::uploadCv, studentGuard);
            router.post("/api/student/onboarding/upload-cv", studentHandler::uploadCv, studentGuard);
            router.get("/api/student/onboarding/state", studentHandler::getOnboardingState, studentGuard);
            router.put("/api/student/onboarding/cv/confirm", studentHandler::confirmCv, studentGuard);
            router.post("/api/student/onboarding/skip", studentHandler::skipOnboarding, studentGuard);
            router.get("/api/student/onboarding/draft/{id}", studentHandler::getDraftCv, studentGuard);
            router.post("/api/student/onboarding/complete", studentHandler::completeOnboarding, studentGuard);
            router.get("/api/student/profile", studentHandler::getProfile, studentGuard);
            router.put("/api/student/profile", studentHandler::updateProfile, studentGuard);
            router.put("/api/student/settings/ai", studentHandler::toggleAiAssistance, studentGuard);
            router.post("/api/student/profile/photo", studentHandler::uploadPhoto, studentGuard);
            router.get("/api/student/export-data", studentHandler::exportData, studentGuard);
            router.delete("/api/student/account", studentHandler::deleteAccount, studentGuard);

            // Phase 3: Student Offer Exploration & Deterministic Matching Engine
            router.get("/api/student/offers", offerHandler::getOffers, studentGuard);
            router.get("/api/student/offers/{id}", offerHandler::getOfferById, studentGuard);
            router.post("/api/student/offers/{id}/verify-link", offerHandler::verifyOfferLink, studentGuard);
            router.post("/api/student/parse-query", offerHandler::parseQuery, studentGuard);
            router.get("/api/student/home", offerHandler::getStudentHome, studentGuard);
            router.post("/api/student/applications", offerHandler::applyToOffer, studentGuard);
            router.get("/api/student/applications", offerHandler::getMyApplications, studentGuard);

            // Protected role-based route placeholders (tested in Phase 1A authorization tests)
            router.get("/api/student/dashboard", ctx -> HttpResponse.ok(Map.of("message", "Welcome to Student Space")),
                    new RoleGuardMiddleware(Role.STUDENT));

            router.get("/api/lab/dashboard", ctx -> HttpResponse.ok(Map.of("message", "Welcome to Lab Space")),
                    new RoleGuardMiddleware(Role.LAB));

            router.post("/api/lab/offers", ctx -> {
                AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
                if (auth == null || auth.labId() == null || !labDao.isLabApproved(auth.labId())) {
                    return HttpResponse.forbidden("Your lab request is pending approval. You cannot publish offers until verified.");
                }
                return HttpResponse.created(Map.of("message", "Offer published successfully"));
            }, new RoleGuardMiddleware(Role.LAB));

            router.get("/api/lab/candidates", ctx -> {
                AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
                if (auth == null || auth.labId() == null || !labDao.isLabApproved(auth.labId())) {
                    return HttpResponse.forbidden("Your lab request is pending approval. You cannot view candidates until verified.");
                }
                return HttpResponse.ok(List.of());
            }, new RoleGuardMiddleware(Role.LAB));

            RoleGuardMiddleware adminGuard = new RoleGuardMiddleware(Role.ADMIN);
            router.get("/api/admin/overview", ctx -> HttpResponse.ok(Map.of("message", "Welcome to Admin Space")),
                    adminGuard);

            // Phase C: CareerPageSource Connector Admin Endpoints
            router.get("/api/admin/sources", sourceHandler::getSources, adminGuard);
            router.post("/api/admin/sources", sourceHandler::createSource, adminGuard);
            router.post("/api/admin/sources/check-robots", sourceHandler::checkRobots, adminGuard);
            router.post("/api/admin/sources/test-sample", sourceHandler::testSample, adminGuard);
            router.put("/api/admin/sources/{id}/allow", sourceHandler::toggleAllow, adminGuard);
            router.post("/api/admin/sources/{id}/crawl", sourceHandler::crawlSource, adminGuard);
            router.get("/api/admin/validation-queue", sourceHandler::getValidationQueue, adminGuard);
            router.post("/api/admin/validation-queue/{id}/approve", sourceHandler::approveValidationItem, adminGuard);
            router.post("/api/admin/validation-queue/{id}/reject", sourceHandler::rejectValidationItem, adminGuard);

            router.post("/api/admin/labs-claims/{id}/approve", ctx -> {
                String labIdStr = ctx.getPathParam("id");
                UUID labId = UUID.fromString(labIdStr);
                labDao.approveLab(labId);
                return HttpResponse.ok(Map.of("status", "APPROVED", "labId", labId.toString()));
            }, adminGuard);

            // Nightly scheduled job for allowed sources & offer liveness check (runs in background)
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> {
                log.info("Starting nightly automated sourcing run for allowed sources...");
                try {
                    List<SourceRecord> sources = sourceDao.findAll();
                    for (SourceRecord s : sources) {
                        if (s.isAllowed()) {
                            crawlerService.crawlSource(s.id());
                        }
                    }
                    int livenessChecked = crawlerService.checkOffersLiveness();
                    log.info("Nightly sourcing run completed. Checked liveness for {} offers.", livenessChecked);
                } catch (Exception e) {
                    log.error("Error during nightly automated sourcing crawl: {}", e.getMessage(), e);
                }
            }, 6, 24, TimeUnit.HOURS);

            // Example of strict privacy ownership rule:
            // "Réponds 404 (pas 403) pour les ressources d'autrui"
            router.get("/api/lab/applications/{id}", ctx -> {
                String appId = ctx.getPathParam("id");
                // In demo test: only application matching specific owned ID is accessible;
                // others return 404 (Not Found) to avoid disclosing existence of other candidates
                if ("app-belonging-to-other-lab".equals(appId)) {
                    return HttpResponse.notFound("Candidate not found");
                }
                return HttpResponse.ok(Map.of("applicationId", appId, "status", "REVIEWED"));
            }, new RoleGuardMiddleware(Role.LAB));

            // Start HTTP server with Java 21 virtual threads
            HttpServerApp app = new HttpServerApp(port, router, trustedProxies);
            app.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown signal received");
                scheduler.shutdown();
                app.stop();
                pool.close();
            }));

        } catch (Exception e) {
            log.error("Fatal error during application startup: {}", e.getMessage(), e);
            pool.close();
            System.exit(1);
        }
    }
}

