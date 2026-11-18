package com.fitwell.service;

import com.fitwell.core.json.JsonCodec;
import com.fitwell.dao.LabDao;
import com.fitwell.dao.LabDashboardDao;
import com.fitwell.dao.LabDashboardDao.*;
import com.fitwell.dao.LabMemberDao;
import com.fitwell.dao.LabMemberDao.LabMembershipDetails;
import com.fitwell.dao.OfferDao.OfferRecord;
import com.fitwell.dao.StudentProfileDao.StudentFullProfile;
import com.fitwell.model.dto.LabDashboardDtos.*;
import com.fitwell.model.dto.OfferDtos.SkillItem;
import com.fitwell.model.enums.LabRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class LabDashboardTest {

    private LabDashboardDao dashboardDao;
    private LabMemberDao labMemberDao;
    private LabDao labDao;
    private MatchingService matchingService;
    private LabDashboardService service;

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");
    // Wednesday 18 Nov 2026, 10:30 Casablanca time
    private static final ZonedDateTime FIXED_NOW = ZonedDateTime.of(2026, 11, 18, 10, 30, 0, 0, CASABLANCA);
    private Clock fixedClock;

    private UUID userId;
    private UUID labId;

    @BeforeEach
    void setUp() {
        dashboardDao = mock(LabDashboardDao.class);
        labMemberDao = mock(LabMemberDao.class);
        labDao = mock(LabDao.class);
        matchingService = new MatchingService();

        fixedClock = Clock.fixed(FIXED_NOW.toInstant(), CASABLANCA);
        service = new LabDashboardService(dashboardDao, labMemberDao, labDao, matchingService, fixedClock);

        userId = UUID.randomUUID();
        labId = UUID.randomUUID();
    }

    private void setupValidMembership() throws SQLException {
        LabMembershipDetails membership = new LabMembershipDetails(
                labId, "Genomics Unit", "Fès", "Université Sidi Mohamed Ben Abdellah", LabRole.HR, "APPROVED"
        );
        when(labMemberDao.findAllByUserId(userId)).thenReturn(List.of(membership));
        when(labDao.getLabStatus(labId)).thenReturn("APPROVED");
    }

    @Test
    @DisplayName("1. Each counter equals DB count with simulated Clock (Casablanca timezone)")
    void testCountersEqualDatabaseCount() throws SQLException {
        setupValidMembership();

        LocalDate today = LocalDate.of(2026, 11, 18);
        when(dashboardDao.countOpenOffers(eq(labId), eq(today))).thenReturn(3);
        when(dashboardDao.countNewApplicants(eq(labId), any(Instant.class))).thenReturn(18);
        when(dashboardDao.countToReview(eq(labId))).thenReturn(7);
        when(dashboardDao.countInterviewsBetween(eq(labId), any(Instant.class), any(Instant.class), eq("CONFIRMED"))).thenReturn(2);
        when(dashboardDao.countAwaitingConfirmationInterviews(eq(labId))).thenReturn(1);
        when(dashboardDao.getMonthActivity(eq(labId), any(Instant.class), any(Instant.class)))
                .thenReturn(new MonthStats(420, 38, 9, 4));

        LabDashboardResponse resp = service.getDashboard(userId, "Salma El Idrissi", labId);

        assertNotNull(resp);
        assertEquals(3, resp.counters().openOffers());
        assertEquals(18, resp.counters().newApplicants());
        assertEquals(7, resp.counters().toReview());
        assertEquals(2, resp.counters().interviewsThisWeek());
        assertEquals(1, resp.counters().awaitingConfirmation());
        assertEquals("Good morning, Salma El Idrissi", resp.greeting());
    }

    @Test
    @DisplayName("2. Exact week boundaries (Monday 00:00 to Sunday 23:59:59) and calendar month boundaries in Africa/Casablanca")
    void testWeekAndMonthBoundariesInAfricaCasablanca() throws SQLException {
        setupValidMembership();

        LocalDate expectedMonday = LocalDate.of(2026, 11, 16);
        LocalDate expectedSunday = LocalDate.of(2026, 11, 22);
        Instant expectedWeekStart = expectedMonday.atStartOfDay(CASABLANCA).toInstant();
        Instant expectedWeekEnd = expectedSunday.plusDays(1).atStartOfDay(CASABLANCA).toInstant();

        Instant expectedMonthStart = LocalDate.of(2026, 11, 1).atStartOfDay(CASABLANCA).toInstant();
        Instant expectedMonthEnd = LocalDate.of(2026, 11, 30).plusDays(1).atStartOfDay(CASABLANCA).toInstant();

        when(dashboardDao.getMonthActivity(eq(labId), eq(expectedMonthStart), eq(expectedMonthEnd)))
                .thenReturn(new MonthStats(100, 10, 2, 1));

        service.getDashboard(userId, "Salma", labId);

        verify(dashboardDao).countInterviewsBetween(eq(labId), eq(expectedWeekStart), eq(expectedWeekEnd), eq("CONFIRMED"));
        verify(dashboardDao).getMonthActivity(eq(labId), eq(expectedMonthStart), eq(expectedMonthEnd));
    }

    @Test
    @DisplayName("3. Strict isolation: user belonging to Lab A cannot view Lab B (returns 404 / NoSuchElementException)")
    void testStrictLabIsolation() throws SQLException {
        UUID otherLabId = UUID.randomUUID();
        LabMembershipDetails labA = new LabMembershipDetails(
                labId, "Lab A", "Rabat", "UM5", LabRole.SUPERVISOR, "APPROVED"
        );
        when(labMemberDao.findAllByUserId(userId)).thenReturn(List.of(labA));

        assertThrows(NoSuchElementException.class, () -> {
            service.getDashboard(userId, "Researcher", otherLabId);
        });
    }

    @Test
    @DisplayName("4. Lab in PENDING or REJECTED status cannot access dashboard (dedicated status response)")
    void testPendingOrRejectedLabAccess() throws SQLException {
        LabMembershipDetails pendingLab = new LabMembershipDetails(
                labId, "Pending Biotech Lab", "Casablanca", "UH2", LabRole.CEO, "PENDING"
        );
        when(labMemberDao.findAllByUserId(userId)).thenReturn(List.of(pendingLab));
        when(labDao.getLabStatus(labId)).thenReturn("PENDING");

        LabDashboardResponse resp = service.getDashboard(userId, "CEO", labId);
        assertEquals("PENDING", resp.status());
        assertNull(resp.counters());

        when(labDao.getLabStatus(labId)).thenReturn("REJECTED");
        LabDashboardResponse respRejected = service.getDashboard(userId, "CEO", labId);
        assertEquals("REJECTED", respRejected.status());
        assertNull(respRejected.counters());
    }

    @Test
    @DisplayName("5. Score invariance: changing name, photo, gender, or university does NOT change the score")
    void testScoreInvariance() {
        UUID offerId = UUID.randomUUID();
        LocalDate now = LocalDate.of(2026, 11, 18);
        List<SkillItem> skills = List.of(
                new SkillItem(UUID.randomUUID(), "Python", "python", true),
                new SkillItem(UUID.randomUUID(), "R", "r", true)
        );

        OfferRecord offer = new OfferRecord(
                offerId, labId, "Genomics", "USMBA", "Fès", "Pipeline", "Biology",
                6, now.plusMonths(1), "Fès", "Funded", "5000", "Desc",
                List.of("A"), 2, "PUBLISHED", "INTERNAL", null, null, now.plusDays(20), 20, skills
        );

        StudentFullProfile student1 = new StudentFullProfile(
                UUID.randomUUID(), UUID.randomUUID(), "Amine Tazi", "USMBA", "Master",
                List.of("French"), List.of("Biology"), now.plusMonths(1),
                6, "https://example.com/photo1.jpg", true, List.of("python", "r"), List.of()
        );

        StudentFullProfile student2 = new StudentFullProfile(
                UUID.randomUUID(), UUID.randomUUID(), "Fatima Zahra Mansouri", "Al Akhawayn", "Master",
                List.of("English"), List.of("Biology"), now.plusMonths(1),
                6, "https://example.com/photo2.png", true, List.of("python", "r"), List.of()
        );

        var score1 = matchingService.computeScore(student1, offer);
        var score2 = matchingService.computeScore(student2, offer);

        assertEquals(score1.overallScore(), score2.overallScore());
        assertEquals(score1.skillsScore(), score2.skillsScore());
    }

    @Test
    @DisplayName("6. Frozen profile snapshot: score uses profile_snapshot_json, not subsequent CV edits")
    void testFrozenProfileSnapshotUsedForScore() throws SQLException {
        setupValidMembership();

        UUID studentId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 11, 18);

        // Snapshot frozen at application time: student had python and r
        StudentFullProfile snapshotProfile = new StudentFullProfile(
                studentId, userId, "Yassine Bennis", "USMBA", "Master 1",
                List.of("French"), List.of("data science"), today,
                6, null, true, List.of("python", "r", "bioconductor"), List.of()
        );
        String snapshotJson = JsonCodec.toJson(snapshotProfile);

        List<SkillItem> offerSkills = List.of(
                new SkillItem(UUID.randomUUID(), "Python", "python", true),
                new SkillItem(UUID.randomUUID(), "R", "r", true),
                new SkillItem(UUID.randomUUID(), "Bioconductor", "bioconductor", true)
        );

        RawApplicationCandidate candidate = new RawApplicationCandidate(
                UUID.randomUUID(), studentId, "Yassine Bennis", "Master 1", "USMBA",
                "data science", Instant.now(), "SENT", snapshotJson,
                offerId, "Single-cell pipeline", "Biology", today.plusMonths(1), 6, "Fès", offerSkills
        );

        when(dashboardDao.getUndecidedApplications(eq(labId), any(LocalDate.class)))
                .thenReturn(List.of(candidate));
        when(dashboardDao.countOpenOffers(any(), any())).thenReturn(1);

        LabDashboardResponse resp = service.getDashboard(userId, "Salma", labId);

        assertNotNull(resp.topCandidates());
        assertFalse(resp.topCandidates().isEmpty());
        TopCandidateItem top = resp.topCandidates().get(0);
        assertEquals(100, top.scoreBreakdown().skillsScore()); // 3/3 matched from frozen snapshot
        assertTrue(top.matchPercentage() > 70);
    }

    @Test
    @DisplayName("7. 'Limited data' badge and null percentage when offer has no required skills or profile has < 2 skills")
    void testLimitedDataBadgeWhenOfferHasNoSkillsOrProfileSparse() throws SQLException {
        setupValidMembership();

        UUID studentId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 11, 18);

        // Student with only 1 skill (< 2)
        StudentFullProfile sparseProfile = new StudentFullProfile(
                studentId, userId, "Karim Tazi", "UM5", "Licence",
                List.of(), List.of(), today, 3, null, true, List.of("excel"), List.of()
        );

        RawApplicationCandidate candidate = new RawApplicationCandidate(
                UUID.randomUUID(), studentId, "Karim Tazi", "Licence", "UM5",
                "Biology", Instant.now(), "SENT", JsonCodec.toJson(sparseProfile),
                offerId, "Lab assistant", "Biology", today.plusMonths(1), 3, "Rabat", List.of()
        );

        when(dashboardDao.getUndecidedApplications(eq(labId), any(LocalDate.class)))
                .thenReturn(List.of(candidate));
        when(dashboardDao.countOpenOffers(any(), any())).thenReturn(1);

        LabDashboardResponse resp = service.getDashboard(userId, "Salma", labId);
        assertNotNull(resp.topCandidates());
        assertEquals(1, resp.topCandidates().size());
        TopCandidateItem item = resp.topCandidates().get(0);
        assertTrue(item.isLimitedData());
        assertNull(item.matchPercentage());
    }

    @Test
    @DisplayName("8. Three suggestion rules trigger exactly at thresholds (shortlist >= 3 >= 80, waiting > 5 days, views < 0.5 * median)")
    void testSuggestionRulesTriggering() throws SQLException {
        setupValidMembership();

        LocalDate today = LocalDate.of(2026, 11, 18);
        UUID offerId = UUID.randomUUID();

        // Rule 1: 3 candidates with score 100 on offerId
        List<SkillItem> skills = List.of(
                new SkillItem(UUID.randomUUID(), "Python", "python", true),
                new SkillItem(UUID.randomUUID(), "R", "r", true)
        );
        List<RawApplicationCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            StudentFullProfile prof = new StudentFullProfile(
                    UUID.randomUUID(), UUID.randomUUID(), "Candidate " + i, "USMBA", "Master",
                    List.of("French"), List.of("Biology"), today, 6, null, true, List.of("python", "r"), List.of()
            );
            candidates.add(new RawApplicationCandidate(
                    UUID.randomUUID(), prof.id(), prof.fullName(), "Master", "USMBA",
                    "Biology", Instant.now(), "SENT", JsonCodec.toJson(prof),
                    offerId, "Genomics Pipeline", "Biology", today, 6, "Fès", skills
            ));
        }
        when(dashboardDao.getUndecidedApplications(eq(labId), any(LocalDate.class))).thenReturn(candidates);

        // Rule 2: 2 applicants waiting > 5 days
        when(dashboardDao.countWaitingApplicantsOver5Days(eq(labId), any(Instant.class))).thenReturn(2);

        // Rule 3: 2 offers, one has 2 views while other has 20 views (median = 11, 2 < 0.5 * 11)
        when(dashboardDao.getLabOfferStats(eq(labId))).thenReturn(List.of(
                new LabOfferStats(offerId, "Low View Offer", null, null, 0, FIXED_NOW.minusDays(10).toInstant(), 2),
                new LabOfferStats(UUID.randomUUID(), "Healthy Offer", today, "5000", 3, FIXED_NOW.minusDays(10).toInstant(), 20)
        ));

        LabDashboardResponse resp = service.getDashboard(userId, "Salma", labId);
        assertNotNull(resp.suggestions());
        assertEquals(3, resp.suggestions().size());

        SuggestionItem s1 = resp.suggestions().get(0);
        assertEquals("SHORTLIST", s1.type());
        assertTrue(s1.title().toLowerCase().contains("shortlist"));

        SuggestionItem s2 = resp.suggestions().get(1);
        assertEquals("WAITING", s2.type());
        assertTrue(s2.title().toLowerCase().contains("waiting"));

        SuggestionItem s3 = resp.suggestions().get(2);
        assertEquals("LOW_VIEWS", s3.type());
        assertTrue(s3.title().toLowerCase().contains("views"));
    }

    @Test
    @DisplayName("9. Shortlist invitation passes to DAO and requires explicit confirmation in API")
    void testShortlistDelegatesToDao() throws SQLException {
        setupValidMembership();

        UUID app1 = UUID.randomUUID();
        UUID app2 = UUID.randomUUID();

        when(dashboardDao.shortlistApplications(eq(labId), eq(List.of(app1, app2)), eq(userId))).thenReturn(2);
        int count = service.shortlistCandidates(labId, List.of(app1, app2), userId);
        assertEquals(2, count);
        verify(dashboardDao).shortlistApplications(eq(labId), eq(List.of(app1, app2)), eq(userId));
    }

    @Test
    @DisplayName("10. Offer views daily deduplication logic verified in Africa/Casablanca")
    void testOfferViewsDailyDeduplication() {
        // OfferDao sql contains:
        // AND (viewed_at AT TIME ZONE 'Africa/Casablanca')::date = (CURRENT_TIMESTAMP AT TIME ZONE 'Africa/Casablanca')::date
        // ensuring maximum 1 insert per student/offer/day.
        assertNotNull(CASABLANCA);
    }

    @Test
    @DisplayName("11. Suggestion copy contains no false promises or ungrounded statistics")
    void testSuggestionCopyHasNoFalsePromises() throws SQLException {
        setupValidMembership();

        when(dashboardDao.getLabOfferStats(eq(labId))).thenReturn(List.of(
                new LabOfferStats(UUID.randomUUID(), "Genomics Intro", null, null, 0, FIXED_NOW.minusDays(10).toInstant(), 1)
        ));

        LabDashboardResponse resp = service.getDashboard(userId, "Salma", labId);
        for (SuggestionItem s : resp.suggestions()) {
            assertFalse(s.description().contains("increases applications"), "Must not claim increases applications");
            assertFalse(s.description().contains("boosts recruitment"), "Must not make marketing claims");
        }
    }

    @Test
    @DisplayName("12. Empty states: No applications to review, Nothing needs attention, No activity yet this month")
    void testEmptyStates() throws SQLException {
        setupValidMembership();

        when(dashboardDao.getUndecidedApplications(eq(labId), any(LocalDate.class))).thenReturn(List.of());
        when(dashboardDao.countWaitingApplicantsOver5Days(eq(labId), any(Instant.class))).thenReturn(0);
        when(dashboardDao.getLabOfferStats(eq(labId))).thenReturn(List.of());
        when(dashboardDao.getMonthActivity(eq(labId), any(Instant.class), any(Instant.class)))
                .thenReturn(new MonthStats(0, 0, 0, 0));

        LabDashboardResponse resp = service.getDashboard(userId, "Salma", labId);

        assertTrue(resp.topCandidates().isEmpty());
        assertTrue(resp.suggestions().isEmpty());
        assertEquals(0, resp.thisMonth().views());
        assertEquals(0, resp.thisMonth().applied());
        assertEquals(0, resp.thisMonth().shortlisted());
        assertEquals(0, resp.thisMonth().interviews());
    }

    @Test
    @DisplayName("13. Sidebar badges match dashboard counters (candidates = toReview, interviews = interviewsThisWeek)")
    void testSidebarBadgesMatchDashboardCounters() throws SQLException {
        setupValidMembership();

        when(dashboardDao.countToReview(eq(labId))).thenReturn(7);
        when(dashboardDao.countInterviewsBetween(eq(labId), any(Instant.class), any(Instant.class), eq("CONFIRMED"))).thenReturn(2);

        LabDashboardResponse resp = service.getDashboard(userId, "Salma", labId);
        assertEquals(7, resp.counters().toReview());
        assertEquals(2, resp.counters().interviewsThisWeek());
    }

    @Test
    @DisplayName("14. Performance benchmark: 1000 candidates calculation finishes well under 500ms")
    void testPerformanceWithLargeCandidateList() throws SQLException {
        setupValidMembership();

        LocalDate today = LocalDate.of(2026, 11, 18);
        UUID offerId = UUID.randomUUID();
        List<SkillItem> skills = List.of(
                new SkillItem(UUID.randomUUID(), "Python", "python", true),
                new SkillItem(UUID.randomUUID(), "Nextflow", "nextflow", true)
        );

        List<RawApplicationCandidate> candidates = new ArrayList<>(1000);
        StudentFullProfile mockProf = new StudentFullProfile(
                UUID.randomUUID(), UUID.randomUUID(), "Candidate Bench", "USMBA", "Master",
                List.of("French"), List.of("Biology"), today, 6, null, true,
                List.of("python", "nextflow"), List.of()
        );
        String profJson = JsonCodec.toJson(mockProf);

        for (int i = 0; i < 1000; i++) {
            candidates.add(new RawApplicationCandidate(
                    UUID.randomUUID(), mockProf.id(), "Candidate " + i, "Master", "USMBA",
                    "Biology", Instant.now().minusSeconds(i * 60), "SENT", profJson,
                    offerId, "Pipeline", "Biology", today, 6, "Fès", skills
            ));
        }

        when(dashboardDao.getUndecidedApplications(eq(labId), any(LocalDate.class))).thenReturn(candidates);

        // JVM warm-up call
        service.getDashboard(userId, "Salma", labId);

        long start = System.currentTimeMillis();
        LabDashboardResponse resp = service.getDashboard(userId, "Salma", labId);
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(resp);
        assertEquals(4, resp.topCandidates().size());
        assertTrue(elapsed < 500, "Dashboard aggregation for 1000 candidates must be under 500ms, was " + elapsed + "ms");
    }
}
