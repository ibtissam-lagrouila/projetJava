package com.fitwell.service;

import com.fitwell.core.json.JsonCodec;
import com.fitwell.dao.SessionDao;
import com.fitwell.dao.StudentProfileDao;
import com.fitwell.dao.StudentProfileDao.*;
import com.fitwell.service.CvExtractorService.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public final class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    private final StudentProfileDao studentProfileDao;
    private final SessionDao sessionDao;
    private final CvExtractorService cvExtractorService;
    private final AuditService auditService;

    public StudentService(StudentProfileDao studentProfileDao,
                          SessionDao sessionDao,
                          CvExtractorService cvExtractorService,
                          AuditService auditService) {
        this.studentProfileDao = studentProfileDao;
        this.sessionDao = sessionDao;
        this.cvExtractorService = cvExtractorService;
        this.auditService = auditService;
    }

    public record OnboardingCompleteRequest(
            String fullName,
            String university,
            String level,
            List<String> skills,
            List<String> languages,
            List<ExtractedProject> projects,
            List<String> interests,
            LocalDate availabilityStart,
            int availabilityDurationMonths,
            String cvFileName,
            String cvSavedPath,
            String rawCvText
    ) {}

    public record ConfirmCvRequest(
            UUID cvId,
            String education,
            String university,
            List<String> skills,
            List<String> languages,
            List<ExtractedProject> projects
    ) {}

    public record CvUploadResponse(
            UUID cvId,
            String originalFileName,
            String savedStoragePath,
            long fileSizeKb,
            ExtractedCvResult extracted
    ) {}

    public record OnboardingStateResponse(
            int step,
            boolean onboardingCompleted,
            boolean onboardingSkipped,
            boolean hasDraft,
            Map<String, Object> draft
    ) {}

    public CvUploadResponse handleCvUpload(UUID userId, String originalFileName, byte[] fileBytes, String uploadDir, String clientIp) throws IOException, SQLException {
        if (!cvExtractorService.isValidPdf(fileBytes)) {
            throw new IllegalArgumentException("Invalid file format. Uploaded file must be a valid PDF document with %PDF- header.");
        }
        if (fileBytes.length > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("File size exceeds maximum allowed limit of 5MB");
        }

        UUID studentProfileId = getOrCreateStudentProfileId(userId);

        // Rate limiting check: max 10 uploads per hour per student
        int recentUploads = studentProfileDao.countRecentCvUploads(studentProfileId);
        if (recentUploads >= 10) {
            throw new IllegalStateException("Rate limit exceeded: maximum 10 CV uploads per hour. Please wait before trying again.");
        }

        // Save PDF with random UUID outside public folder
        Path storageDir = Paths.get(uploadDir, "cvs");
        Files.createDirectories(storageDir);

        String secureFileName = UUID.randomUUID() + ".pdf";
        Path targetPath = storageDir.resolve(secureFileName);
        Files.write(targetPath, fileBytes);

        // Extract text and parse fields (timeout protected)
        String extractedText = cvExtractorService.extractTextFromPdf(fileBytes);
        ExtractedCvResult result = cvExtractorService.parseCvText(extractedText);

        long sizeKb = Math.max(1, fileBytes.length / 1024);

        // Save as DRAFT in cvs table (does not write to student_profiles, student_skills or student_projects until confirmation!)
        Map<String, Object> draftJson = new LinkedHashMap<>();
        draftJson.put("status", "DRAFT");
        draftJson.put("fileName", originalFileName);
        draftJson.put("fileSizeKb", sizeKb);
        draftJson.put("education", result.education());
        draftJson.put("skills", result.skills());
        draftJson.put("languages", result.languages());
        draftJson.put("projects", result.projects());
        draftJson.put("fieldsReadCount", result.fieldsReadCount());
        draftJson.put("checkThisWarning", result.checkThisWarning());
        draftJson.put("noTextFound", result.noTextFound());

        UUID cvId = studentProfileDao.recordDraftCv(studentProfileId, originalFileName, targetPath.toString(), extractedText, draftJson);

        // Audit log action WITHOUT recording CV text or contents
        auditService.logAction(userId, "STUDENT_CV_DRAFT_UPLOADED", "cvs", cvId.toString(), clientIp);

        log.info("Successfully extracted CV draft {} for student {}: {} fields read, warning: {}",
                cvId, studentProfileId, result.fieldsReadCount(), result.checkThisWarning());

        return new CvUploadResponse(cvId, originalFileName, targetPath.toString(), sizeKb, result);
    }

    public OnboardingStateResponse getOnboardingState(UUID userId) throws SQLException {
        StudentProfileDao.OnboardingStatus status = studentProfileDao.getOnboardingStatus(userId);
        Optional<StudentFullProfile> profileOpt = studentProfileDao.findFullProfileByUserId(userId);
        if (profileOpt.isEmpty()) {
            return new OnboardingStateResponse(2, status.completed(), status.skipped(), false, null);
        }

        StudentFullProfile profile = profileOpt.get();

        Optional<DraftCvRecord> cvOpt = studentProfileDao.findLatestDraftCv(profile.id());
        if (cvOpt.isEmpty()) {
            cvOpt = studentProfileDao.findLatestCv(profile.id());
        }

        boolean hasDraft = cvOpt.isPresent();
        Map<String, Object> draftData = null;

        if (hasDraft) {
            DraftCvRecord draft = cvOpt.get();
            draftData = new LinkedHashMap<>();
            draftData.put("cvId", draft.id().toString());
            draftData.put("fileName", draft.fileName());
            draftData.put("parsedJson", draft.parsedJson());
            draftData.put("createdAt", draft.createdAt().toString());
        } else if ((profile.skills() != null && !profile.skills().isEmpty()) || (profile.level() != null && !profile.level().isBlank())) {
            // Even if no CV uploaded yet, populate with current profile fields
            Map<String, Object> fallbackJson = new LinkedHashMap<>();
            fallbackJson.put("education", profile.level() != null ? profile.level() : "");
            fallbackJson.put("university", profile.university() != null ? profile.university() : "");
            fallbackJson.put("skills", profile.skills() != null ? profile.skills() : List.of());
            fallbackJson.put("languages", profile.languages() != null ? profile.languages() : List.of());
            List<Map<String, Object>> projs = new ArrayList<>();
            if (profile.projects() != null) {
                for (var p : profile.projects()) {
                    Map<String, Object> pm = new LinkedHashMap<>();
                    pm.put("title", p.title());
                    pm.put("description", p.description());
                    pm.put("skills", p.skills());
                    pm.put("resultSummary", p.resultSummary());
                    projs.add(pm);
                }
            }
            fallbackJson.put("projects", projs);
            fallbackJson.put("fileSizeKb", 214);

            draftData = new LinkedHashMap<>();
            draftData.put("cvId", UUID.randomUUID().toString());
            draftData.put("fileName", "CV-" + profile.fullName().replace(" ", "_") + ".pdf");
            draftData.put("parsedJson", JsonCodec.toJson(fallbackJson));
            draftData.put("createdAt", Instant.now().toString());
            hasDraft = true;
        }

        return new OnboardingStateResponse(2, status.completed(), status.skipped(), hasDraft, draftData);
    }

    public DraftCvRecord getDraftCv(UUID userId, UUID cvId) throws SQLException {
        UUID studentProfileId = getOrCreateStudentProfileId(userId);
        return studentProfileDao.findDraftCvById(cvId, studentProfileId)
                .orElseThrow(() -> new NoSuchElementException("CV draft not found"));
    }

    public void confirmCv(UUID userId, ConfirmCvRequest req, String clientIp) throws SQLException {
        if (req.cvId() == null) {
            throw new IllegalArgumentException("cvId is required for confirmation");
        }

        // Server-side validation
        if (req.education() != null && req.education().length() > 255) {
            throw new IllegalArgumentException("Education field exceeds maximum length of 255 characters");
        }
        if (req.skills() != null && req.skills().size() > 50) {
            throw new IllegalArgumentException("Too many skills submitted (maximum 50)");
        }
        if (req.languages() != null && req.languages().size() > 20) {
            throw new IllegalArgumentException("Too many languages submitted (maximum 20)");
        }
        if (req.projects() != null && req.projects().size() > 20) {
            throw new IllegalArgumentException("Too many projects submitted (maximum 20)");
        }

        UUID studentProfileId = getOrCreateStudentProfileId(userId);

        // Check draft exists and belongs to this student (throws NoSuchElementException -> 404 if not found)
        if (studentProfileDao.findDraftCvById(req.cvId(), studentProfileId).isEmpty()) {
            throw new NoSuchElementException("CV draft not found or unauthorized");
        }

        Map<String, Object> confirmedJson = new LinkedHashMap<>();
        confirmedJson.put("status", "CONFIRMED");
        confirmedJson.put("education", req.education());
        confirmedJson.put("university", req.university());
        confirmedJson.put("skills", req.skills());
        confirmedJson.put("languages", req.languages());
        confirmedJson.put("projects", req.projects());
        confirmedJson.put("confirmedAt", Instant.now().toString());

        studentProfileDao.confirmCvDraft(
                req.cvId(),
                studentProfileId,
                req.education(),
                req.university(),
                req.skills(),
                req.languages(),
                req.projects(),
                confirmedJson
        );

        auditService.logAction(userId, "STUDENT_ONBOARDING_CV_CONFIRMED", "cvs", req.cvId().toString(), clientIp);
        log.info("Student {} successfully confirmed CV draft {}", userId, req.cvId());
    }

    public void skipOnboarding(UUID userId, String clientIp) throws SQLException {
        studentProfileDao.skipOnboarding(userId);
        auditService.logAction(userId, "STUDENT_ONBOARDING_SKIPPED", "student_profiles", userId.toString(), clientIp);
        log.info("Student {} skipped CV onboarding step", userId);
    }

    public void completeOnboarding(UUID userId, OnboardingCompleteRequest req, String clientIp) throws SQLException {
        UUID studentProfileId = getOrCreateStudentProfileId(userId);

        // Update profile preserving already extracted education / university
        var existingProfile = studentProfileDao.findFullProfileByUserId(userId).orElse(null);
        String finalFullName = (req.fullName() != null && !req.fullName().isBlank()) ? req.fullName()
                : (existingProfile != null && existingProfile.fullName() != null ? existingProfile.fullName() : "Student");
        String finalUniversity = (req.university() != null && !req.university().isBlank()) ? req.university()
                : (existingProfile != null && existingProfile.university() != null ? existingProfile.university() : "Moroccan University");
        String finalLevel = (req.level() != null && !req.level().isBlank()) ? req.level()
                : (existingProfile != null && existingProfile.level() != null ? existingProfile.level() : "Master 1");

        studentProfileDao.updateProfileDetails(
                userId,
                finalFullName,
                finalUniversity,
                finalLevel,
                req.languages() != null && !req.languages().isEmpty() ? req.languages()
                        : (existingProfile != null && existingProfile.languages() != null ? existingProfile.languages() : List.of("Arabic", "French", "English")),
                req.interests() != null && !req.interests().isEmpty() ? req.interests()
                        : (existingProfile != null && existingProfile.interests() != null && !existingProfile.interests().isEmpty() ? existingProfile.interests() : List.of("Web Development", "AI/ML")),
                req.availabilityStart() != null ? req.availabilityStart() : LocalDate.now().plusMonths(1),
                req.availabilityDurationMonths() > 0 ? req.availabilityDurationMonths() : 4
        );

        // Save verified skills
        if (req.skills() != null && !req.skills().isEmpty()) {
            studentProfileDao.replaceStudentSkills(studentProfileId, req.skills());
        }

        // Save verified projects
        if (req.projects() != null && !req.projects().isEmpty()) {
            List<StudentProjectRecord> projectRecords = req.projects().stream()
                    .map(p -> new StudentProjectRecord(UUID.randomUUID(), p.title(), p.description(), p.skills(), p.resultSummary()))
                    .toList();
            studentProfileDao.replaceStudentProjects(studentProfileId, projectRecords);
        }

        // Record master CV if provided
        if (req.cvSavedPath() != null && !req.cvSavedPath().isBlank()) {
            studentProfileDao.recordUploadedCv(
                    studentProfileId,
                    req.cvFileName() != null ? req.cvFileName() : "CV.pdf",
                    req.cvSavedPath(),
                    req.rawCvText() != null ? req.rawCvText() : "",
                    req
            );
        }

        auditService.logAction(userId, "STUDENT_ONBOARDING_COMPLETED", "StudentProfile", studentProfileId.toString(), clientIp);
    }

    public StudentFullProfile getProfile(UUID userId) throws SQLException {
        return studentProfileDao.findFullProfileByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Student profile not found"));
    }

    public void updateProfile(UUID userId, String fullName, String university, String level,
                              List<String> languages, List<String> interests,
                              LocalDate availabilityStart, int durationMonths, String clientIp) throws SQLException {
        studentProfileDao.updateProfileDetails(
                userId, fullName, university, level, languages, interests, availabilityStart, durationMonths
        );
        auditService.logAction(userId, "STUDENT_PROFILE_UPDATED", "StudentProfile", userId.toString(), clientIp);
    }

    public void setAiAssistance(UUID userId, boolean enabled, String clientIp) throws SQLException {
        studentProfileDao.updateAiAssistance(userId, enabled);
        auditService.logAction(userId, "STUDENT_AI_ASSISTANCE_TOGGLED", "StudentProfile", userId.toString(), clientIp);
    }

    public String updateProfilePhoto(UUID userId, byte[] photoBytes, String contentType, String uploadDir) throws IOException, SQLException {
        if (photoBytes == null || photoBytes.length == 0) {
            throw new IllegalArgumentException("Empty file uploaded");
        }
        if (photoBytes.length > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("Photo exceeds maximum limit of 2MB");
        }

        Path photoDir = Paths.get(uploadDir, "photos");
        Files.createDirectories(photoDir);

        String ext = ".jpg";
        if (contentType != null && contentType.contains("png")) ext = ".png";

        String fileName = UUID.randomUUID() + ext;
        Path target = photoDir.resolve(fileName);
        Files.write(target, photoBytes);

        String photoPath = "/uploads/photos/" + fileName;
        studentProfileDao.updatePhoto(userId, photoPath);
        return photoPath;
    }

    public Map<String, Object> exportUserData(UUID userId, String clientIp) throws SQLException {
        auditService.logAction(userId, "STUDENT_DATA_EXPORTED", "users", userId.toString(), clientIp);
        return studentProfileDao.exportAllData(userId);
    }

    public void deleteAccount(UUID userId, String clientIp) throws SQLException {
        auditService.logAction(userId, "STUDENT_ACCOUNT_DELETED", "users", userId.toString(), clientIp);
        if (sessionDao != null) {
            sessionDao.deleteAllForUser(userId);
        }
        studentProfileDao.deleteUserAccount(userId);
    }

    private UUID getOrCreateStudentProfileId(UUID userId) throws SQLException {
        Optional<StudentFullProfile> currentProfile = studentProfileDao.findFullProfileByUserId(userId);
        if (currentProfile.isPresent()) {
            return currentProfile.get().id();
        }
        studentProfileDao.createProfile(userId, "Student", "Moroccan University", "Master 1");
        return studentProfileDao.findByUserId(userId).map(StudentProfileInfo::id).orElseThrow();
    }
}
