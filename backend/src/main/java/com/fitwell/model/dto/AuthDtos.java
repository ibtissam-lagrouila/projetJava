package com.fitwell.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fitwell.model.enums.Role;

import java.util.Map;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            String email,
            String password
    ) {}

    public record RegisterRequest(
            String name,
            String accountType,
            String email,
            String password,
            String labName,
            String city,
            boolean privacyPolicyAccepted,
            // Client-supplied role field will be ignored by the backend
            String role
    ) {}

    public record ForgotPasswordRequest(
            String email
    ) {}

    public record ResetPasswordRequest(
            String token,
            String newPassword
    ) {}

    public record ResendVerificationRequest(
            String email
    ) {}

    public record UserDto(
            UUID id,
            String email,
            Role role,
            boolean emailVerified,
            String fullName,
            UUID labId,
            boolean isLabApproved,
            boolean onboardingCompleted,
            boolean onboardingSkipped,
            boolean isVerifiedStudent
    ) {}

    public record AuthResponse(
            String token,
            UserDto user
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MessageResponse(
            String message,
            String devVerificationLink
    ) {
        public MessageResponse(String message) {
            this(message, null);
        }
    }

    public record ValidationErrorResponse(
            String error,
            Map<String, String> errors
    ) {}

    public record AuthConfigResponse(
            String appEnv,
            boolean isDev
    ) {}
}
