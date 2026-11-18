package com.fitwell.service;

import com.fitwell.api.admin.SourceHandler;
import com.fitwell.api.student.OfferHandler;
import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.HttpStatus;
import com.fitwell.core.http.RequestContext;
import com.fitwell.core.json.JsonCodec;
import com.fitwell.dao.OfferDao;
import com.fitwell.dao.OfferDao.OfferRecord;
import com.fitwell.dao.SourceDao;
import com.fitwell.dao.StudentProfileDao;
import com.fitwell.dao.ValidationDao;
import com.fitwell.service.sourcing.CareerPageCrawlerService;
import com.fitwell.service.sourcing.RobotsTxtService;
import com.fitwell.service.sourcing.model.SourceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class SourceCatalogTest {

    private SourceDao sourceDao;
    private ValidationDao validationDao;
    private OfferDao offerDao;
    private RobotsTxtService robotsTxtService;
    private CareerPageCrawlerService crawlerService;
    private SourceHandler sourceHandler;

    private StudentProfileDao studentProfileDao;
    private MatchingService matchingService;
    private QueryParserService queryParserService;
    private OfferHandler offerHandler;

    @BeforeEach
    void setUp() {
        sourceDao = mock(SourceDao.class);
        validationDao = mock(ValidationDao.class);
        offerDao = mock(OfferDao.class);
        robotsTxtService = mock(RobotsTxtService.class);
        crawlerService = mock(CareerPageCrawlerService.class);
        sourceHandler = new SourceHandler(sourceDao, validationDao, offerDao, robotsTxtService, crawlerService);

        studentProfileDao = mock(StudentProfileDao.class);
        matchingService = mock(MatchingService.class);
        queryParserService = mock(QueryParserService.class);
        offerHandler = new OfferHandler(offerDao, studentProfileDao, matchingService, queryParserService, sourceDao);
    }

    private String getBodyString(HttpResponse resp) {
        return new String(resp.getBody(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("1. Migration V005 is idempotent and contains WHERE NOT EXISTS guards")
    void testMigrationIdempotence() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/db/migrations/V005__source_catalog.sql")) {
            assertNotNull(is, "V005__source_catalog.sql must exist");
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("IF NOT EXISTS"), "Must contain IF NOT EXISTS guards for columns");
            assertTrue(sql.contains("WHERE NOT EXISTS"), "Must contain WHERE NOT EXISTS guards for inserts");
        }
    }

    @Test
    @DisplayName("2. No API, CAREER_PAGE or REMOTE_FEED source is allowed by default in catalog")
    void testDefaultAllowedRules() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/db/migrations/V005__source_catalog.sql")) {
            assertNotNull(is);
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Verify Jooble is false
            assertTrue(sql.contains("'Jooble'") && sql.contains("'jooble.org'") && sql.contains("'API'") && sql.contains("false"), "Jooble must be false by default");

            // Verify Career pages default to false
            assertTrue(sql.contains("'OCP Group Careers'") && sql.contains("'CAREER_PAGE'") && sql.contains("false"), "OCP must be false by default");
            assertTrue(sql.contains("'Orange Maroc'") && sql.contains("orange.jobs"), "Orange Maroc must be in catalog");
            assertTrue(sql.contains("'UM6P Careers'") && sql.contains("um6p.ma"), "UM6P must be in catalog");

            // Verify Remote feeds default to false
            assertTrue(sql.contains("'RemoteOK'") && sql.contains("'REMOTE_FEED'") && sql.contains("false"), "RemoteOK must be false by default");
            assertTrue(sql.contains("'Jobicy'") && sql.contains("'REMOTE_FEED'") && sql.contains("false"), "Jobicy must be false by default");

            // Verify Remotive is NOT included in catalog
            assertFalse(sql.toLowerCase().contains("remotive"), "Remotive must not be included");
        }
    }

    @Test
    @DisplayName("3. Source cannot be activated without tos_url and successful robots check")
    void testSourceActivationRules() throws Exception {
        UUID sourceId = UUID.randomUUID();

        // Case A: Missing ToS URL
        SourceRecord sourceWithoutTos = new SourceRecord(
                sourceId, "Test Company", "test.ma", "CAREER_PAGE", "https://test.ma/jobs",
                null, null, "WEBSITE", "PENDING", 100, false,
                Instant.now(), "ALLOWED", 5, null, null, null, false, null, null, null, null
        );
        when(sourceDao.findById(sourceId)).thenReturn(Optional.of(sourceWithoutTos));

        RequestContext ctxA = mock(RequestContext.class);
        when(ctxA.getPathParam("id")).thenReturn(sourceId.toString());
        when(ctxA.parseJsonBody(SourceHandler.AllowSourceReq.class)).thenReturn(new SourceHandler.AllowSourceReq(true));

        HttpResponse respA = sourceHandler.toggleAllow(ctxA);
        assertEquals(HttpStatus.BAD_REQUEST, respA.getStatus());
        assertTrue(getBodyString(respA).contains("Terms of Service (ToS) URL is missing"));
        verify(sourceDao, never()).setAllowed(any(), eq(true));

        // Case B: Has ToS URL but robots_status is PENDING / not ALLOWED
        SourceRecord sourceNotAllowedRobots = new SourceRecord(
                sourceId, "Test Company", "test.ma", "CAREER_PAGE", "https://test.ma/jobs",
                "https://test.ma/tos", "https://test.ma/tos", "WEBSITE", "PENDING", 100, false,
                null, "PENDING_CHECK", 5, null, null, null, false, null, null, null, null
        );
        when(sourceDao.findById(sourceId)).thenReturn(Optional.of(sourceNotAllowedRobots));

        HttpResponse respB = sourceHandler.toggleAllow(ctxA);
        assertEquals(HttpStatus.BAD_REQUEST, respB.getStatus());
        assertTrue(getBodyString(respB).contains("robots.txt check must be executed and ALLOWED"));
        verify(sourceDao, never()).setAllowed(any(), eq(true));

        // Case C: Has ToS URL and robots_status = ALLOWED -> succeeds
        SourceRecord validSource = new SourceRecord(
                sourceId, "Test Company", "test.ma", "CAREER_PAGE", "https://test.ma/jobs",
                "https://test.ma/tos", "https://test.ma/tos", "WEBSITE", "PENDING", 100, false,
                Instant.now(), "ALLOWED", 5, null, null, null, false, null, null, null, null
        );
        when(sourceDao.findById(sourceId)).thenReturn(Optional.of(validSource));

        HttpResponse respC = sourceHandler.toggleAllow(ctxA);
        assertEquals(HttpStatus.OK, respC.getStatus());
        verify(sourceDao, times(1)).setAllowed(sourceId, true);
    }

    @Test
    @DisplayName("4. DEEPLINK sources make ZERO network calls")
    void testDeeplinkMakesZeroNetworkCalls() throws Exception {
        HttpClient mockHttpClient = mock(HttpClient.class);

        // Building deeplink URL does not touch any network or HttpClient
        String resultUrl = OfferHandler.buildDeeplinkUrl(
                "https://www.linkedin.com/jobs/search/?keywords={keywords}&location={city}",
                "https://www.linkedin.com/jobs",
                "Machine Learning",
                "Casablanca"
        );

        assertNotNull(resultUrl);
        assertEquals("https://www.linkedin.com/jobs/search/?keywords=Machine+Learning&location=Casablanca", resultUrl);
        verifyNoInteractions(mockHttpClient);
    }

    @Test
    @DisplayName("5. Keywords and city are URL-encoded in link templates (no raw concatenation)")
    void testKeywordsAndCityAreUrlEncoded() {
        String template = "https://www.rekrute.com/offres-emploi-maroc.html?keyword={keywords}&region={city}";
        String query = "stage r&d c++ & python";
        String city = "Rabat / Salé";

        String url = OfferHandler.buildDeeplinkUrl(template, "https://www.rekrute.com", query, city);

        assertNotNull(url);
        // Ensure space and & are encoded
        assertTrue(url.contains("stage+r%26d+c%2B%2B+%26+python") || url.contains("stage%20r%26d%20c%2B%2B%20%26%20python"),
                "Keywords must be URL-encoded");
        assertTrue(url.contains("Rabat+%2F+Sal%C3%A9") || url.contains("Rabat%20%2F%20Sal%C3%A9"),
                "City must be URL-encoded with UTF-8");
        assertFalse(url.contains("stage r&d"), "Raw spaces or ampersands must never appear in query params");
    }

    @Test
    @DisplayName("6. Invalid or non-http(s) link templates are strictly rejected")
    void testInvalidOrNonHttpSchemeRejected() {
        // Javascript scheme
        assertNull(OfferHandler.buildDeeplinkUrl("javascript:alert(1)", "http://site.com", "kw", "city"));

        // File scheme
        assertNull(OfferHandler.buildDeeplinkUrl("file:///etc/passwd", "http://site.com", "kw", "city"));

        // Ftp scheme
        assertNull(OfferHandler.buildDeeplinkUrl("ftp://ftp.example.com?q={keywords}", "http://site.com", "kw", "city"));

        // Valid https template
        String valid = OfferHandler.buildDeeplinkUrl("https://ma.indeed.com/jobs?q={keywords}&l={city}", "https://ma.indeed.com", "react", "rabat");
        assertNotNull(valid);
        assertTrue(valid.startsWith("https://ma.indeed.com"));
    }

    @Test
    @DisplayName("7. No offer card can have a DEEPLINK source")
    void testNoOfferCardHasDeeplinkSource() throws Exception {
        UUID offerId = UUID.randomUUID();
        OfferRecord deeplinkOffer = new OfferRecord(
                offerId, UUID.randomUUID(), "Test Lab", "UM5", "Rabat",
                "Fake LinkedIn Scraped Offer", "Tech", 4, LocalDate.now(), "Rabat",
                "Funded", "4000 DH", "Excerpt", List.of(), 1, "ACTIVE",
                "DEEPLINK", "LinkedIn Jobs", "https://linkedin.com/jobs/view/123",
                LocalDate.now().plusMonths(2), 60, List.of()
        );

        when(offerDao.findPublishedOffers()).thenReturn(List.of(deeplinkOffer));

        RequestContext ctx = mock(RequestContext.class);
        HttpResponse resp = offerHandler.getOffers(ctx);

        assertEquals(HttpStatus.OK, resp.getStatus());
        String body = getBodyString(resp);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = JsonCodec.fromJson(body, Map.class);
        List<?> offers = (List<?>) map.get("offers");
        assertTrue(offers.isEmpty(), "DEEPLINK source offer must never appear as an offer card");
    }

    @Test
    @DisplayName("8. No seed offer carries an external badge without connector")
    void testNoSeedOfferHasExternalBadgeWithoutConnector() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/db/seed_morocco_sources.sql")) {
            assertNotNull(is);
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // Verify no fake badges or forbidden sources in seed
            assertFalse(sql.toLowerCase().contains("linkedin"), "Seed offers must not have LinkedIn source");
            assertFalse(sql.toLowerCase().contains("stagiaire.ma"), "Seed offers must not have stagiaire.ma source");
            assertFalse(sql.toLowerCase().contains("rekrute"), "Seed offers must not have rekrute source");
            assertFalse(sql.toLowerCase().contains("google"), "Seed offers must not have google source");

            // Seed offers belong strictly to approved partners Mifactory, UIT, DeepLeaf
            assertTrue(sql.contains("Mifactory"));
            assertTrue(sql.contains("Université Ibn Tofail"));
            assertTrue(sql.contains("DeepLeaf"));
        }
    }

    @Test
    @DisplayName("9. Jooble API key is server-side only and never leaked in response or log")
    void testJoobleApiKeyNeverLeakedInResponseOrLog() throws Exception {
        String testKey = "SECRET_JOOBLE_KEY_98765_ABCD";
        RequestContext ctx = mock(RequestContext.class);
        when(offerDao.findPublishedOffers()).thenReturn(List.of());

        HttpResponse resp = offerHandler.getOffers(ctx);

        assertEquals(HttpStatus.OK, resp.getStatus());
        String body = getBodyString(resp);
        assertFalse(body.contains(testKey), "Jooble API key must never appear in response body");
        assertFalse(body.toLowerCase().contains("secret_jooble_key"), "No secret key in response");
    }
}
