package com.fitwell.service;

import com.fitwell.api.student.StudentHandler;
import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.HttpStatus;
import com.fitwell.core.http.RequestContext;
import com.fitwell.core.http.Router;
import com.fitwell.core.middleware.AuthMiddleware;
import com.fitwell.core.middleware.ErrorHandlingMiddleware;
import com.fitwell.core.middleware.RoleGuardMiddleware;
import com.fitwell.core.security.AuthContext;
import com.fitwell.dao.StudentProfileDao;
import com.fitwell.model.entity.User;
import com.fitwell.model.enums.Role;
import com.fitwell.service.CvExtractorService.ExtractedCvResult;
import com.fitwell.service.CvExtractorService.ExtractedProject;
import com.fitwell.service.StudentService.ConfirmCvRequest;
import com.fitwell.service.StudentService.CvUploadResponse;
import com.fitwell.service.StudentService.OnboardingStateResponse;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudentOnboardingSecurityTest {

    private CvExtractorService cvExtractorService;
    private StudentProfileDao studentProfileDao;
    private AuditService auditService;
    private StudentService studentService;
    private StudentHandler studentHandler;
    private Router router;

    private static final String TEMP_UPLOAD_DIR = System.getProperty("java.io.tmpdir") + File.separator + "fitwell_test_uploads_" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cvExtractorService = new CvExtractorService();
        studentProfileDao = Mockito.mock(StudentProfileDao.class);
        auditService = Mockito.mock(AuditService.class);
        studentService = new StudentService(studentProfileDao, null, cvExtractorService, auditService);
        studentHandler = new StudentHandler(studentService, TEMP_UPLOAD_DIR);

        router = new Router();
        router.use(new ErrorHandlingMiddleware());
        RoleGuardMiddleware studentGuard = new RoleGuardMiddleware(Role.STUDENT);

        router.post("/api/student/onboarding/cv", studentHandler::uploadCv, studentGuard);
        router.get("/api/student/onboarding/state", studentHandler::getOnboardingState, studentGuard);
        router.put("/api/student/onboarding/cv/confirm", studentHandler::confirmCv, studentGuard);
        router.post("/api/student/onboarding/skip", studentHandler::skipOnboarding, studentGuard);
        router.get("/api/student/onboarding/draft/{id}", studentHandler::getDraftCv, studentGuard);
    }

    private byte[] createSamplePdf(String content) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cos = new PDPageContentStream(doc, page)) {
                cos.beginText();
                cos.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cos.newLineAtOffset(50, 700);
                if (content != null && !content.isBlank()) {
                    for (String line : content.split("\n")) {
                        cos.showText(line);
                        cos.newLineAtOffset(0, -15);
                    }
                }
                cos.endText();
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private RequestContext createMockContext(String method, String path, AuthContext auth, byte[] body, Map<String, String> headers) {
        HttpExchange exchange = Mockito.mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn(method);
        when(exchange.getRequestURI()).thenReturn(URI.create(path));
        Headers h = new Headers();
        if (headers != null) {
            headers.forEach(h::add);
        }
        when(exchange.getRequestHeaders()).thenReturn(h);
        when(exchange.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(body != null ? body : new byte[0]));

        RequestContext ctx = new RequestContext(exchange, Collections.emptyMap(), Collections.singleton("127.0.0.1"));
        if (auth != null) {
            ctx.setAttribute(AuthMiddleware.AUTH_ATTRIBUTE, auth);
        }
        return ctx;
    }

    private User createStudentUser(UUID id) {
        return new User(id, "student@univ.ma", "hash", "salt", Role.STUDENT, Instant.now(), true, Instant.now(), Instant.now());
    }

    private StudentProfileDao.StudentFullProfile createMockProfile(UUID profileId, UUID userId) {
        return new StudentProfileDao.StudentFullProfile(
                profileId, userId, "Yassine Bennis", "UH2C", "Master 1",
                List.of("Arabic", "French", "English"),
                List.of("Machine Learning"),
                null, 4, null, true,
                List.of("Python", "SQL"),
                List.of()
        );
    }

    @Test
    @DisplayName("Valid PDF: extracted fields, counter and draft are consistent")
    void validPdfUploadReturnsExtractedFieldsAndConsistentCounter() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User student = createStudentUser(userId);
        AuthContext auth = new AuthContext(student, null, null);

        when(studentProfileDao.findFullProfileByUserId(userId)).thenReturn(Optional.of(createMockProfile(profileId, userId)));
        when(studentProfileDao.countRecentCvUploads(profileId)).thenReturn(0);
        when(studentProfileDao.recordDraftCv(eq(profileId), anyString(), anyString(), anyString(), any())).thenReturn(UUID.randomUUID());

        String cvText = """
                Yassine Bennis
                Education: Master 1, Data science
                Skills: Python, PyTorch, SQL, OpenCV, Git
                Languages: Arabic, French, English
                Projects:
                Image segmentation: Biomedical scan segmentation with U-Net
                Research abstracts scraper: Indexed 12000 papers with 94% precision
                """;
        byte[] pdfBytes = createSamplePdf(cvText);

        RequestContext ctx = createMockContext("POST", "/api/student/onboarding/cv", auth, pdfBytes, Map.of(
                "Content-Type", "application/pdf",
                "X-File-Name", "CV-Yassine.pdf"
        ));

        HttpResponse res = router.dispatch(ctx);
        assertEquals(HttpStatus.OK, res.getStatus());
        String body = new String(res.getBody());
        assertTrue(body.contains("Master 1, Data science"));
        assertTrue(body.contains("Python"));
        assertTrue(body.contains("fieldsReadCount"));
    }

    @Test
    @DisplayName("File without %PDF- magic bytes (e.g. text or HTML renamed to .pdf) is strictly rejected")
    void nonPdfMagicBytesRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        User student = createStudentUser(userId);
        AuthContext auth = new AuthContext(student, null, null);

        byte[] fakePdf = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        RequestContext ctx = createMockContext("POST", "/api/student/onboarding/cv", auth, fakePdf, Map.of(
                "Content-Type", "application/pdf",
                "X-File-Name", "exploit.pdf"
        ));

        HttpResponse res = router.dispatch(ctx);
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatus());
        assertTrue(new String(res.getBody()).contains("%PDF-"));
    }

    @Test
    @DisplayName("File exceeding 5MB limit is strictly rejected")
    void fileExceeding5MbRejected() {
        byte[] hugePdf = new byte[6 * 1024 * 1024]; // 6MB
        hugePdf[0] = '%';
        hugePdf[1] = 'P';
        hugePdf[2] = 'D';
        hugePdf[3] = 'F';
        hugePdf[4] = '-';

        UUID userId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                studentService.handleCvUpload(userId, "big.pdf", hugePdf, TEMP_UPLOAD_DIR, "127.0.0.1")
        );
    }

    @Test
    @DisplayName("Scanned or blank PDF with no selectable text returns noTextFound: true without inventing content")
    void blankPdfReturnsNoTextFoundWithoutHallucination() throws Exception {
        byte[] blankPdf = createSamplePdf(""); // Empty content
        ExtractedCvResult result = cvExtractorService.parseCvText(cvExtractorService.extractTextFromPdf(blankPdf));

        assertTrue(result.noTextFound());
        assertEquals("", result.education());
        assertTrue(result.skills().isEmpty());
        assertTrue(result.languages().isEmpty());
        assertTrue(result.projects().isEmpty());
        assertEquals(0, result.fieldsReadCount());
    }

    @Test
    @DisplayName("DRAFT upload does NOT alter student profile, skills, or projects before confirmation")
    void draftUploadDoesNotAlterProfileBeforeConfirmation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        User student = createStudentUser(userId);
        AuthContext auth = new AuthContext(student, null, null);

        when(studentProfileDao.findFullProfileByUserId(userId)).thenReturn(Optional.of(createMockProfile(profileId, userId)));
        when(studentProfileDao.countRecentCvUploads(profileId)).thenReturn(0);
        when(studentProfileDao.recordDraftCv(eq(profileId), anyString(), anyString(), anyString(), any())).thenReturn(UUID.randomUUID());

        byte[] pdfBytes = createSamplePdf("Education: Master 1, Data science\nSkills: Python, SQL");

        RequestContext ctx = createMockContext("POST", "/api/student/onboarding/cv", auth, pdfBytes, Map.of("X-File-Name", "cv.pdf"));
        HttpResponse res = router.dispatch(ctx);
        assertEquals(HttpStatus.OK, res.getStatus());

        // Verify that confirmCvDraft or updateProfile was NEVER called
        verify(studentProfileDao, never()).confirmCvDraft(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Confirmation writes student-corrected fields to profile and skills")
    void confirmationWritesCorrectedFields() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        User student = createStudentUser(userId);
        AuthContext auth = new AuthContext(student, null, null);

        when(studentProfileDao.findFullProfileByUserId(userId)).thenReturn(Optional.of(createMockProfile(profileId, userId)));
        when(studentProfileDao.findDraftCvById(cvId, profileId)).thenReturn(Optional.of(
                new StudentProfileDao.DraftCvRecord(cvId, profileId, "mycv.pdf", "/path/to/cv.pdf", "raw", "{}", Timestamp.from(Instant.now()))
        ));

        ConfirmCvRequest req = new ConfirmCvRequest(
                cvId,
                "Master 2 AI & Robotics",
                "UH2C",
                List.of("Python", "Rust", "TensorFlow"),
                List.of("Arabic", "English"),
                List.of(new ExtractedProject("Drone Navigation", "SLAM with ROS", List.of("Python"), "98% precision"))
        );

        String jsonBody = com.fitwell.core.json.JsonCodec.toJson(req);

        RequestContext ctx = createMockContext("PUT", "/api/student/onboarding/cv/confirm", auth, jsonBody.getBytes(StandardCharsets.UTF_8), Map.of(
                "Content-Type", "application/json"
        ));

        HttpResponse res = router.dispatch(ctx);
        assertEquals(HttpStatus.OK, res.getStatus());

        // Verify confirmCvDraft called with exact student corrected fields
        verify(studentProfileDao, times(1)).confirmCvDraft(
                eq(cvId),
                eq(profileId),
                eq("Master 2 AI & Robotics"),
                eq("UH2C"),
                eq(List.of("Python", "Rust", "TensorFlow")),
                eq(List.of("Arabic", "English")),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Student accessing another student's CV draft returns 404 Not Found (NOT 403) to prevent ID enumeration")
    void foreignStudentDraftReturns404NotFound() throws Exception {
        UUID student1Id = UUID.randomUUID();
        UUID student1ProfileId = UUID.randomUUID();
        UUID draftIdOfStudent2 = UUID.randomUUID();

        User student1 = createStudentUser(student1Id);
        AuthContext auth = new AuthContext(student1, null, null);

        when(studentProfileDao.findFullProfileByUserId(student1Id)).thenReturn(Optional.of(createMockProfile(student1ProfileId, student1Id)));
        // Scoped lookup returns empty when draft does not belong to student1ProfileId
        when(studentProfileDao.findDraftCvById(draftIdOfStudent2, student1ProfileId)).thenReturn(Optional.empty());

        RequestContext ctx = createMockContext("GET", "/api/student/onboarding/draft/" + draftIdOfStudent2, auth, null, null);
        HttpResponse res = router.dispatch(ctx);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatus());
        assertTrue(new String(res.getBody()).contains("not found"));
        assertFalse(new String(res.getBody()).contains("403"));
    }

    @Test
    @DisplayName("Skip for now marks onboarding as skipped")
    void skipOnboardingMarksSkipped() throws Exception {
        UUID userId = UUID.randomUUID();
        User student = createStudentUser(userId);
        AuthContext auth = new AuthContext(student, null, null);

        RequestContext ctx = createMockContext("POST", "/api/student/onboarding/skip", auth, null, null);
        HttpResponse res = router.dispatch(ctx);

        assertEquals(HttpStatus.OK, res.getStatus());
        verify(studentProfileDao, times(1)).skipOnboarding(userId);
    }

    @Test
    @DisplayName("Rate limit check: more than 10 CV uploads per hour throws IllegalStateException")
    void rateLimit10UploadsPerHour() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        when(studentProfileDao.findFullProfileByUserId(userId)).thenReturn(Optional.of(createMockProfile(profileId, userId)));
        when(studentProfileDao.countRecentCvUploads(profileId)).thenReturn(10); // Rate limit reached

        byte[] pdfBytes = createSamplePdf("Education: M1");
        assertThrows(IllegalStateException.class, () ->
                studentService.handleCvUpload(userId, "test.pdf", pdfBytes, TEMP_UPLOAD_DIR, "127.0.0.1")
        );
    }
}
