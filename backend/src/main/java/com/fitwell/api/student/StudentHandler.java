package com.fitwell.api.student;

import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.HttpStatus;
import com.fitwell.core.http.RequestContext;
import com.fitwell.core.middleware.AuthMiddleware;
import com.fitwell.core.security.AuthContext;
import com.fitwell.service.StudentService;
import com.fitwell.service.StudentService.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class StudentHandler {

    private final StudentService studentService;
    private final String uploadDir;

    public StudentHandler(StudentService studentService, String uploadDir) {
        this.studentService = studentService;
        this.uploadDir = uploadDir;
    }

    public HttpResponse uploadCv(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        byte[] pdfBytes = ctx.getBody(5 * 1024 * 1024); // 5MB limit
        String originalFileName = ctx.getHeader("X-File-Name");
        if (originalFileName == null || originalFileName.isBlank()) {
            originalFileName = "CV.pdf";
        }

        CvUploadResponse response = studentService.handleCvUpload(auth.getUserId(), originalFileName, pdfBytes, uploadDir, ctx.getClientIp());
        return HttpResponse.ok(response);
    }

    public HttpResponse getOnboardingState(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        var state = studentService.getOnboardingState(auth.getUserId());
        return HttpResponse.ok(state);
    }

    public HttpResponse confirmCv(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        ConfirmCvRequest req = ctx.parseJsonBody(ConfirmCvRequest.class);
        studentService.confirmCv(auth.getUserId(), req, ctx.getClientIp());
        return HttpResponse.ok(Map.of("message", "CV confirmed and profile updated", "onboardingCompleted", true));
    }

    public HttpResponse skipOnboarding(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        studentService.skipOnboarding(auth.getUserId(), ctx.getClientIp());
        return HttpResponse.ok(Map.of("message", "Onboarding skipped", "onboardingSkipped", true));
    }

    public HttpResponse getDraftCv(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        String cvIdStr = ctx.getPathParam("id");
        java.util.UUID cvId;
        try {
            cvId = java.util.UUID.fromString(cvIdStr);
        } catch (IllegalArgumentException e) {
            return HttpResponse.notFound("CV draft not found");
        }

        try {
            var draft = studentService.getDraftCv(auth.getUserId(), cvId);
            return HttpResponse.ok(draft);
        } catch (java.util.NoSuchElementException e) {
            return HttpResponse.notFound("CV draft not found");
        }
    }

    public HttpResponse completeOnboarding(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        OnboardingCompleteRequest req = ctx.parseJsonBody(OnboardingCompleteRequest.class);
        studentService.completeOnboarding(auth.getUserId(), req, ctx.getClientIp());
        return HttpResponse.ok(Map.of("message", "Onboarding completed successfully"));
    }

    public HttpResponse getProfile(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        var profile = studentService.getProfile(auth.getUserId());
        return HttpResponse.ok(profile);
    }

    public HttpResponse updateProfile(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        record UpdateProfileReq(
                String fullName,
                String university,
                String level,
                List<String> languages,
                List<String> interests,
                String availabilityStart,
                int availabilityDurationMonths
        ) {}

        UpdateProfileReq req = ctx.parseJsonBody(UpdateProfileReq.class);
        LocalDate start = req.availabilityStart() != null ? LocalDate.parse(req.availabilityStart()) : null;

        studentService.updateProfile(
                auth.getUserId(),
                req.fullName(),
                req.university(),
                req.level(),
                req.languages() != null ? req.languages() : List.of(),
                req.interests() != null ? req.interests() : List.of(),
                start,
                req.availabilityDurationMonths(),
                ctx.getClientIp()
        );

        return HttpResponse.ok(Map.of("message", "Profile updated successfully"));
    }

    public HttpResponse toggleAiAssistance(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        record AiToggleReq(boolean enabled) {}
        AiToggleReq req = ctx.parseJsonBody(AiToggleReq.class);
        studentService.setAiAssistance(auth.getUserId(), req.enabled(), ctx.getClientIp());
        return HttpResponse.ok(Map.of("enabled", req.enabled()));
    }

    public HttpResponse uploadPhoto(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        byte[] photoBytes = ctx.getBody(2 * 1024 * 1024); // 2MB
        String contentType = ctx.getHeader("Content-Type");
        String path = studentService.updateProfilePhoto(auth.getUserId(), photoBytes, contentType, uploadDir);
        return HttpResponse.ok(Map.of("message", "Photo uploaded successfully", "path", path));
    }

    public HttpResponse exportData(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        var data = studentService.exportUserData(auth.getUserId(), ctx.getClientIp());
        return HttpResponse.ok(data);
    }

    public HttpResponse deleteAccount(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        studentService.deleteAccount(auth.getUserId(), ctx.getClientIp());
        return HttpResponse.builder()
                .status(HttpStatus.OK)
                .clearCookie(AuthMiddleware.SESSION_COOKIE_NAME)
                .body(com.fitwell.core.json.JsonCodec.toJsonBytes(Map.of("message", "Account successfully deleted")))
                .build();
    }
}

