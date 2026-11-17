package com.fitwell.service;

import com.fitwell.dao.OfferDao.OfferRecord;
import com.fitwell.dao.StudentProfileDao.StudentFullProfile;
import com.fitwell.dao.StudentProfileDao.StudentProjectRecord;
import com.fitwell.model.dto.OfferDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MatchingServiceTest {

    private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingService = new MatchingService();
    }

    @Test
    @DisplayName("Deterministic math: 3 required (weight 3 each) and 1 optional (weight 1) -> 90% when only required are met")
    void testDeterministicSkillsCoverageCalculation() {
        UUID offerId = UUID.randomUUID();
        LocalDate now = LocalDate.now();

        List<SkillItem> offerSkills = List.of(
                new SkillItem(UUID.randomUUID(), "Python", "python", true),    // w = 3.0
                new SkillItem(UUID.randomUUID(), "PyTorch", "pytorch", true),  // w = 3.0
                new SkillItem(UUID.randomUUID(), "OpenCV", "opencv", true),    // w = 3.0
                new SkillItem(UUID.randomUUID(), "DICOM", "dicom", false)      // w = 1.0 (total weight = 10.0)
        );

        OfferRecord offer = new OfferRecord(
                offerId, UUID.randomUUID(), "Vision and Imaging Lab", "Université Mohammed V", "Rabat",
                "Vision models for medical imaging", "Medical imaging, Computer Vision",
                6, now.plusMonths(1), "Rabat", "Funded", "Gratification mensuelle",
                "Train segmentation models on annotated microscopy images.",
                List.of("Build dataset", "Train models"), 2, "PUBLISHED", "LAB_POSTED",
                null, null,
                now.plusDays(17), 17, offerSkills
        );

        // Student has Python, PyTorch, OpenCV (earned weight = 3 + 3 + 3 = 9.0; total = 10.0 => 90% skills score)
        StudentFullProfile student = new StudentFullProfile(
                UUID.randomUUID(), UUID.randomUUID(), "Yassine Bennis", "Université Hassan II", "Master 1",
                List.of("French", "English"),
                List.of("Medical imaging", "Computer Vision"),
                now.plusMonths(1), 6, null, true,
                List.of("Python", "PyTorch", "OpenCV"),
                List.of(new StudentProjectRecord(
                        UUID.randomUUID(), "Image segmentation", "Trained U-Net in PyTorch",
                        List.of("Python", "PyTorch", "OpenCV"), "0.89 Dice score"
                ))
        );

        ScoreBreakdown score = matchingService.computeScore(student, offer);

        // Skills: earned 9.0 / 10.0 = 90%
        assertEquals(90, score.skillsScore());

        // Interest: "Medical imaging" exact match in domain -> 95%
        assertEquals(95, score.interestScore());

        // Dates: exact start date (0 days diff -> 100) and duration 6 >= 6 -> 100. Dates score = 100%
        assertEquals(100, score.datesScore());

        // Overall: round(0.50 * 90 + 0.30 * 95 + 0.20 * 100) = round(45.0 + 28.5 + 20.0) = round(93.5) = 94%
        assertEquals(94, score.overallScore());
        assertEquals("Excellent match", score.matchLabel());

        // Gap to close: DICOM is missing and optional
        assertEquals(1, score.gapToClose().size());
        assertEquals("DICOM", score.gapToClose().get(0).name());
        assertFalse(score.gapToClose().get(0).isRequired());

        // "Why Fitwell picked it" explanation contains student's project
        assertTrue(score.whyPicked().contains("segmentation"), "Explanation should reference project title");
        assertTrue(score.whyPicked().contains("PyTorch") || score.whyPicked().contains("OpenCV"));
    }

    @Test
    @DisplayName("Deterministic math: 100% when student owns all required and optional skills")
    void testPerfectMatchCalculation() {
        UUID offerId = UUID.randomUUID();
        LocalDate now = LocalDate.now();

        List<SkillItem> offerSkills = List.of(
                new SkillItem(UUID.randomUUID(), "Python", "python", true),
                new SkillItem(UUID.randomUUID(), "R", "r", true),
                new SkillItem(UUID.randomUUID(), "Bioinformatics", "bioinformatics", false)
        );

        OfferRecord offer = new OfferRecord(
                offerId, UUID.randomUUID(), "Genomics Unit", "FMP Fès", "Fès",
                "Gene expression analysis", "Genomics & Bioinformatics",
                4, now.plusMonths(1), "Fès", "Funded", "Paid",
                "RNA-seq analysis", List.of("Run pipeline"), 1, "PUBLISHED", "LAB_POSTED",
                null, null,
                now.plusDays(30), 30, offerSkills
        );

        StudentFullProfile student = new StudentFullProfile(
                UUID.randomUUID(), UUID.randomUUID(), "Sara Tahiri", "USMBA Fès", "Master 2",
                List.of("English"),
                List.of("Genomics & Bioinformatics"),
                now.plusMonths(1), 4, null, true,
                List.of("Python", "R", "Bioinformatics"),
                List.of()
        );

        ScoreBreakdown score = matchingService.computeScore(student, offer);

        assertEquals(100, score.skillsScore());
        assertEquals(95, score.interestScore());
        assertEquals(100, score.datesScore());
        // Overall: round(0.50 * 100 + 0.30 * 95 + 0.20 * 100) = 50 + 28.5 + 20 = round(98.5) = 99%
        assertEquals(99, score.overallScore());
        assertEquals("Excellent match", score.matchLabel());
        assertTrue(score.gapToClose().isEmpty(), "No missing skills");
    }

    @Test
    @DisplayName("Deterministic math: partial required skills carrying 3x weight vs optional")
    void testRequiredSkillsCarry3xWeight() {
        UUID offerId = UUID.randomUUID();
        LocalDate now = LocalDate.now();

        List<SkillItem> offerSkills = List.of(
                new SkillItem(UUID.randomUUID(), "PyTorch", "pytorch", true),    // w = 3.0
                new SkillItem(UUID.randomUUID(), "Docker", "docker", false)      // w = 1.0 (total = 4.0)
        );

        OfferRecord offer = new OfferRecord(
                offerId, UUID.randomUUID(), "AI Lab", "UM5 Rabat", "Rabat",
                "Deep Learning", "Artificial Intelligence",
                4, now, "Rabat", "Funded", null,
                "DL models", List.of("Task 1"), 1, "PUBLISHED", "LAB_POSTED",
                null, null,
                now.plusDays(10), 10, offerSkills
        );

        // Student A has ONLY PyTorch (required): earned = 3.0 / 4.0 = 75%
        StudentFullProfile studentA = new StudentFullProfile(
                UUID.randomUUID(), UUID.randomUUID(), "Student A", "UM5", "M1",
                List.of(), List.of("Artificial Intelligence"), now, 4, null, true,
                List.of("PyTorch"), List.of()
        );

        // Student B has ONLY Docker (optional): earned = 1.0 / 4.0 = 25%
        StudentFullProfile studentB = new StudentFullProfile(
                UUID.randomUUID(), UUID.randomUUID(), "Student B", "UM5", "M1",
                List.of(), List.of("Artificial Intelligence"), now, 4, null, true,
                List.of("Docker"), List.of()
        );

        ScoreBreakdown scoreA = matchingService.computeScore(studentA, offer);
        ScoreBreakdown scoreB = matchingService.computeScore(studentB, offer);

        assertEquals(75, scoreA.skillsScore(), "3 out of 4 weight must equal exactly 75%");
        assertEquals(25, scoreB.skillsScore(), "1 out of 4 weight must equal exactly 25%");
        assertTrue(scoreA.skillsScore() > scoreB.skillsScore());
    }

    @Test
    @DisplayName("Match labels correspond accurately to project score thresholds")
    void testScoreThresholdLabels() {
        UUID offerId = UUID.randomUUID();
        LocalDate now = LocalDate.now();

        OfferRecord offer = new OfferRecord(
                offerId, UUID.randomUUID(), "Lab", "Univ", "City",
                "Title", "General Domain",
                4, now, "City", "Funded", null,
                "Desc", List.of(), 1, "PUBLISHED", "LAB_POSTED",
                null, null,
                now.plusDays(10), 10, List.of()
        );

        // When offer has no skills, skills score = 100
        StudentFullProfile studentExcellent = new StudentFullProfile(
                UUID.randomUUID(), UUID.randomUUID(), "High Match", "Univ", "M1",
                List.of(), List.of("General Domain"), now, 4, null, true,
                List.of(), List.of()
        );
        ScoreBreakdown scoreExc = matchingService.computeScore(studentExcellent, offer);
        assertTrue(scoreExc.overallScore() >= 90);
        assertEquals("Excellent match", scoreExc.matchLabel());
    }
}

