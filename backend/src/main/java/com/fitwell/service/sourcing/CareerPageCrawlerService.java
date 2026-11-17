package com.fitwell.service.sourcing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitwell.dao.OfferDao;
import com.fitwell.dao.SourceDao;
import com.fitwell.dao.ValidationDao;
import com.fitwell.service.sourcing.model.ExtractedOffer;
import com.fitwell.service.sourcing.model.RobotsCheckResult;
import com.fitwell.service.sourcing.model.SourceRecord;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CareerPageCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(CareerPageCrawlerService.class);
    private static final int MAX_PAGES_PER_CRAWL = 50;
    private static final int MAX_PAGE_SIZE_BYTES = 2 * 1024 * 1024; // 2MB limit
    private static final ObjectMapper mapper = new ObjectMapper();

    private final SourceDao sourceDao;
    private final ValidationDao validationDao;
    private final OfferDao offerDao;
    private final RobotsTxtService robotsTxtService;
    private final CareerPageExtractor extractor;

    // Domain -> Last request timestamp in epoch millis for rate limiting (at most 1 req / 5s)
    private final Map<String, Long> lastRequestPerDomain = new ConcurrentHashMap<>();

    public CareerPageCrawlerService(SourceDao sourceDao, ValidationDao validationDao, OfferDao offerDao,
                                    RobotsTxtService robotsTxtService, CareerPageExtractor extractor) {
        this.sourceDao = sourceDao;
        this.validationDao = validationDao;
        this.offerDao = offerDao;
        this.robotsTxtService = robotsTxtService;
        this.extractor = extractor;
    }

    public record CrawlSummary(
            UUID sourceId,
            String sourceName,
            boolean success,
            int itemsFound,
            String status,
            String message
    ) {}

    public record SampleTestResult(
            boolean robotsAllowed,
            String robotsMessage,
            int crawlDelaySeconds,
            boolean isJavascriptOnly,
            String status,
            List<ExtractedOffer> previewOffers,
            String message
    ) {}

    /**
     * Executes a sample test (up to 5 pages max) without persisting anything to the database.
     */
    public SampleTestResult runSampleTest(String url, String cssSelectorsJson) {
        try {
            SsrfProtectionValidator.validateSafeUrl(url);

            // 1. Robots.txt evaluation
            RobotsCheckResult robotsCheck = robotsTxtService.checkUrl(url);
            if (!robotsCheck.allowed()) {
                return new SampleTestResult(
                        false,
                        robotsCheck.message(),
                        robotsCheck.crawlDelaySeconds(),
                        false,
                        "ROBOTS_DISALLOWED",
                        List.of(),
                        "Robots.txt forbids access: " + robotsCheck.message()
                );
            }

            // 2. Fetch page (single sample, max 2MB)
            HttpResponseData response = fetchPageWithEtiquette(url, robotsCheck.crawlDelaySeconds());
            if (response.statusCode == 403 || response.statusCode == 429) {
                return new SampleTestResult(
                        true,
                        robotsCheck.message(),
                        robotsCheck.crawlDelaySeconds(),
                        false,
                        "RATE_LIMITED_OR_FORBIDDEN",
                        List.of(),
                        "Target site returned HTTP " + response.statusCode
                );
            }

            Document doc = Jsoup.parse(response.body, url);
            CareerPageExtractor.ExtractionOutcome outcome = extractor.extractFromDocument(doc, url, cssSelectorsJson, null);

            List<ExtractedOffer> sampleOffers = outcome.offers();
            if (sampleOffers.size() > 5) {
                sampleOffers = sampleOffers.subList(0, 5); // Limit sample preview to 5 items max
            }

            return new SampleTestResult(
                    true,
                    robotsCheck.message(),
                    robotsCheck.crawlDelaySeconds(),
                    outcome.isJavascriptOnly(),
                    outcome.status(),
                    sampleOffers,
                    outcome.message()
            );

        } catch (SecurityException se) {
            return new SampleTestResult(false, se.getMessage(), 0, false, "SECURITY_BLOCKED", List.of(), se.getMessage());
        } catch (Exception e) {
            return new SampleTestResult(false, e.getMessage(), 0, false, "ERROR", List.of(), "Sample test error: " + e.getMessage());
        }
    }

    /**
     * Crawls an authorized source adhering to all rules:
     * - Source must be allowed (is_allowed == true)
     * - SSRF validation
     * - Robots.txt verification (pauses source if disallowed)
     * - Rate limiting (1 req / 5s)
     * - Max 50 pages
     * - Timeouts & max 2MB size
     * - No cross-domain redirects
     * - Auto-pause on 403 or 429
     * - unsupported (JavaScript) handling
     */
    public CrawlSummary crawlSource(UUID sourceId) {
        Optional<SourceRecord> opt = Optional.empty();
        try {
            opt = sourceDao.findById(sourceId);
        } catch (Exception e) {
            return new CrawlSummary(sourceId, "Unknown", false, 0, "ERROR", e.getMessage());
        }

        if (opt.isEmpty()) {
            return new CrawlSummary(sourceId, "Unknown", false, 0, "NOT_FOUND", "Source not found");
        }

        SourceRecord source = opt.get();

        // STRICT RULE: A non-allowed source is NEVER called or crawled
        if (!source.isAllowed()) {
            log.warn("Crawl rejected: Source '{}' ({}) is not allowed by admin.", source.name(), sourceId);
            return new CrawlSummary(sourceId, source.name(), false, 0, "BLOCKED", "Source is not allowed by admin.");
        }

        try {
            // 1. SSRF Check
            SsrfProtectionValidator.validateSafeUrl(source.url());

            // 2. Robots.txt check
            RobotsCheckResult robotsCheck = robotsTxtService.checkUrl(source.url());
            sourceDao.updateRobotsCheck(source.id(), robotsCheck.robotsStatus(), robotsCheck.crawlDelaySeconds(), robotsCheck.message());

            if (!robotsCheck.allowed()) {
                String pauseMsg = "Source paused: robots.txt disallows crawl (" + robotsCheck.message() + ")";
                sourceDao.pauseSource(source.id(), pauseMsg);
                return new CrawlSummary(sourceId, source.name(), false, 0, "PAUSED", pauseMsg);
            }

            int crawlDelay = Math.max(5, Math.max(robotsCheck.crawlDelaySeconds(), source.crawlDelaySeconds()));

            // 3. Fetch offers page
            HttpResponseData pageResponse = fetchPageWithEtiquette(source.url(), crawlDelay);

            // Auto pause on 403 or 429
            if (pageResponse.statusCode == 403 || pageResponse.statusCode == 429) {
                String alert = "Source paused: HTTP " + pageResponse.statusCode + " "
                        + (pageResponse.statusCode == 429 ? "Too Many Requests" : "Forbidden");
                sourceDao.pauseSource(source.id(), alert);
                return new CrawlSummary(sourceId, source.name(), false, 0, "PAUSED", alert);
            }

            if (pageResponse.statusCode != 200) {
                sourceDao.updateStatus(source.id(), "WARNING", "Site responded with HTTP " + pageResponse.statusCode, null);
                return new CrawlSummary(sourceId, source.name(), false, 0, "WARNING", "HTTP status: " + pageResponse.statusCode);
            }

            // 4. Extract offers
            Document doc = Jsoup.parse(pageResponse.body, source.url());
            CareerPageExtractor.ExtractionOutcome outcome = extractor.extractFromDocument(
                    doc, source.url(), source.cssSelectors(), null
            );

            // JavaScript CSR check
            if (outcome.isJavascriptOnly()) {
                sourceDao.updateStatus(source.id(), "unsupported (JavaScript)",
                        "Offers not in static HTML (rendered via JS). Direct search link suggested.", null);
                return new CrawlSummary(sourceId, source.name(), false, 0, "unsupported (JavaScript)", outcome.message());
            }

            List<ExtractedOffer> offers = outcome.offers();
            int countSaved = 0;

            for (ExtractedOffer offer : offers) {
                if (countSaved >= MAX_PAGES_PER_CRAWL) break;

                // Check duplicate by content_hash
                Optional<UUID> existingOffer = offerDao.findOfferByHash(offer.contentHash());

                if (source.autoPublish() && !"AI_FALLBACK".equals(offer.extractionLevel())) {
                    // Auto-publish rule: Direct publication into offers
                    offerDao.insertSourcedOffer(
                            source.id(),
                            offer.title(),
                            offer.employer(),
                            offer.location(),
                            offer.durationMonths(),
                            offer.excerpt(),
                            offer.canonicalUrl(),
                            offer.domain(),
                            offer.compensationType(),
                            offer.contentHash()
                    );
                    countSaved++;
                } else {
                    // Route to validation queue
                    String fieldsJson = mapper.writeValueAsString(Map.of(
                            "title", offer.title(),
                            "employer", offer.employer(),
                            "location", offer.location(),
                            "durationMonths", offer.durationMonths(),
                            "canonicalUrl", offer.canonicalUrl(),
                            "postedDate", offer.postedDate().toString(),
                            "extractionLevel", offer.extractionLevel(),
                            "contentHash", offer.contentHash()
                    ));

                    validationDao.addItem(
                            source.id(),
                            offer.excerpt(),
                            fieldsJson,
                            offer.confidenceScore(),
                            existingOffer.orElse(null)
                    );
                    countSaved++;
                }
            }

            sourceDao.recordRun(source.id(), Instant.now(), countSaved);
            return new CrawlSummary(sourceId, source.name(), true, countSaved, "SUCCESS", "Extracted " + countSaved + " offers");

        } catch (SecurityException se) {
            try {
                sourceDao.pauseSource(source.id(), "Source paused: " + se.getMessage());
            } catch (Exception ignored) {}
            return new CrawlSummary(sourceId, source.name(), false, 0, "PAUSED", se.getMessage());
        } catch (Exception e) {
            log.error("Crawl error for source '{}': {}", source.name(), e.getMessage(), e);
            try {
                sourceDao.updateStatus(source.id(), "FAILING", e.getMessage(), e.getMessage());
            } catch (Exception ignored) {}
            return new CrawlSummary(sourceId, source.name(), false, 0, "FAILING", e.getMessage());
        }
    }

    /**
     * Checks liveness of active sourced offers:
     * - An offer whose URL no longer responds is marked "may be closed"
     * - Hidden/closed after 14 days
     */
    public int checkOffersLiveness() {
        int checked = 0;
        try {
            // First, hide offers dead for > 14 days
            offerDao.hideExpiredDeadOffers(14);

            List<OfferDao.OfferRecord> offers = offerDao.findPublishedOffers();
            for (OfferDao.OfferRecord offer : offers) {
                if (offer.sourceUrl() == null || offer.sourceUrl().isBlank()) continue;

                checked++;
                try {
                    SsrfProtectionValidator.validateSafeUrl(offer.sourceUrl());
                    URI uri = URI.create(offer.sourceUrl());
                    String host = uri.getHost();

                    enforceRateLimit(host, 5);

                    HttpURLConnection conn = (HttpURLConnection) URI.create(offer.sourceUrl()).toURL().openConnection();
                    conn.setRequestMethod("HEAD");
                    conn.setRequestProperty("User-Agent", RobotsTxtService.USER_AGENT);
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(4000);
                    conn.setInstanceFollowRedirects(false);

                    int code = conn.getResponseCode();
                    if (code == 404 || code == 410) {
                        offerDao.markOfferDead(offer.id(), LocalDate.now());
                    } else if (code >= 200 && code < 400) {
                        offerDao.markOfferLive(offer.id());
                    }
                } catch (Exception deadEx) {
                    offerDao.markOfferDead(offer.id(), LocalDate.now());
                }
            }
        } catch (Exception e) {
            log.error("Error running offer liveness check: {}", e.getMessage());
        }
        return checked;
    }

    /**
     * Polite HTTP fetcher respecting Crawl-delay, domain rate limiting, timeouts, and max page size.
     */
    private HttpResponseData fetchPageWithEtiquette(String urlString, int crawlDelaySeconds) throws Exception {
        URI uri = URI.create(urlString);
        String host = uri.getHost();

        // Rate limiting: at most 1 request every 5s (or crawl delay)
        enforceRateLimit(host, crawlDelaySeconds);

        HttpURLConnection conn = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", RobotsTxtService.USER_AGENT);
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setInstanceFollowRedirects(false); // Disallow cross-domain redirects

        int status = conn.getResponseCode();

        // Check if redirect
        if (status >= 300 && status < 400) {
            String redirectUrl = conn.getHeaderField("Location");
            if (redirectUrl != null) {
                URI redirectUri = URI.create(redirectUrl);
                if (redirectUri.getHost() != null && !redirectUri.getHost().equalsIgnoreCase(host)) {
                    throw new SecurityException("Cross-domain redirect from " + host + " to " + redirectUri.getHost() + " blocked");
                }
                // Allowed same-domain redirect
                return fetchPageWithEtiquette(redirectUrl, crawlDelaySeconds);
            }
        }

        if (status == 403 || status == 429) {
            return new HttpResponseData(status, "");
        }

        if (status != 200) {
            return new HttpResponseData(status, "");
        }

        // Read up to 2MB
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (InputStream in = conn.getInputStream()) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
                if (baos.size() > MAX_PAGE_SIZE_BYTES) {
                    break;
                }
            }
        }

        String body = baos.toString(StandardCharsets.UTF_8);
        return new HttpResponseData(status, body);
    }

    private void enforceRateLimit(String host, int delaySeconds) {
        if (host == null) return;
        long minIntervalMs = Math.max(5, delaySeconds) * 1000L;
        long now = System.currentTimeMillis();
        Long last = lastRequestPerDomain.get(host);
        if (last != null) {
            long elapsed = now - last;
            if (elapsed < minIntervalMs) {
                try {
                    Thread.sleep(minIntervalMs - elapsed);
                } catch (InterruptedException ignored) {}
            }
        }
        lastRequestPerDomain.put(host, System.currentTimeMillis());
    }

    private record HttpResponseData(int statusCode, String body) {}
}
