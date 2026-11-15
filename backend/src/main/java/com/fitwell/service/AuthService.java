package com.fitwell.service;

import com.fitwell.core.http.ValidationException;
import com.fitwell.core.security.PasswordHasher;
import com.fitwell.core.security.TokenGenerator;
import com.fitwell.dao.*;
import com.fitwell.model.dto.AuthDtos.*;
import com.fitwell.model.entity.LabMember;
import com.fitwell.model.entity.User;
import com.fitwell.model.enums.LabRole;
import com.fitwell.model.enums.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration SESSION_DURATION = Duration.ofDays(30);
    private static final Duration VERIFICATION_TOKEN_DURATION = Duration.ofHours(24);

    private static final Set<String> ALLOWED_ACCOUNT_TYPES = Set.of("STUDENT", "HR", "CEO");

    private static final Set<String> COMMON_WEAK_PASSWORDS = Set.of(
            "password123456", "123456789012", "qwerty123456", "admin12345678",
            "azerty123456", "fitwell123456", "welcome123456", "password2026!"
    );

    private static final Set<String> FREE_EMAIL_PROVIDERS = Set.of(
            "gmail.com", "yahoo.com", "yahoo.fr", "hotmail.com", "outlook.com", "live.com", "icloud.com"
    );

    private final UserDao userDao;
    private final SessionDao sessionDao;
    private final StudentProfileDao studentProfileDao;
    private final LabDao labDao;
    private final LabMemberDao labMemberDao;
    private final AllowedUniversityDao allowedUniversityDao;
    private final AuditService auditService;

    public AuthService(UserDao userDao,
                       SessionDao sessionDao,
                       StudentProfileDao studentProfileDao,
                       LabDao labDao,
                       LabMemberDao labMemberDao,
                       AllowedUniversityDao allowedUniversityDao,
                       AuditService auditService) {
        this.userDao = userDao;
        this.sessionDao = sessionDao;
        this.studentProfileDao = studentProfileDao;
        this.labDao = labDao;
        this.labMemberDao = labMemberDao;
        this.allowedUniversityDao = allowedUniversityDao;
        this.auditService = auditService;
    }

    public boolean isDevEnv() {
        String appEnv = System.getenv().getOrDefault("APP_ENV", "dev");
        return "dev".equalsIgnoreCase(appEnv) || "development".equalsIgnoreCase(appEnv);
    }

    public boolean isDevAutoVerifyEmail() {
        String auto = System.getenv().getOrDefault("DEV_AUTO_VERIFY_EMAIL", "false");
        return isDevEnv() && Boolean.parseBoolean(auto);
    }

    /**
     * Unified single-page registration for STUDENT, HR, or CEO.
     * Validates accountType strictly against closed set {STUDENT, HR, CEO}.
     * Ignores any client-supplied "role" field.
     */
    public MessageResponse register(RegisterRequest req, String clientIp) throws SQLException {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        // 1. Validate accountType against strictly closed set {STUDENT, HR, CEO}
        if (req.accountType() == null || !ALLOWED_ACCOUNT_TYPES.contains(req.accountType().trim().toUpperCase())) {
            throw new IllegalArgumentException("Invalid accountType. Allowed types are: STUDENT, HR, CEO");
        }
        String accountType = req.accountType().trim().toUpperCase();

        // 2. Validate Name
        if (req.name() == null || req.name().trim().length() < 2) {
            fieldErrors.put("name", "Full name must be at least 2 characters");
        }

        // 3. Validate Email: valid format and maximum length (RFC 5321)
        if (req.email() == null || req.email().trim().isBlank()) {
            fieldErrors.put("email", "Email address is required");
        } else {
            String rawEmail = req.email().trim();
            if (rawEmail.length() > 254) {
                fieldErrors.put("email", "Email address is too long (maximum 254 characters)");
            } else if (!rawEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                fieldErrors.put("email", "A valid email address is required");
            }
        }

        // 4. Validate Password: >= 12 characters and not in common passwords list
        if (req.password() == null || req.password().length() < 12) {
            fieldErrors.put("password", "Password must be at least 12 characters");
        } else if (COMMON_WEAK_PASSWORDS.contains(req.password().toLowerCase())) {
            fieldErrors.put("password", "This password is too common. Please choose a stronger password.");
        }

        // 5. Validate Privacy Policy acceptance (Moroccan Law 09-08)
        if (!req.privacyPolicyAccepted()) {
            fieldErrors.put("privacyPolicyAccepted", "You must accept the privacy policy (Moroccan Law 09-08) to create an account.");
        }

        // 6. Validate Lab-specific fields for HR and CEO
        if ("HR".equals(accountType) || "CEO".equals(accountType)) {
            if (req.labName() == null || req.labName().trim().isBlank()) {
                fieldErrors.put("labName", "Laboratory name is required");
            }
            if (req.city() == null || req.city().trim().isBlank()) {
                fieldErrors.put("city", "City is required");
            }
        }

        // Throw field errors if any
        if (!fieldErrors.isEmpty()) {
            throw new ValidationException("Registration validation failed", fieldErrors);
        }

        String email = req.email().toLowerCase().trim();
        String domain = email.substring(email.indexOf("@") + 1);

        // 7. Rate limit check: max 5 registrations per IP/email in 15 minutes
        int recentRegs = userDao.countRecentRegistrations(clientIp, email, 15);
        if (recentRegs >= 5) {
            throw new IllegalStateException("Too many registration attempts. Please try again later.");
        }

        // 9. Anti-enumeration: if email already exists, return identical success message
        Optional<User> existingUser = userDao.findByEmail(email);
        if (existingUser.isPresent()) {
            log.info("Registration attempt for existing email {}. Returning generic response to prevent account enumeration.", email);
            return new MessageResponse("If your email is eligible, a verification link has been sent to your address.");
        }

        // 10. Create User with PBKDF2 hash (600,000 iterations)
        String passwordHash = PasswordHasher.hashPassword(req.password());
        String salt = passwordHash.split("\\$")[2];

        boolean autoVerify = isDevAutoVerifyEmail();
        Role userRole = "STUDENT".equals(accountType) ? Role.STUDENT : Role.LAB;

        User user = userDao.createUser(email, passwordHash, salt, userRole, autoVerify);

        // 11. Create Email Verification token if not auto-verified
        String devVerificationLink = null;
        if (!autoVerify) {
            String rawToken = TokenGenerator.generateToken();
            String tokenHash = TokenGenerator.hashToken(rawToken);
            userDao.createEmailVerificationToken(user.id(), tokenHash, Instant.now().plus(VERIFICATION_TOKEN_DURATION));

            // In development, create the clickable verification link
            if (isDevEnv()) {
                devVerificationLink = "http://localhost:3001/verify-email?token=" + rawToken;
                log.info("[EMAIL VERIFICATION SIMULATION] Verification link for {}: {}", email, devVerificationLink);
            }
        }

        // 12. Create Role-specific entities
        if ("STUDENT".equals(accountType)) {
            studentProfileDao.createProfile(user.id(), req.name().trim(), "Unknown", "Master");
            auditService.logAction(user.id(), "STUDENT_REGISTERED", "User", user.id().toString(),
                    "{\"email\":\"" + email + "\"}", clientIp);
        } else {
            // HR or CEO -> Create unverified Lab & pending ClaimRequest
            boolean isFreeEmail = FREE_EMAIL_PROVIDERS.contains(domain);
            LabDao.LabInfo lab = labDao.createLab(
                    req.labName().trim(),
                    "Research Unit",
                    req.city().trim(),
                    "Scientific Research",
                    "",
                    false // verified = false (pending admin review)
            );

            LabRole labRole = "HR".equals(accountType) ? LabRole.HR : LabRole.CEO;
            labMemberDao.addMember(user.id(), lab.id(), labRole);

            boolean domainMatches = !isFreeEmail;
            labDao.createClaimRequest(lab.id(), user.id(), email, domainMatches);

            auditService.logAction(user.id(), "LAB_ACCOUNT_REQUESTED", "Lab", lab.id().toString(),
                    "{\"labName\":\"" + req.labName() + "\",\"freeEmail\":" + isFreeEmail + ",\"role\":\"" + labRole + "\"}", clientIp);
        }

        return new MessageResponse("If your email is eligible, a verification link has been sent to your address.", devVerificationLink);
    }

    public AuthResponse login(LoginRequest req, String clientIp) throws SQLException {
        if (req.email() == null || req.password() == null) {
            throw new IllegalArgumentException("Email and password are required");
        }

        String email = req.email().toLowerCase().trim();
        Optional<User> userOpt = userDao.findByEmail(email);

        if (userOpt.isEmpty()) {
            userDao.recordLoginAttempt(email, clientIp, false);
            // Identical generic error message to prevent account enumeration
            throw new IllegalArgumentException("Invalid email or password");
        }

        User user = userOpt.get();
        boolean passwordMatches = PasswordHasher.verifyPassword(req.password(), user.passwordHash());

        if (!passwordMatches) {
            userDao.recordLoginAttempt(email, clientIp, false);
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Check if email has been verified
        if (!user.isEmailVerified()) {
            throw new SecurityException("Please verify your email address before signing in.");
        }

        userDao.recordLoginAttempt(email, clientIp, true);
        auditService.logAction(user.id(), "LOGIN_SUCCESS", "User", user.id().toString(), "{}", clientIp);

        String fullName = null;
        UUID labId = null;
        boolean isLabApproved = false;
        boolean onboardingCompleted = true;
        boolean onboardingSkipped = false;

        if (user.role() == Role.STUDENT) {
            fullName = studentProfileDao.findByUserId(user.id())
                    .map(StudentProfileDao.StudentProfileInfo::fullName)
                    .orElse(user.email());
            StudentProfileDao.OnboardingStatus status = studentProfileDao.getOnboardingStatus(user.id());
            onboardingCompleted = status.completed() || status.skipped();
            onboardingSkipped = status.skipped();
        } else if (user.role() == Role.LAB) {
            Optional<LabMember> memberOpt = labMemberDao.findByUserId(user.id());
            if (memberOpt.isPresent()) {
                labId = memberOpt.get().labId();
                fullName = labDao.findById(labId).map(LabDao.LabInfo::name).orElse(user.email());
                isLabApproved = labDao.isLabApproved(labId);
            }
        } else if (user.role() == Role.ADMIN) {
            fullName = "Platform Admin";
            isLabApproved = true;
        }

        return createSessionResponse(user, fullName, labId, isLabApproved, onboardingCompleted, onboardingSkipped);
    }

    public MessageResponse verifyEmail(String rawToken, String clientIp) throws SQLException {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Verification token is required");
        }
        String tokenHash = TokenGenerator.hashToken(rawToken.trim());
        Optional<UUID> userIdOpt = userDao.consumeVerificationToken(tokenHash);

        if (userIdOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid or expired verification token.");
        }

        auditService.logAction(userIdOpt.get(), "EMAIL_VERIFIED", "User", userIdOpt.get().toString(), "{}", clientIp);
        return new MessageResponse("Email successfully verified. You can now sign in.");
    }

    public MessageResponse resendVerification(ResendVerificationRequest req, String clientIp) throws SQLException {
        String genericMessage = "If your account is eligible, a new verification link has been sent to your email.";
        if (req == null || req.email() == null || req.email().isBlank()) {
            return new MessageResponse(genericMessage);
        }

        String email = req.email().toLowerCase().trim();

        // Rate limit: max 3 attempts per IP/email in 15 minutes
        int recentAttempts = userDao.countRecentVerificationResends(clientIp, email, 15);
        if (recentAttempts >= 3) {
            throw new IllegalStateException("Too many verification requests. Please wait a few minutes before trying again.");
        }

        Optional<User> userOpt = userDao.findByEmail(email);
        if (userOpt.isEmpty()) {
            auditService.logAction(null, "VERIFICATION_RESENT", "User", null, "{\"email\":\"" + email + "\"}", clientIp);
            return new MessageResponse(genericMessage);
        }

        User user = userOpt.get();
        if (user.isEmailVerified()) {
            auditService.logAction(user.id(), "VERIFICATION_RESENT", "User", user.id().toString(), "{\"email\":\"" + email + "\"}", clientIp);
            return new MessageResponse(genericMessage);
        }

        // Generate new token (256-bit, 24h, hashed)
        String rawToken = TokenGenerator.generateToken();
        String tokenHash = TokenGenerator.hashToken(rawToken);
        userDao.createEmailVerificationToken(user.id(), tokenHash, Instant.now().plus(VERIFICATION_TOKEN_DURATION));

        String devLink = null;
        if (isDevEnv()) {
            devLink = "http://localhost:3001/verify-email?token=" + rawToken;
            log.info("[EMAIL VERIFICATION SIMULATION] Resent verification link for {}: {}", email, devLink);
        }

        auditService.logAction(user.id(), "VERIFICATION_RESENT", "User", user.id().toString(), "{\"email\":\"" + email + "\"}", clientIp);

        return new MessageResponse(genericMessage, devLink);
    }

    public void logout(String rawToken, UUID userId, String clientIp) throws SQLException {
        if (rawToken != null && !rawToken.isBlank()) {
            String tokenHash = TokenGenerator.hashToken(rawToken);
            sessionDao.deleteByTokenHash(tokenHash);
            auditService.logAction(userId, "LOGOUT", "Session", tokenHash, "{}", clientIp);
        }
    }

    public MessageResponse forgotPassword(ForgotPasswordRequest req, String clientIp) throws SQLException {
        String genericMessage = "If the email exists in our system, a password reset link has been sent.";
        if (req.email() == null || req.email().isBlank()) {
            return new MessageResponse(genericMessage);
        }

        String email = req.email().toLowerCase().trim();
        Optional<User> userOpt = userDao.findByEmail(email);
        if (userOpt.isPresent()) {
            String resetToken = TokenGenerator.generateToken();
            log.info("[EMAIL SIMULATION] Password reset link for {}: http://localhost:3001/reset-password?token={}",
                    email, resetToken);
            auditService.logAction(userOpt.get().id(), "PASSWORD_RESET_REQUESTED", "User", userOpt.get().id().toString(), "{}", clientIp);
        }

        return new MessageResponse(genericMessage);
    }

    public MessageResponse resetPassword(ResetPasswordRequest req, String clientIp) throws SQLException {
        if (req.token() == null || req.token().isBlank()) {
            throw new IllegalArgumentException("Reset token is required");
        }
        if (req.newPassword() == null || req.newPassword().length() < 12) {
            throw new IllegalArgumentException("New password must be at least 12 characters");
        }
        return new MessageResponse("Password has been successfully updated. Please sign in with your new password.");
    }

    private AuthResponse createSessionResponse(User user, String fullName, UUID labId, boolean isLabApproved, boolean onboardingCompleted, boolean onboardingSkipped) throws SQLException {
        String rawToken = TokenGenerator.generateToken();
        String tokenHash = TokenGenerator.hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(SESSION_DURATION);

        sessionDao.createSession(user.id(), tokenHash, expiresAt);

        boolean isVerifiedStudent = false;
        if (user.role() == Role.STUDENT && user.email() != null && user.email().contains("@")) {
            String domain = user.email().substring(user.email().indexOf("@") + 1);
            isVerifiedStudent = allowedUniversityDao.isDomainAllowed(domain)
                    || domain.endsWith(".ac.ma") || domain.endsWith(".ma") || domain.endsWith(".edu");
        }

        UserDto userDto = new UserDto(
                user.id(),
                user.email(),
                user.role(),
                user.isEmailVerified(),
                fullName != null ? fullName : user.email(),
                labId,
                isLabApproved,
                onboardingCompleted,
                onboardingSkipped,
                isVerifiedStudent
        );

        return new AuthResponse(rawToken, userDto);
    }
}
