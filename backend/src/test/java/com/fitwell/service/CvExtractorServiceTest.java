package com.fitwell.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CvExtractorServiceTest {

    private CvExtractorService extractor;

    @BeforeEach
    void setUp() {
        extractor = new CvExtractorService();
    }

    private byte[] createSamplePdf(String content) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            if (content != null && !content.isEmpty()) {
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(content);
                    cs.endText();
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("Should validate %PDF- magic bytes properly; text file renamed to .pdf must be rejected")
    void shouldValidatePdfMagicBytes() throws IOException {
        byte[] validPdf = createSamplePdf("Test CV content");
        assertTrue(extractor.isValidPdf(validPdf), "Valid PDF must be recognized via %PDF- header");

        byte[] fakeFile = "<html><body>Fake PDF with extension .pdf</body></html>".getBytes();
        assertFalse(extractor.isValidPdf(fakeFile), "Renamed HTML/Text file must be rejected");

        assertFalse(extractor.isValidPdf(new byte[]{0x00, 0x01}), "Short byte array must be rejected");
        assertFalse(extractor.isValidPdf(null), "Null must be rejected");
    }

    @Test
    @DisplayName("Should extract text from PDF using PDFBox")
    void shouldExtractTextFromPdf() throws IOException {
        byte[] pdf = createSamplePdf("Master 1 Data science student with PyTorch and Python");
        String text = extractor.extractTextFromPdf(pdf);

        assertNotNull(text);
        assertTrue(text.contains("Master 1"));
        assertTrue(text.contains("PyTorch"));
    }

    @Test
    @DisplayName("Scanned or blank PDF returns no text found without inventing any content")
    void shouldHandlePdfWithNoText() throws IOException {
        byte[] blankPdf = createSamplePdf(""); // Empty content
        String text = extractor.extractTextFromPdf(blankPdf);

        var result = extractor.parseCvText(text);

        assertTrue(result.noTextFound(), "Must flag noTextFound when text is blank");
        assertEquals(0, result.fieldsReadCount(), "Fields count must be 0 for blank/scanned PDF");
        assertTrue(result.skills().isEmpty(), "Must NEVER invent skills when no text is found");
        assertTrue(result.languages().isEmpty(), "Must NEVER invent languages when no text is found");
        assertTrue(result.education().isEmpty(), "Must NEVER invent education when no text is found");
        assertTrue(result.projects().isEmpty(), "Must NEVER invent projects when no text is found");
        assertNotNull(result.checkThisWarning());
        assertTrue(result.checkThisWarning().contains("No text could be extracted"));
    }

    @Test
    @DisplayName("Should parse education, skills, languages, and calculate exact fieldsReadCount")
    void shouldParseCvTextFaithfully() {
        String cvText = """
            Yassine Bennis
            Master 1, Data science - Casablanca
            Skills: Python, PyTorch, OpenCV, SQL, Git
            Languages: Arabic, French, English
            Projects:
            - Image segmentation with U-Net in PyTorch
            - Research abstracts scraper with Python and SQL: 12,000 papers indexed with 94% precision
        """;

        var result = extractor.parseCvText(cvText);

        assertEquals("Master 1, Data science", result.education());
        assertTrue(result.skills().contains("Python"));
        assertTrue(result.skills().contains("PyTorch"));
        assertTrue(result.skills().contains("OpenCV"));
        assertTrue(result.skills().contains("SQL"));
        assertTrue(result.skills().contains("Git"));
        assertTrue(result.languages().contains("Arabic"));
        assertTrue(result.languages().contains("French"));
        assertTrue(result.languages().contains("English"));
        assertEquals(2, result.projects().size());

        // fieldsCount: 1 education + 5 skills + 3 languages + 2 projects = 11
        assertEquals(11, result.fieldsReadCount());

        // Verifies the "Check this" flag: project 1 has no result numbers
        assertNotNull(result.checkThisWarning());
        assertTrue(result.checkThisWarning().contains("Check this: one project has no result"));
    }

    @Test
    @DisplayName("Check this banner is completely hidden when all fields are present and projects have numbers")
    void checkThisHiddenWhenNoDoubts() {
        String cvText = """
            Master 1, Data science
            Skills: Python, PyTorch, OpenCV, SQL
            Languages: Arabic, French
            Projects:
            - Research abstracts scraper: 12,000 papers indexed with 94% precision
        """;

        var result = extractor.parseCvText(cvText);
        // Project has "12,000" and "94%" numbers -> quantified result present!
        // Education is present, languages present, skills >= 3.
        assertNull(result.checkThisWarning(), "Check this warning must be null when all conditions are satisfied");
    }

    @Test
    @DisplayName("Sanitizes PII (emails and phone numbers) before sending to external AI providers")
    void shouldSanitizePiiForAi() {
        String raw = "Student Yassine, email yassine@univ-casa.ma and phone +212612345678, skilled in PyTorch";
        String sanitized = extractor.sanitizePiiForAi(raw);

        assertFalse(sanitized.contains("yassine@univ-casa.ma"));
        assertFalse(sanitized.contains("+212612345678"));
        assertTrue(sanitized.contains("[EMAIL_REDACTED]"));
        assertTrue(sanitized.contains("[PHONE_REDACTED]"));
        assertTrue(sanitized.contains("PyTorch"));
    }
}
