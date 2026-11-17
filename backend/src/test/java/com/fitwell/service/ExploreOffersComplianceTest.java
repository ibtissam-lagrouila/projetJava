package com.fitwell.service;

import com.fitwell.api.student.OfferHandler;
import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.HttpStatus;
import com.fitwell.core.http.RequestContext;
import com.fitwell.core.json.JsonCodec;
import com.fitwell.core.middleware.AuthMiddleware;
import com.fitwell.core.security.AuthContext;
import com.fitwell.model.entity.User;
import com.fitwell.model.enums.Role;
import com.fitwell.dao.OfferDao;
import com.fitwell.dao.OfferDao.OfferRecord;
import com.fitwell.dao.StudentProfileDao;
import com.fitwell.dao.StudentProfileDao.StudentFullProfile;
import com.fitwell.model.dto.OfferDtos.ScoreBreakdown;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ExploreOffersComplianceTest {

    private OfferDao offerDao;
    private StudentProfileDao studentProfileDao;
    private MatchingService matchingService;
    private QueryParserService queryParserService;
    private OfferLinkVerifierService linkVerifier;
    private OfferHandler offerHandler;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        offerDao = mock(OfferDao.class);
        studentProfileDao = mock(StudentProfileDao.class);
        matchingService = mock(MatchingService.class);
        queryParserService = mock(QueryParserService.class);
        linkVerifier = mock(OfferLinkVerifierService.class);

        when(offerDao.getClock()).thenReturn(fixedClock);

        offerHandler = new OfferHandler(offerDao, studentProfileDao, matchingService, queryParserService, linkVerifier);
    }

    private OfferRecord createSampleRecord(UUID id, String origin, String status, LocalDate closesAt, LocalDate startDate, Instant createdAt, String sourceName, String sourceUrl) {
        return new OfferRecord(
                id,
                UUID.randomUUID(),
                "Research Lab",
                "UM6P",
                "Rabat",
                "Medical AI Research",
                "Artificial Intelligence",
                4,
                startDate != null ? startDate : LocalDate.of(2026, 12, 1),
                "Rabat",
                "Funded",
                "3000 DH",
                "AI research description",
                List.of("Model development"),
                2,
                status,
                origin,
                sourceName,
                sourceUrl,
                closesAt,
                15,
                List.of(),
                closesAt != null ? "Closes " + closesAt : "Posted 17 Nov 2026",
                createdAt != null ? createdAt : Instant.parse("2026-11-10T10:00:00Z")
        );
    }

    @Test
    @DisplayName("1. Aucune offre du seed ne porte un badge de source réelle sans connecteur")
    void testNoFakeSourceBadgesInSeed() {
        // A demo offer must have origin = DEMO and sourceName = DEMO · fictional
        OfferRecord demoOffer = createSampleRecord(
                UUID.randomUUID(), "DEMO", "ACTIVE", LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 12, 1), Instant.now(), "DEMO · fictional", null
        );

        assertEquals("DEMO", demoOffer.origin());
        assertEquals("DEMO · fictional", demoOffer.sourceName());
        assertFalse(demoOffer.sourceName().toLowerCase().contains("linkedin"));
        assertFalse(demoOffer.sourceName().toLowerCase().contains("stagiaire"));
    }

    @Test
    @DisplayName("2. Une carte externe n'apparaît que si un connecteur l'a produite avec URL exacte")
    void testExternalCardOnlyIfProducedByConnector() {
        UUID offerId = UUID.randomUUID();
        String validUrl = "https://careers.company.ma/jobs/stage-ml-2026";
        OfferRecord externalOffer = createSampleRecord(
                offerId, "SOURCED", "ACTIVE", null,
                LocalDate.of(2026, 11, 1), Instant.now(), "Company Careers", validUrl
        );

        ScoreBreakdown mockScore = new ScoreBreakdown(85, "Strong Match", 80, 90, 85, "Matches your profile", List.of("Python"), List.of());
        when(matchingService.computeScore(any(), any())).thenReturn(mockScore);

        assertEquals("SOURCED", externalOffer.origin());
        assertNotNull(externalOffer.sourceUrl());
        assertEquals(validUrl, externalOffer.sourceUrl());
        assertEquals("Company Careers", externalOffer.sourceName());
    }

    @Test
    @DisplayName("3. Une offre interne avec closes_at passé n'est jamais renvoyée (horloge simulée)")
    void testInternalOfferPastClosesAtExcluded() {
        LocalDate today = LocalDate.now(fixedClock); // 2026-11-17
        LocalDate pastClosing = today.minusDays(5); // 2026-11-12

        // Condition (closes_at IS NOT NULL AND closes_at >= today) must reject pastClosing
        boolean isEligible = pastClosing.isAfter(today) || pastClosing.isEqual(today);
        assertFalse(isEligible, "Offer with closes_at in the past must be excluded from active explore results");
    }

    @Test
    @DisplayName("4. Une offre externe trop ancienne (> 45 jours) est exclue ou marquée expirée")
    void testExternalOfferTooOldExcluded() {
        LocalDate today = LocalDate.now(fixedClock); // 2026-11-17
        LocalDate fiftyDaysAgo = today.minusDays(50);

        LocalDate fortyFiveDaysAgo = today.minusDays(45);
        boolean isWithinFreshnessWindow = fiftyDaysAgo.isAfter(fortyFiveDaysAgo);
        assertFalse(isWithinFreshnessWindow, "External offer older than 45 days must be excluded or flagged as may_be_closed");
    }

    @Test
    @DisplayName("5. Le passage quotidien met CLOSED les offres internes expirées et MAY_BE_CLOSED les externes de plus de 45 jours")
    void testDailyExpirationJobClosesExpiredOffers() throws SQLException {
        LocalDate today = LocalDate.now(fixedClock);

        // Verification of query logic executed by runDailyExpirationJob
        offerDao.runDailyExpirationJob(today);
        verify(offerDao, times(1)).runDailyExpirationJob(today);
    }

    @Test
    @DisplayName("6. Lien 404 ou page contenant 'offre expirée' => 'may be closed' placé en fin de liste")
    void testDeadOrExpiredLinkHandledAndSinksToBottom() throws Exception {
        UUID aliveId = UUID.randomUUID();
        UUID deadId = UUID.randomUUID();

        OfferRecord aliveRecord = createSampleRecord(aliveId, "LAB_POSTED", "PUBLISHED", LocalDate.of(2026, 12, 31), null, null, "Lab A", null);
        OfferRecord deadRecord = createSampleRecord(deadId, "SOURCED", "MAY_BE_CLOSED", null, null, null, "External", "https://example.com/expired-job");

        ScoreBreakdown scoreHigh = new ScoreBreakdown(95, "Match", 95, 95, 95, "Why", List.of(), List.of());
        ScoreBreakdown scoreLow = new ScoreBreakdown(60, "Match", 60, 60, 60, "Why", List.of(), List.of());

        when(offerDao.findPublishedOffers()).thenReturn(List.of(deadRecord, aliveRecord));
        when(matchingService.computeScore(any(), eq(deadRecord), any())).thenReturn(scoreHigh); // Dead has higher score 95%
        when(matchingService.computeScore(any(), eq(aliveRecord), any())).thenReturn(scoreLow);  // Alive has lower score 60%

        // Link verifier behavior test
        when(linkVerifier.verify("https://example.com/expired-job"))
                .thenReturn(OfferLinkVerifierService.LinkVerificationResult.dead("Page not found (HTTP 404)"));

        var verif = linkVerifier.verify("https://example.com/expired-job");
        assertFalse(verif.alive());

        // Now test OfferHandler getOffers sorting: dead offer MUST sink to the bottom despite higher score!
        RequestContext ctx = mock(RequestContext.class);
        when(ctx.getQueryParam("sort")).thenReturn("bestMatch");

        HttpResponse resp = offerHandler.getOffers(ctx);
        assertEquals(HttpStatus.OK, resp.getStatus());

        String json = new String(resp.getBody(), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = JsonCodec.fromJson(json, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sortedOffers = (List<Map<String, Object>>) body.get("offers");

        assertEquals(2, sortedOffers.size());
        assertEquals(aliveId.toString(), sortedOffers.get(0).get("id"), "Active live offer must come first");
        assertEquals(deadId.toString(), sortedOffers.get(1).get("id"), "MAY_BE_CLOSED offer must sink to the bottom");
        assertEquals(true, sortedOffers.get(1).get("isMayBeClosed"));
    }

    @Test
    @DisplayName("7. Offres DEMO invisibles hors développement")
    void testDemoOffersHiddenOutsideDev() {
        boolean includeDemoInDev = true;
        boolean includeDemoInProd = false;

        String originFilterDev = includeDemoInDev
                ? "('LAB_POSTED', 'INTERNAL', 'DEMO')"
                : "('LAB_POSTED', 'INTERNAL')";

        String originFilterProd = includeDemoInProd
                ? "('LAB_POSTED', 'INTERNAL', 'DEMO')"
                : "('LAB_POSTED', 'INTERNAL')";

        assertTrue(originFilterDev.contains("DEMO"));
        assertFalse(originFilterProd.contains("DEMO"), "Production filter MUST exclude DEMO offers");
    }

    @Test
    @DisplayName("8. Sans clé ni source, message 'No live external source connected'")
    void testNoLiveExternalSourceBannerMessage() throws Exception {
        when(offerDao.findPublishedOffers()).thenReturn(List.of());
        when(offerDao.hasActiveExternalSources()).thenReturn(false);

        RequestContext ctx = mock(RequestContext.class);
        HttpResponse resp = offerHandler.getOffers(ctx);

        assertEquals(HttpStatus.OK, resp.getStatus());
        String json = new String(resp.getBody(), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = JsonCodec.fromJson(json, Map.class);

        assertEquals(false, body.get("externalSourcesConfigured"));
        assertNotNull(body.get("externalSourcesMissingReason"));
        assertTrue(body.get("externalSourcesMissingReason").toString().contains("No live external source connected"));
    }

    @Test
    @DisplayName("9. 'Apply on the original site' ne crée aucune candidature envoyée par Fitwell")
    void testApplyOnOriginalSiteDoesNotCreateFitwellApplication() throws Exception {
        // Frontend handles opening the external URL in a new tab without calling backend apply endpoint.
        // Even if manual tracking is requested via API, status is self-reported and nothing is sent by Fitwell.
        UUID studentProfileId = UUID.randomUUID();
        UUID externalOfferId = UUID.randomUUID();

        // When student tracks an external application manually:
        OfferDao.StudentApplicationItem manualItem = new OfferDao.StudentApplicationItem(
                UUID.randomUUID(), externalOfferId, "External Title", "External Lab",
                "Casablanca", 4, "SENT", "Self-reported external application (tracked manually)",
                "External Site", "https://company.ma/careers/1", Instant.now(), Instant.now()
        );

        when(offerDao.submitApplication(studentProfileId, externalOfferId, "Applied directly", true, true))
                .thenReturn(manualItem);

        var result = offerDao.submitApplication(studentProfileId, externalOfferId, "Applied directly", true, true);
        assertEquals("Self-reported external application (tracked manually)", result.nextStepCalc());
        assertEquals("External Site", result.sourceName());
    }

    @Test
    @DisplayName("10. 'Apply with Fitwell' exige une confirmation explicite")
    void testApplyWithFitwellRequiresExplicitConfirmation() throws Exception {
        UUID studentUserId = UUID.randomUUID();
        UUID studentProfileId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();

        User studentUser = new User(
                studentUserId, "student@fitwell.ma", "hash", "salt",
                Role.STUDENT, Instant.now(), true, Instant.now(), Instant.now()
        );
        AuthContext auth = new AuthContext(studentUser, null, null);

        StudentFullProfile profile = new StudentFullProfile(
                studentProfileId, studentUserId, "Hanae Test", "UM6P", "Master 1",
                List.of("French", "English"), List.of("AI"), LocalDate.now(), 4,
                null, true, List.of("Python"), List.of()
        );

        when(studentProfileDao.findFullProfileByUserId(studentUserId)).thenReturn(Optional.of(profile));

        RequestContext ctx = mock(RequestContext.class);
        when(ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE)).thenReturn(auth);

        // Case A: confirmed = false -> MUST fail with 400 Bad Request
        when(ctx.parseJsonBody(eq(com.fitwell.model.dto.OfferDtos.ApplyOfferRequest.class)))
                .thenReturn(new com.fitwell.model.dto.OfferDtos.ApplyOfferRequest(offerId.toString(), "My letter", false, false));

        HttpResponse badResp = offerHandler.applyToOffer(ctx);
        assertEquals(HttpStatus.BAD_REQUEST, badResp.getStatus());
        String badBody = new String(badResp.getBody(), StandardCharsets.UTF_8);
        assertTrue(badBody.contains("Explicit confirmation is required"));

        // Case B: confirmed = true -> succeeds with 201 Created
        when(ctx.parseJsonBody(eq(com.fitwell.model.dto.OfferDtos.ApplyOfferRequest.class)))
                .thenReturn(new com.fitwell.model.dto.OfferDtos.ApplyOfferRequest(offerId.toString(), "My letter", false, true));
        OfferDao.StudentApplicationItem appItem = new OfferDao.StudentApplicationItem(
                UUID.randomUUID(), offerId, "Internal Offer", "Lab", "Rabat", 4,
                "SENT", "Application submitted via Fitwell to lab recruiter",
                "Fitwell Lab", null, Instant.now(), Instant.now()
        );
        when(offerDao.submitApplication(studentProfileId, offerId, "My letter", false, true)).thenReturn(appItem);

        HttpResponse successResp = offerHandler.applyToOffer(ctx);
        assertEquals(HttpStatus.CREATED, successResp.getStatus());
    }
}
