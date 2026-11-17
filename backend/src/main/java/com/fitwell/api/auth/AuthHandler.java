package com.fitwell.api.auth;

import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.HttpStatus;
import com.fitwell.core.http.RequestContext;
import com.fitwell.core.middleware.AuthMiddleware;
import com.fitwell.core.security.AuthContext;
import com.fitwell.dao.AllowedUniversityDao;
import com.fitwell.dao.LabDao;
import com.fitwell.dao.StudentProfileDao;
import com.fitwell.model.dto.AuthDtos.*;
import com.fitwell.model.enums.Role;
import com.fitwell.service.AuthService;

public final class AuthHandler {

    private final AuthService authService;
    private final LabDao labDao;
    private final StudentProfileDao studentProfileDao;
    private final AllowedUniversityDao allowedUniversityDao;

    public AuthHandler(AuthService authService, LabDao labDao, StudentProfileDao studentProfileDao, AllowedUniversityDao allowedUniversityDao) {
        this.authService = authService;
        this.labDao = labDao;
        this.studentProfileDao = studentProfileDao;
        this.allowedUniversityDao = allowedUniversityDao;
    }

    /**
     * Unified registration endpoint.
     */
    public HttpResponse register(RequestContext ctx) throws Exception {
        RegisterRequest req = ctx.parseJsonBody(RegisterRequest.class);
        MessageResponse response = authService.register(req, ctx.getClientIp());
        return HttpResponse.ok(response);
    }

    public HttpResponse login(RequestContext ctx) throws Exception {
        LoginRequest req = ctx.parseJsonBody(LoginRequest.class);
        AuthResponse response = authService.login(req, ctx.getClientIp());

        return HttpResponse.builder()
                .status(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .setCookie(AuthMiddleware.SESSION_COOKIE_NAME, response.token(), 30 * 24 * 3600, false)
                .body(com.fitwell.core.json.JsonCodec.toJsonBytes(response))
                .build();
    }

    public HttpResponse logout(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        String token = ctx.getCookie(AuthMiddleware.SESSION_COOKIE_NAME);
        if (token == null) {
            String header = ctx.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                token = header.substring(7).trim();
            }
        }

        if (token != null) {
            authService.logout(token, auth != null ? auth.getUserId() : null, ctx.getClientIp());
        }

        return HttpResponse.builder()
                .status(HttpStatus.OK)
                .clearCookie(AuthMiddleware.SESSION_COOKIE_NAME)
                .body(com.fitwell.core.json.JsonCodec.toJsonBytes(new MessageResponse("Signed out successfully")))
                .build();
    }

    public HttpResponse me(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        if (auth == null || auth.user() == null) {
            return HttpResponse.unauthorized("Not authenticated");
        }

        boolean isLabApproved = false;
        boolean onboardingCompleted = true;
        boolean onboardingSkipped = false;
        boolean isVerifiedStudent = false;

        String fullName = auth.user().email();
        String studentLevel = "Master";
        if (auth.user().role() == Role.LAB && auth.labId() != null) {
            isLabApproved = labDao.isLabApproved(auth.labId());
        } else if (auth.user().role() == Role.STUDENT) {
            StudentProfileDao.OnboardingStatus status = studentProfileDao.getOnboardingStatus(auth.user().id());
            onboardingCompleted = status.completed() || status.skipped();
            onboardingSkipped = status.skipped();
            var profileOpt = studentProfileDao.findByUserId(auth.user().id());
            if (profileOpt.isPresent()) {
                if (profileOpt.get().fullName() != null && !profileOpt.get().fullName().isBlank()) {
                    fullName = profileOpt.get().fullName();
                }
                if (profileOpt.get().level() != null && !profileOpt.get().level().isBlank()) {
                    studentLevel = profileOpt.get().level();
                }
            }
            String email = auth.user().email();
            if (email != null && email.contains("@")) {
                String domain = email.substring(email.indexOf("@") + 1);
                isVerifiedStudent = allowedUniversityDao.isDomainAllowed(domain)
                        || domain.endsWith(".ac.ma") || domain.endsWith(".ma") || domain.endsWith(".edu");
            }
        }

        UserDto userDto = new UserDto(
                auth.user().id(),
                auth.user().email(),
                auth.user().role(),
                auth.user().isEmailVerified(),
                fullName,
                studentLevel,
                auth.labId(),
                isLabApproved,
                onboardingCompleted,
                onboardingSkipped,
                isVerifiedStudent
        );

        return HttpResponse.ok(userDto);
    }

    public HttpResponse getConfig(RequestContext ctx) {
        String appEnv = System.getenv().getOrDefault("APP_ENV", "dev");
        boolean isDev = authService.isDevEnv();
        return HttpResponse.ok(new AuthConfigResponse(appEnv, isDev));
    }

    public HttpResponse verifyEmail(RequestContext ctx) throws Exception {
        String token = ctx.getQueryParam("token");
        if (token == null || token.isBlank()) {
            return HttpResponse.badRequest("Verification token is required");
        }
        MessageResponse response = authService.verifyEmail(token, ctx.getClientIp());
        return HttpResponse.ok(response);
    }

    public HttpResponse resendVerification(RequestContext ctx) throws Exception {
        ResendVerificationRequest req = ctx.parseJsonBody(ResendVerificationRequest.class);
        MessageResponse response = authService.resendVerification(req, ctx.getClientIp());
        return HttpResponse.ok(response);
    }

    public HttpResponse forgotPassword(RequestContext ctx) throws Exception {
        ForgotPasswordRequest req = ctx.parseJsonBody(ForgotPasswordRequest.class);
        MessageResponse response = authService.forgotPassword(req, ctx.getClientIp());
        return HttpResponse.ok(response);
    }

    public HttpResponse resetPassword(RequestContext ctx) throws Exception {
        ResetPasswordRequest req = ctx.parseJsonBody(ResetPasswordRequest.class);
        MessageResponse response = authService.resetPassword(req, ctx.getClientIp());
        return HttpResponse.ok(response);
    }
}
