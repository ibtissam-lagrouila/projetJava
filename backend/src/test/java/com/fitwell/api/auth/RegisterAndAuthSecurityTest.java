package com.fitwell.api.auth;

import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.HttpStatus;
import com.fitwell.core.http.RequestContext;
import com.fitwell.core.http.Router;
import com.fitwell.core.middleware.AuthMiddleware;
import com.fitwell.core.middleware.ErrorHandlingMiddleware;
import com.fitwell.core.middleware.RoleGuardMiddleware;
import com.fitwell.core.security.AuthContext;
import com.fitwell.core.security.PasswordHasher;
import com.fitwell.dao.*;
import com.fitwell.model.dto.AuthDtos.*;
import com.fitwell.model.entity.User;
import com.fitwell.model.enums.LabRole;
import com.fitwell.model.enums.Role;
import com.fitwell.service.AuditService;
import com.fitwell.service.AuthService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RegisterAndAuthSecurityTest {

    private UserDao userDao;
    private SessionDao sessionDao;
    private StudentProfileDao studentProfileDao;
    private LabDao labDao;
    private LabMemberDao labMemberDao;
    private AllowedUniversityDao allowedUniversityDao;
    private AuditService auditService;
    private AuthService authService;
    private AuthHandler authHandler;
    private Router router;

    @BeforeEach
    void setUp() throws SQLException {
        userDao = mock(UserDao.class);
        sessionDao = mock(SessionDao.class);
        studentProfileDao = mock(StudentProfileDao.class);
        labDao = mock(LabDao.class);
        labMemberDao = mock(LabMemberDao.class);
        allowedUniversityDao = mock(AllowedUniversityDao.class);
        auditService = mock(AuditService.class);

        authService = new AuthService(
                userDao, sessionDao, studentProfileDao, labDao, labMemberDao, allowedUniversityDao, auditService
        );
        authHandler = new AuthHandler(authService, labDao, studentProfileDao, allowedUniversityDao);

        router = new Router();
        router.use(new ErrorHandlingMiddleware());

        // Routes
        router.post("/api/auth/register", authHandler::register);
        router.post("/api/auth/login", authHandler::login);
        router.post("/api/auth/resend-verification", authHandler::resendVerification);

        router.post("/api/lab/offers", ctx -> {
            AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
            if (auth == null || auth.labId() == null || !labDao.isLabApproved(auth.labId())) {
                return HttpResponse.forbidden("Your lab request is pending approval. You cannot publish offers until verified.");
            }
            return HttpResponse.created(Map.of("message", "Offer published successfully"));
        }, new RoleGuardMiddleware(Role.LAB));

        router.post("/api/admin/labs-claims/{id}/approve", ctx -> {
            String labIdStr = ctx.getPathParam("id");
            UUID labId = UUID.fromString(labIdStr);
            labDao.approveLab(labId);
            return HttpResponse.ok(Map.of("status", "APPROVED", "labId", labId.toString()));
        }, new RoleGuardMiddleware(Role.ADMIN));
    }

    private RequestContext createMockContext(String method, String path, String jsonBody, AuthContext auth) {
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn(method);
        when(exchange.getRequestURI()).thenReturn(URI.create(path));
        Headers headers = new Headers();
        headers.set("Content-Type", "application/json");
        when(exchange.getRequestHeaders()).thenReturn(headers);
        when(exchange.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        if (jsonBody != null) {
            when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(jsonBody.getBytes(StandardCharsets.UTF_8)));
        } else {
            when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(new byte[0]));
        }

        RequestContext ctx = new RequestContext(exchange, Collections.emptyMap(), Collections.singleton("127.0.0.1"));
        if (auth != null) {
            ctx.setAttribute(AuthMiddleware.AUTH_ATTRIBUTE, auth);
        }
        return ctx;
    }

    @Test
    @DisplayName("1. A gmail.com email is accepted for Student, HR and CEO registration")
    void gmailAcceptedForStudentHrAndCeo() throws Exception {
        when(userDao.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userDao.countRecentRegistrations(anyString(), anyString(), anyInt())).thenReturn(0);

        UUID uid1 = UUID.randomUUID();
        when(userDao.createUser(eq("student.test@gmail.com"), anyString(), anyString(), eq(Role.STUDENT), anyBoolean()))
                .thenReturn(new User(uid1, "student.test@gmail.com", "h", "s", Role.STUDENT, null, true, Instant.now(), Instant.now()));

        // Student with gmail
        String studentBody = """
            {
                "accountType": "STUDENT",
                "name": "Karim Gmail",
                "email": "student.test@gmail.com",
                "password": "SecurePassword123!",
                "privacyPolicyAccepted": true
            }
        """;
        RequestContext ctx1 = createMockContext("POST", "/api/auth/register", studentBody, null);
        HttpResponse res1 = router.dispatch(ctx1);
        assertEquals(HttpStatus.OK, res1.getStatus());

        // HR with gmail
        UUID uid2 = UUID.randomUUID();
        UUID labId1 = UUID.randomUUID();
        when(userDao.createUser(eq("hr.test@gmail.com"), anyString(), anyString(), eq(Role.LAB), anyBoolean()))
                .thenReturn(new User(uid2, "hr.test@gmail.com", "h", "s", Role.LAB, null, true, Instant.now(), Instant.now()));
        when(labDao.createLab(eq("MAScIR Nanotech"), anyString(), eq("Rabat"), anyString(), anyString(), eq(false)))
                .thenReturn(new LabDao.LabInfo(labId1, "MAScIR Nanotech", "Unit", "Rabat", "Research", "", false));

        String hrBody = """
            {
                "accountType": "HR",
                "name": "Sara HR",
                "email": "hr.test@gmail.com",
                "password": "SecurePassword123!",
                "privacyPolicyAccepted": true,
                "labName": "MAScIR Nanotech",
                "city": "Rabat"
            }
        """;
        RequestContext ctx2 = createMockContext("POST", "/api/auth/register", hrBody, null);
        HttpResponse res2 = router.dispatch(ctx2);
        assertEquals(HttpStatus.OK, res2.getStatus());

        // CEO with gmail
        UUID uid3 = UUID.randomUUID();
        UUID labId2 = UUID.randomUUID();
        when(userDao.createUser(eq("ceo.test@gmail.com"), anyString(), anyString(), eq(Role.LAB), anyBoolean()))
                .thenReturn(new User(uid3, "ceo.test@gmail.com", "h", "s", Role.LAB, null, true, Instant.now(), Instant.now()));
        when(labDao.createLab(eq("AI Lab"), anyString(), eq("Casablanca"), anyString(), anyString(), eq(false)))
                .thenReturn(new LabDao.LabInfo(labId2, "AI Lab", "Unit", "Casablanca", "Research", "", false));

        String ceoBody = """
            {
                "accountType": "CEO",
                "name": "Dr. Amine CEO",
                "email": "ceo.test@gmail.com",
                "password": "SecurePassword123!",
                "privacyPolicyAccepted": true,
                "labName": "AI Lab",
                "city": "Casablanca"
            }
        """;
        RequestContext ctx3 = createMockContext("POST", "/api/auth/register", ceoBody, null);
        HttpResponse res3 = router.dispatch(ctx3);
        assertEquals(HttpStatus.OK, res3.getStatus());
    }

    @Test
    @DisplayName("2. Invalid email format (missing @ or exceeding 254 chars) is refused with 400 Bad Request")
    void invalidEmailRefused() throws Exception {
        // Without @
        String noAtBody = """
            {
                "accountType": "STUDENT",
                "name": "Invalid Email",
                "email": "notanemailaddress.com",
                "password": "SecurePassword123!",
                "privacyPolicyAccepted": true
            }
        """;
        RequestContext ctxNoAt = createMockContext("POST", "/api/auth/register", noAtBody, null);
        HttpResponse resNoAt = router.dispatch(ctxNoAt);
        assertEquals(HttpStatus.BAD_REQUEST, resNoAt.getStatus());
        assertTrue(new String(resNoAt.getBody(), StandardCharsets.UTF_8).contains("A valid email address is required"));

        // Exceeding 254 characters
        String longLocal = "a".repeat(250) + "@example.com";
        String tooLongBody = """
            {
                "accountType": "STUDENT",
                "name": "Too Long",
                "email": "%s",
                "password": "SecurePassword123!",
                "privacyPolicyAccepted": true
            }
        """.formatted(longLocal);
        RequestContext ctxTooLong = createMockContext("POST", "/api/auth/register", tooLongBody, null);
        HttpResponse resTooLong = router.dispatch(ctxTooLong);
        assertEquals(HttpStatus.BAD_REQUEST, resTooLong.getStatus());
        assertTrue(new String(resTooLong.getBody(), StandardCharsets.UTF_8).contains("too long"));
    }

    @Test
    @DisplayName("3. Student with gmail receives verification token and cannot login before verifying")
    void studentWithGmailReceivesVerificationLinkAndCannotLoginBeforeVerifying() throws Exception {
        String email = "student.verify@gmail.com";
        when(userDao.findByEmail(email)).thenReturn(Optional.empty());
        when(userDao.countRecentRegistrations(anyString(), anyString(), anyInt())).thenReturn(0);

        UUID studentId = UUID.randomUUID();
        when(userDao.createUser(eq(email), anyString(), anyString(), eq(Role.STUDENT), eq(false)))
                .thenReturn(new User(studentId, email, "hash", "salt", Role.STUDENT, null, true, Instant.now(), Instant.now()));

        String body = """
            {
                "accountType": "STUDENT",
                "name": "Student Test",
                "email": "%s",
                "password": "SecurePassword123!",
                "privacyPolicyAccepted": true
            }
        """.formatted(email);

        RequestContext ctx = createMockContext("POST", "/api/auth/register", body, null);
        HttpResponse res = router.dispatch(ctx);
        assertEquals(HttpStatus.OK, res.getStatus());

        // Token creation was called
        verify(userDao).createEmailVerificationToken(eq(studentId), anyString(), any());

        // Now attempt login before verification (emailVerifiedAt is null)
        String rawPassword = "SecurePassword123!";
        String hash = PasswordHasher.hashPassword(rawPassword);
        User unverifiedUser = new User(studentId, email, hash, "salt", Role.STUDENT, null, true, Instant.now(), Instant.now());
        when(userDao.findByEmail(email)).thenReturn(Optional.of(unverifiedUser));

        String loginBody = """
            {
                "email": "%s",
                "password": "%s"
            }
        """.formatted(email, rawPassword);

        RequestContext loginCtx = createMockContext("POST", "/api/auth/login", loginBody, null);
        HttpResponse loginRes = router.dispatch(loginCtx);

        assertEquals(HttpStatus.FORBIDDEN, loginRes.getStatus());
        assertTrue(new String(loginRes.getBody(), StandardCharsets.UTF_8).contains("Please verify your email address before signing in."));
    }

    @Test
    @DisplayName("4. HR with gmail creates pending account with domain_matches=false (Personal email) visible by admin")
    void hrWithGmailCreatesPendingAccountWithPersonalEmailFlag() throws Exception {
        String email = "sara.hr@gmail.com";
        when(userDao.findByEmail(email)).thenReturn(Optional.empty());
        when(userDao.countRecentRegistrations(anyString(), anyString(), anyInt())).thenReturn(0);

        UUID hrId = UUID.randomUUID();
        UUID labId = UUID.randomUUID();

        when(userDao.createUser(eq(email), anyString(), anyString(), eq(Role.LAB), anyBoolean()))
                .thenReturn(new User(hrId, email, "hash", "salt", Role.LAB, null, true, Instant.now(), Instant.now()));
        when(labDao.createLab(eq("Bio Tech"), anyString(), eq("Rabat"), anyString(), anyString(), eq(false)))
                .thenReturn(new LabDao.LabInfo(labId, "Bio Tech", "Unit", "Rabat", "Research", "", false));

        String body = """
            {
                "accountType": "HR",
                "name": "Sara HR",
                "email": "%s",
                "password": "SecurePassword123!",
                "privacyPolicyAccepted": true,
                "labName": "Bio Tech",
                "city": "Rabat"
            }
        """.formatted(email);

        RequestContext ctx = createMockContext("POST", "/api/auth/register", body, null);
        HttpResponse res = router.dispatch(ctx);
        assertEquals(HttpStatus.OK, res.getStatus());

        // Verified domain_matches is false because gmail.com is in FREE_EMAIL_PROVIDERS
        verify(labDao).createClaimRequest(eq(labId), eq(hrId), eq(email), eq(false));
        // Lab is created unverified (verified = false)
        verify(labDao).createLab(eq("Bio Tech"), anyString(), eq("Rabat"), anyString(), anyString(), eq(false));
    }

    @Test
    @DisplayName("5. Any valid email domain is accepted without university domain blocking")
    void anyValidEmailAcceptedNoDomainRejection() throws Exception {
        when(userDao.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userDao.countRecentRegistrations(anyString(), anyString(), anyInt())).thenReturn(0);

        UUID uid = UUID.randomUUID();
        when(userDao.createUser(eq("student@international-mail.org"), anyString(), anyString(), eq(Role.STUDENT), anyBoolean()))
                .thenReturn(new User(uid, "student@international-mail.org", "hash", "salt", Role.STUDENT, null, true, Instant.now(), Instant.now()));

        String body = """
            {
                "accountType": "STUDENT",
                "name": "International Student",
                "email": "student@international-mail.org",
                "password": "SecurePassword123!",
                "privacyPolicyAccepted": true
            }
        """;

        RequestContext ctx = createMockContext("POST", "/api/auth/register", body, null);
        HttpResponse res = router.dispatch(ctx);

        assertEquals(HttpStatus.OK, res.getStatus());
        assertTrue(new String(res.getBody(), StandardCharsets.UTF_8).contains("If your email is eligible, a verification link has been sent to your address."));
    }

    @Test
    @DisplayName("6. Registration with accountType=ADMIN is strictly rejected with 400 Bad Request")
    void accountTypeAdminRejected() throws Exception {
        String body = """
            {
                "accountType": "ADMIN",
                "name": "Attacker Trying Admin",
                "email": "attacker@gmail.com",
                "password": "SecurePassword123!",
                "privacyPolicyAccepted": true
            }
        """;

        RequestContext ctx = createMockContext("POST", "/api/auth/register", body, null);
        HttpResponse response = router.dispatch(ctx);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        String bodyStr = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(bodyStr.contains("Invalid accountType"));
        verify(userDao, never()).createUser(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("7. Client payload role=ADMIN is ignored; accountType=STUDENT strictly enforces Role.STUDENT")
    void clientRoleFieldIgnored() throws Exception {
        when(userDao.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userDao.countRecentRegistrations(anyString(), anyString(), anyInt())).thenReturn(0);

        UUID uid = UUID.randomUUID();
        when(userDao.createUser(anyString(), anyString(), anyString(), eq(Role.STUDENT), anyBoolean()))
                .thenReturn(new User(uid, "student@gmail.com", "hash", "salt", Role.STUDENT, null, true, Instant.now(), Instant.now()));

        String body = """
            {
                "accountType": "STUDENT",
                "role": "ADMIN",
                "name": "Smart Student",
                "email": "student@gmail.com",
                "password": "SecurePassword123!",
                "privacyPolicyAccepted": true
            }
        """;

        RequestContext ctx = createMockContext("POST", "/api/auth/register", body, null);
        HttpResponse response = router.dispatch(ctx);

        assertEquals(HttpStatus.OK, response.getStatus());
        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(userDao).createUser(anyString(), anyString(), anyString(), roleCaptor.capture(), anyBoolean());
        assertEquals(Role.STUDENT, roleCaptor.getValue());
    }

    @Test
    @DisplayName("8. Password shorter than 12 characters is rejected with 400 Bad Request")
    void passwordTooShortRejected() throws Exception {
        String body = """
            {
                "accountType": "STUDENT",
                "name": "Karim Idrissi",
                "email": "karim@gmail.com",
                "password": "Short1!",
                "privacyPolicyAccepted": true
            }
        """;

        RequestContext ctx = createMockContext("POST", "/api/auth/register", body, null);
        HttpResponse response = router.dispatch(ctx);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        String bodyStr = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(bodyStr.contains("Password must be at least 12 characters"));
    }

    @Test
    @DisplayName("9. Existing email returns identical response without leaking user existence (anti-enumeration)")
    void existingEmailReturnsIdenticalResponse() throws Exception {
        User existingUser = new User(UUID.randomUUID(), "existing@gmail.com", "hash", "salt", Role.STUDENT, Instant.now(), true, Instant.now(), Instant.now());
        when(userDao.findByEmail("existing@gmail.com")).thenReturn(Optional.of(existingUser));
        when(userDao.countRecentRegistrations(anyString(), anyString(), anyInt())).thenReturn(0);

        String body = """
            {
                "accountType": "STUDENT",
                "name": "Existing User",
                "email": "existing@gmail.com",
                "password": "SecurePassword123!",
                "privacyPolicyAccepted": true
            }
        """;

        RequestContext ctx = createMockContext("POST", "/api/auth/register", body, null);
        HttpResponse response = router.dispatch(ctx);

        assertEquals(HttpStatus.OK, response.getStatus());
        String bodyStr = new String(response.getBody(), StandardCharsets.UTF_8);
        assertEquals("{\"message\":\"If your email is eligible, a verification link has been sent to your address.\"}", bodyStr);
        verify(userDao, never()).createUser(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("10. After admin approval, the lab user CAN successfully publish offers")
    void afterAdminApprovalLabCanPublishOffer() throws Exception {
        UUID labId = UUID.randomUUID();
        UUID hrUserId = UUID.randomUUID();

        when(labDao.isLabApproved(labId)).thenReturn(false);
        User hrUser = new User(hrUserId, "hr@gmail.com", "hash", "salt", Role.LAB, Instant.now(), true, Instant.now(), Instant.now());
        AuthContext hrAuth = new AuthContext(hrUser, labId, LabRole.HR);

        RequestContext publishBefore = createMockContext("POST", "/api/lab/offers", "{\"title\": \"Internship 1\"}", hrAuth);
        HttpResponse resBefore = router.dispatch(publishBefore);
        assertEquals(HttpStatus.FORBIDDEN, resBefore.getStatus());

        User adminUser = new User(UUID.randomUUID(), "admin@fitwell.ma", "hash", "salt", Role.ADMIN, Instant.now(), true, Instant.now(), Instant.now());
        AuthContext adminAuth = new AuthContext(adminUser, null, null);

        RequestContext approveCtx = createMockContext("POST", "/api/admin/labs-claims/" + labId + "/approve", null, adminAuth);
        HttpResponse approveRes = router.dispatch(approveCtx);
        assertEquals(HttpStatus.OK, approveRes.getStatus());
        verify(labDao).approveLab(labId);

        when(labDao.isLabApproved(labId)).thenReturn(true);

        RequestContext publishAfter = createMockContext("POST", "/api/lab/offers", "{\"title\": \"Internship 1\"}", hrAuth);
        HttpResponse resAfter = router.dispatch(publishAfter);
        assertEquals(HttpStatus.CREATED, resAfter.getStatus());
        assertTrue(new String(resAfter.getBody(), StandardCharsets.UTF_8).contains("Offer published successfully"));
    }

    @Test
    @DisplayName("11. Resend verification returns identical response for existing/non-existing accounts and enforces rate limiting")
    void resendVerificationSecurityAndRateLimit() throws Exception {
        String existingEmail = "unverified.user@gmail.com";
        User user = new User(UUID.randomUUID(), existingEmail, "h", "s", Role.STUDENT, null, true, Instant.now(), Instant.now());
        when(userDao.findByEmail(existingEmail)).thenReturn(Optional.of(user));
        when(userDao.findByEmail("nonexistent@gmail.com")).thenReturn(Optional.empty());
        when(userDao.countRecentVerificationResends(anyString(), anyString(), anyInt())).thenReturn(0);

        // 1. Resend for existing unverified user
        String reqExisting = "{\"email\": \"" + existingEmail + "\"}";
        RequestContext ctx1 = createMockContext("POST", "/api/auth/resend-verification", reqExisting, null);
        HttpResponse res1 = router.dispatch(ctx1);
        assertEquals(HttpStatus.OK, res1.getStatus());
        assertTrue(new String(res1.getBody(), StandardCharsets.UTF_8).contains("If your account is eligible, a new verification link has been sent to your email."));
        verify(userDao).createEmailVerificationToken(eq(user.id()), anyString(), any());

        // 2. Resend for non-existent user returns IDENTICAL generic message
        String reqNonExistent = "{\"email\": \"nonexistent@gmail.com\"}";
        RequestContext ctx2 = createMockContext("POST", "/api/auth/resend-verification", reqNonExistent, null);
        HttpResponse res2 = router.dispatch(ctx2);
        assertEquals(HttpStatus.OK, res2.getStatus());
        assertTrue(new String(res2.getBody(), StandardCharsets.UTF_8).contains("If your account is eligible, a new verification link has been sent to your email."));

        // 3. Frequency limit exceeded (3 attempts in 15 minutes) returns 409 Conflict
        when(userDao.countRecentVerificationResends(anyString(), eq(existingEmail), anyInt())).thenReturn(3);
        RequestContext ctxRateLimited = createMockContext("POST", "/api/auth/resend-verification", reqExisting, null);
        HttpResponse resRateLimited = router.dispatch(ctxRateLimited);
        assertEquals(HttpStatus.CONFLICT, resRateLimited.getStatus());
        assertTrue(new String(resRateLimited.getBody(), StandardCharsets.UTF_8).contains("Too many verification requests"));
    }

    @Test
    @DisplayName("12. DEV_AUTO_VERIFY_EMAIL=true automatically marks new accounts as verified and skips email verification step")
    void devAutoVerifyEmailTrueSkipsVerificationStep() throws Exception {
        AuthService autoVerifyService = spy(new AuthService(
                userDao, sessionDao, studentProfileDao, labDao, labMemberDao, allowedUniversityDao, auditService
        ));
        doReturn(true).when(autoVerifyService).isDevAutoVerifyEmail();

        String email = "autoverify@gmail.com";
        when(userDao.findByEmail(email)).thenReturn(Optional.empty());
        when(userDao.countRecentRegistrations(anyString(), anyString(), anyInt())).thenReturn(0);

        UUID uid = UUID.randomUUID();
        when(userDao.createUser(eq(email), anyString(), anyString(), eq(Role.STUDENT), eq(true)))
                .thenReturn(new User(uid, email, "hash", "salt", Role.STUDENT, Instant.now(), true, Instant.now(), Instant.now()));

        RegisterRequest req = new RegisterRequest("Auto Verified", "STUDENT", email, "SecurePassword123!", null, null, true, null);
        autoVerifyService.register(req, "127.0.0.1");

        // Verify createUser was called with emailVerified = true
        verify(userDao).createUser(eq(email), anyString(), anyString(), eq(Role.STUDENT), eq(true));
        // Verify no email verification token was generated
        verify(userDao, never()).createEmailVerificationToken(eq(uid), anyString(), any());
    }
}
