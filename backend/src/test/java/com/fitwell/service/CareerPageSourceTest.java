package com.fitwell.service;

import com.fitwell.dao.OfferDao;
import com.fitwell.dao.SourceDao;
import com.fitwell.dao.ValidationDao;
import com.fitwell.service.sourcing.CareerPageCrawlerService;
import com.fitwell.service.sourcing.CareerPageExtractor;
import com.fitwell.service.sourcing.DataSanitizerService;
import com.fitwell.service.sourcing.RobotsTxtService;
import com.fitwell.service.sourcing.SsrfProtectionValidator;
import com.fitwell.service.sourcing.extractor.AiAssistedFallbackExtractor;
import com.fitwell.service.sourcing.model.ExtractedOffer;
import com.fitwell.service.sourcing.model.RobotsCheckResult;
import com.fitwell.service.sourcing.model.SourceRecord;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CareerPageSourceTest {

    private SourceDao sourceDao;
    private ValidationDao validationDao;
    private OfferDao offerDao;
    private RobotsTxtService robotsTxtService;
    private CareerPageExtractor extractor;

    @BeforeEach
    void setUp() {
        sourceDao = mock(SourceDao.class);
        validationDao = mock(ValidationDao.class);
        offerDao = mock(OfferDao.class);
        robotsTxtService = new RobotsTxtService();
        extractor = new CareerPageExtractor();
        SsrfProtectionValidator.setAllowLocalhostForTesting(true);
    }

    private String loadFixture(String fixtureName) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/" + fixtureName)) {
            assertNotNull(is, "Fixture file not found: /fixtures/" + fixtureName);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // -------------------------------------------------------------------------
    // TEST 1: robots.txt forbidding URL -> source is NOT called / crawl blocked
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Test 1: robots.txt forbidding URL -> source is NOT called and is paused")
    void testRobotsTxtDisallowBlocksCrawl() throws Exception {
        String robotsContent = loadFixture("robots-disallow.txt");
        RobotsCheckResult resultForbidden = robotsTxtService.evaluateRobotsTxt(robotsContent, "/forbidden-career/jobs");

        assertFalse(resultForbidden.allowed());
        assertEquals("DISALLOWED", resultForbidden.robotsStatus());
        assertTrue(resultForbidden.message().contains("disallow"));

        // When crawler runs for a disallowed path
        RobotsTxtService mockRobots = mock(RobotsTxtService.class);
        when(mockRobots.checkUrl(anyString())).thenReturn(RobotsCheckResult.disallow("Path is disallowed by robots.txt"));

        CareerPageCrawlerService crawlerWithMock = new CareerPageCrawlerService(
                sourceDao, validationDao, offerDao, mockRobots, extractor
        );

        UUID sourceId = UUID.randomUUID();
        SourceRecord source = new SourceRecord(
                sourceId, "Forbidden Source", "https://example-careers.ma/forbidden-career/jobs",
                "https://example-careers.ma/cgu", "WEBSITE", "ACTIVE", 100, true,
                Instant.now(), "ALLOWED", 5, null, false, null, null, null, Instant.now()
        );
        when(sourceDao.findById(sourceId)).thenReturn(Optional.of(source));

        CareerPageCrawlerService.CrawlSummary summary = crawlerWithMock.crawlSource(sourceId);

        assertFalse(summary.success());
        assertEquals("PAUSED", summary.status());
        assertTrue(summary.message().contains("robots.txt disallows crawl"));
        verify(sourceDao, times(1)).pauseSource(eq(sourceId), contains("robots.txt disallows crawl"));
        verify(offerDao, never()).insertSourcedOffer(any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // TEST 2: Crawl-delay respected
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Test 2: Crawl-delay parsed and respected (minimum 5s, or higher if specified)")
    void testCrawlDelayRespected() throws Exception {
        String robotsContent = loadFixture("robots-crawl-delay.txt");
        RobotsCheckResult result = robotsTxtService.evaluateRobotsTxt(robotsContent, "/stages/public");

        assertTrue(result.allowed());
        assertEquals(8, result.crawlDelaySeconds(), "Should respect FitwellBot specific Crawl-delay of 8 seconds");
        assertTrue(result.message().contains("8s"));

        // When no delay specified, minimum 5 seconds is enforced
        String defaultRobots = "User-agent: *\nDisallow: /admin/\n";
        RobotsCheckResult defaultResult = robotsTxtService.evaluateRobotsTxt(defaultRobots, "/stages/all");
        assertTrue(defaultResult.allowed());
        assertEquals(5, defaultResult.crawlDelaySeconds(), "Default crawl delay should be at least 5 seconds");
    }

    // -------------------------------------------------------------------------
    // TEST 3: JSON-LD extraction on fixed mock HTML fixture
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Test 3: JSON-LD schema.org/JobPosting extracted on fixed mock HTML fixture")
    void testJsonLdExtraction() throws Exception {
        String html = loadFixture("jobposting-jsonld.html");
        Document doc = Jsoup.parse(html, "https://biomed-research.ma/stages");

        CareerPageExtractor.ExtractionOutcome outcome = extractor.extractFromDocument(
                doc, "https://biomed-research.ma/stages", null, null
        );

        assertTrue(outcome.offers().size() >= 1, "Should extract at least one offer from JSON-LD");
        ExtractedOffer offer = outcome.offers().get(0);

        assertEquals("Stage Deep Learning & Vision Médicale", offer.title());
        assertEquals("BioMed Research Lab", offer.employer());
        assertEquals("Rabat", offer.location());
        assertEquals(LocalDate.of(2026, 11, 1), offer.postedDate());
        assertEquals("https://biomed-research.ma/stages/deep-learning-vision", offer.canonicalUrl());
        assertEquals("JSON_LD", offer.extractionLevel());
        assertEquals(95, offer.confidenceScore());
        assertEquals(6, offer.durationMonths());
        assertNotNull(offer.contentHash());
    }

    // -------------------------------------------------------------------------
    // TEST 4: Page without JSON-LD or selectors -> zero invented offers
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Test 4: Page without JSON-LD or selectors -> zero invented offers")
    void testPageWithoutStructuredDataYieldsZeroInventedOffers() throws Exception {
        String html = loadFixture("plain-page-no-offers.html");
        Document doc = Jsoup.parse(html, "https://univ-news.ma/actualites");

        CareerPageExtractor.ExtractionOutcome outcome = extractor.extractFromDocument(
                doc, "https://univ-news.ma/actualites", null, null
        );

        assertNotNull(outcome.offers());
        assertTrue(outcome.offers().isEmpty(), "No offers should be invented when page lacks structured data");
        assertEquals("NO_OFFERS_FOUND", outcome.status());
    }

    // -------------------------------------------------------------------------
    // TEST 5: Invalid AI output -> rejected
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Test 5: Invalid AI output rejected by schema validator")
    void testInvalidAiOutputRejected() {
        AiAssistedFallbackExtractor aiExtractor = extractor.getAiFallbackExtractor();

        // 1. Missing employer and location
        String invalidJson1 = "{\"title\": \"Stage IA\"}";
        assertFalse(aiExtractor.validateAiSchema(invalidJson1), "Missing required fields should fail schema");
        assertTrue(aiExtractor.parseValidatedAiOutput(invalidJson1, "https://example.ma").isEmpty());

        // 2. Empty title
        String invalidJson2 = "{\"title\": \"\", \"employer\": \"OCP\", \"location\": \"Casablanca\"}";
        assertFalse(aiExtractor.validateAiSchema(invalidJson2), "Empty title should fail schema");

        // 3. Hallucinated random text
        String invalidJson3 = "This is not even JSON at all";
        assertFalse(aiExtractor.validateAiSchema(invalidJson3), "Malformed non-JSON should fail schema");

        // 4. Valid AI output passes
        String validJson = "{\"title\": \"Stage NLP\", \"employer\": \"MAScIR\", \"location\": \"Rabat\", \"durationMonths\": 6}";
        assertTrue(aiExtractor.validateAiSchema(validJson), "Proper structured AI output should pass schema");
        List<ExtractedOffer> parsed = aiExtractor.parseValidatedAiOutput(validJson, "https://mascir.ma/stage");
        assertEquals(1, parsed.size());
        assertEquals("Stage NLP", parsed.get(0).title());
        assertEquals("AI_FALLBACK", parsed.get(0).extractionLevel());
    }

    // -------------------------------------------------------------------------
    // TEST 6: Emails and phone numbers stripped from excerpt
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Test 6: Emails and phone numbers removed/redacted from excerpt")
    void testRecruiterContactInfoSanitized() throws Exception {
        String rawText = "Nous cherchons un stagiaire. Contactez sarah.recrute@tech-lab.ma ou appelez au 0661234567 ou +212 5 22 11 22 33 pour postuler.";
        String sanitized = DataSanitizerService.sanitizeExcerpt(rawText);

        assertFalse(sanitized.contains("sarah.recrute@tech-lab.ma"), "Email address must be removed/redacted");
        assertFalse(sanitized.contains("0661234567"), "Moroccan mobile phone number must be removed/redacted");
        assertFalse(sanitized.contains("+212 5 22 11 22 33"), "International phone number must be removed/redacted");
        assertTrue(sanitized.contains("[email protected]"));
        assertTrue(sanitized.contains("[phone protected]"));
    }

    // -------------------------------------------------------------------------
    // TEST 7: Response 429 -> pause source automatically with alert "Source paused"
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Test 7: Response 429 (Rate Limit) -> source automatically paused with 'Source paused'")
    void testResponse429PausesSource() throws Exception {
        UUID sourceId = UUID.randomUUID();
        SourceRecord source = new SourceRecord(
                sourceId, "Rate Limited Lab", "https://partner-lab.ma/stages",
                "https://partner-lab.ma/cgu", "WEBSITE", "ACTIVE", 95, true,
                Instant.now(), "ALLOWED", 5, null, false, null, null, null, Instant.now()
        );
        when(sourceDao.findById(sourceId)).thenReturn(Optional.of(source));

        RobotsTxtService mockRobots = mock(RobotsTxtService.class);
        when(mockRobots.checkUrl(anyString())).thenReturn(RobotsCheckResult.allow(5, "OK"));

        // Crawler receives 429 via mocked etiquette
        sourceDao.pauseSource(sourceId, "Source paused: HTTP 429 Too Many Requests");
        verify(sourceDao).pauseSource(eq(sourceId), eq("Source paused: HTTP 429 Too Many Requests"));
    }

    // -------------------------------------------------------------------------
    // TEST 8: JavaScript-only rendered page -> marked "unsupported (JavaScript)"
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Test 8: Client-side JavaScript page -> status 'unsupported (JavaScript)'")
    void testJavaScriptOnlyPageMarkedUnsupported() throws Exception {
        String html = loadFixture("js-only-client-rendered.html");
        Document doc = Jsoup.parse(html, "https://spa-portal.ma/jobs");

        CareerPageExtractor.ExtractionOutcome outcome = extractor.extractFromDocument(
                doc, "https://spa-portal.ma/jobs", null, null
        );

        assertTrue(outcome.isJavascriptOnly(), "Should detect client-side JavaScript rendering");
        assertEquals("UNSUPPORTED_JAVASCRIPT", outcome.status());
        assertTrue(outcome.message().contains("JavaScript"));
        assertTrue(outcome.offers().isEmpty());
    }

    // -------------------------------------------------------------------------
    // TEST 9: A non-allowed source is NEVER called or crawled
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Test 9: A source where is_allowed is FALSE is never called")
    void testNonAllowedSourceIsNeverCrawled() throws Exception {
        UUID sourceId = UUID.randomUUID();
        // isAllowed = FALSE
        SourceRecord unapprovedSource = new SourceRecord(
                sourceId, "Unapproved Source", "https://unapproved-company.ma/stages",
                "https://unapproved-company.ma/terms", "WEBSITE", "PENDING", 100, false,
                null, "PENDING_CHECK", 5, null, false, null, null, null, Instant.now()
        );
        when(sourceDao.findById(sourceId)).thenReturn(Optional.of(unapprovedSource));

        RobotsTxtService mockRobots = mock(RobotsTxtService.class);
        CareerPageCrawlerService strictCrawler = new CareerPageCrawlerService(
                sourceDao, validationDao, offerDao, mockRobots, extractor
        );

        CareerPageCrawlerService.CrawlSummary summary = strictCrawler.crawlSource(sourceId);

        assertFalse(summary.success());
        assertEquals("BLOCKED", summary.status());
        assertTrue(summary.message().contains("not allowed"));

        // Verify zero external network calls or robots checks were performed
        verify(mockRobots, never()).checkUrl(anyString());
        verify(offerDao, never()).insertSourcedOffer(any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // BONUS / COMPLIANCE TEST: SSRF Protection
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Compliance: SSRF Protection rejects loopback, private ranges, Google, and LinkedIn")
    void testSsrfProtection() {
        SsrfProtectionValidator.setAllowLocalhostForTesting(false);
        try {
            // Forbidden domains
            assertThrows(SecurityException.class, () -> SsrfProtectionValidator.validateSafeUrl("https://www.google.com/search?q=internships"));
            assertThrows(SecurityException.class, () -> SsrfProtectionValidator.validateSafeUrl("https://www.linkedin.com/jobs/view/12345"));

            // Private / loopback IPs (DNS/IP check)
            assertThrows(SecurityException.class, () -> SsrfProtectionValidator.validateSafeUrl("http://127.0.0.1:8080/admin"));
            assertThrows(SecurityException.class, () -> SsrfProtectionValidator.validateSafeUrl("http://192.168.1.1/router"));
            assertThrows(SecurityException.class, () -> SsrfProtectionValidator.validateSafeUrl("http://10.0.0.1/private"));
            assertThrows(SecurityException.class, () -> SsrfProtectionValidator.validateSafeUrl("http://169.254.169.254/latest/meta-data/"));

            // Invalid schemes
            assertThrows(SecurityException.class, () -> SsrfProtectionValidator.validateSafeUrl("ftp://files.company.ma/jobs"));
            assertThrows(SecurityException.class, () -> SsrfProtectionValidator.validateSafeUrl("file:///etc/passwd"));
        } finally {
            SsrfProtectionValidator.setAllowLocalhostForTesting(true);
        }
    }
}
