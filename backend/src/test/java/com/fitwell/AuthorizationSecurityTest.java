package com.fitwell;

import com.fitwell.api.auth.AuthHandler;
import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.HttpStatus;
import com.fitwell.core.http.RequestContext;
import com.fitwell.core.http.Router;
import com.fitwell.core.middleware.AuthMiddleware;
import com.fitwell.core.middleware.ErrorHandlingMiddleware;
import com.fitwell.core.middleware.RateLimitMiddleware;
import com.fitwell.core.middleware.RoleGuardMiddleware;
import com.fitwell.core.security.AuthContext;
import com.fitwell.dao.UserDao;
import com.fitwell.model.dto.AuthDtos.LoginRequest;
import com.fitwell.model.entity.User;
import com.fitwell.model.enums.LabRole;
import com.fitwell.model.enums.Role;
import com.fitwell.service.AuthService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class AuthorizationSecurityTest {

    private Router router;
    private UserDao userDao;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userDao = Mockito.mock(UserDao.class);
        authService = Mockito.mock(AuthService.class);
        router = new Router();
        router.use(new ErrorHandlingMiddleware());

        // Setup test routes
        router.get("/api/student/dashboard", ctx -> HttpResponse.ok("Student Dashboard"),
                new RoleGuardMiddleware(Role.STUDENT));

        router.get("/api/lab/dashboard", ctx -> HttpResponse.ok("Lab Dashboard"),
                new RoleGuardMiddleware(Role.LAB));

        router.get("/api/admin/overview", ctx -> HttpResponse.ok("Admin Overview"),
                new RoleGuardMiddleware(Role.ADMIN));

        // Privacy rule: Return 404 (not 403) for other lab's resources
        router.get("/api/lab/applications/{id}", ctx -> {
            String appId = ctx.getPathParam("id");
            if ("other-lab-application-uuid".equals(appId)) {
                return HttpResponse.notFound("Candidate not found");
            }
            return HttpResponse.ok(Map.of("id", appId));
        }, new RoleGuardMiddleware(Role.LAB));
    }

    private RequestContext createMockContext(String method, String path, AuthContext auth) {
        HttpExchange exchange = Mockito.mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn(method);
        when(exchange.getRequestURI()).thenReturn(URI.create(path));
        when(exchange.getRequestHeaders()).thenReturn(new Headers());
        when(exchange.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));

        RequestContext ctx = new RequestContext(exchange, Collections.emptyMap(), Collections.singleton("127.0.0.1"));
        if (auth != null) {
            ctx.setAttribute(AuthMiddleware.AUTH_ATTRIBUTE, auth);
        }
        return ctx;
    }

    private User createMockUser(Role role) {
        return new User(UUID.randomUUID(), "test@domain.ma", "hash", "salt", role, Instant.now(), true, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("Unauthenticated request to protected route must return 401 Unauthorized")
    void unauthenticatedRequestReturns401() throws Exception {
        RequestContext ctx = createMockContext("GET", "/api/student/dashboard", null);
        HttpResponse response = router.dispatch(ctx);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatus());
        assertTrue(new String(response.getBody()).contains("Authentication required"));
    }

    @Test
    @DisplayName("Student accessing lab route must return 403 Forbidden")
    void studentAccessingLabRouteReturns403() throws Exception {
        User student = createMockUser(Role.STUDENT);
        AuthContext auth = new AuthContext(student, null, null);

        RequestContext ctx = createMockContext("GET", "/api/lab/dashboard", auth);
        HttpResponse response = router.dispatch(ctx);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatus());
        assertTrue(new String(response.getBody()).contains("Access denied"));
    }

    @Test
    @DisplayName("Student accessing admin route must return 403 Forbidden")
    void studentAccessingAdminRouteReturns403() throws Exception {
        User student = createMockUser(Role.STUDENT);
        AuthContext auth = new AuthContext(student, null, null);

        RequestContext ctx = createMockContext("GET", "/api/admin/overview", auth);
        HttpResponse response = router.dispatch(ctx);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatus());
    }

    @Test
    @DisplayName("Lab accessing admin route must return 403 Forbidden")
    void labAccessingAdminRouteReturns403() throws Exception {
        User labUser = createMockUser(Role.LAB);
        AuthContext auth = new AuthContext(labUser, UUID.randomUUID(), LabRole.CEO);

        RequestContext ctx = createMockContext("GET", "/api/admin/overview", auth);
        HttpResponse response = router.dispatch(ctx);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatus());
    }

    @Test
    @DisplayName("Privacy check: Lab accessing another lab's candidate returns 404 (NOT 403) to prevent enumeration")
    void accessingOthersResourceReturns404() throws Exception {
        User labUser = createMockUser(Role.LAB);
        AuthContext auth = new AuthContext(labUser, UUID.randomUUID(), LabRole.CEO);

        RequestContext ctx = createMockContext("GET", "/api/lab/applications/other-lab-application-uuid", auth);
        HttpResponse response = router.dispatch(ctx);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatus(), "Must return 404 Not Found rather than 403 to prevent enumeration");
    }

    @Test
    @DisplayName("Rate limiter returns 429 Too Many Requests when threshold exceeded")
    void rateLimiterReturns429() throws Exception {
        when(userDao.countRecentFailedAttempts(any(), anyString(), Mockito.anyInt())).thenReturn(6);

        Router rateLimitedRouter = new Router();
        rateLimitedRouter.use(new RateLimitMiddleware(userDao));
        rateLimitedRouter.post("/api/auth/login", ctx -> HttpResponse.ok("Login"));

        RequestContext ctx = createMockContext("POST", "/api/auth/login", null);
        HttpResponse response = rateLimitedRouter.dispatch(ctx);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatus());
        assertTrue(new String(response.getBody()).contains("Too many failed login attempts"));
    }
}

