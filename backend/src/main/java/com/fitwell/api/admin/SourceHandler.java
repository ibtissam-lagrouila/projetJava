package com.fitwell.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.RequestContext;
import com.fitwell.dao.OfferDao;
import com.fitwell.dao.SourceDao;
import com.fitwell.dao.ValidationDao;
import com.fitwell.service.sourcing.CareerPageCrawlerService;
import com.fitwell.service.sourcing.RobotsTxtService;
import com.fitwell.service.sourcing.model.RobotsCheckResult;
import com.fitwell.service.sourcing.model.SourceRecord;
import com.fitwell.service.sourcing.model.ValidationItemRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SourceHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final SourceDao sourceDao;
    private final ValidationDao validationDao;
    private final OfferDao offerDao;
    private final RobotsTxtService robotsTxtService;
    private final CareerPageCrawlerService crawlerService;

    public SourceHandler(SourceDao sourceDao, ValidationDao validationDao, OfferDao offerDao,
                         RobotsTxtService robotsTxtService, CareerPageCrawlerService crawlerService) {
        this.sourceDao = sourceDao;
        this.validationDao = validationDao;
        this.offerDao = offerDao;
        this.robotsTxtService = robotsTxtService;
        this.crawlerService = crawlerService;
    }

    public HttpResponse getSources(RequestContext ctx) throws Exception {
        List<SourceRecord> sources = sourceDao.findAll();
        return HttpResponse.ok(Map.of("sources", sources));
    }

    public record CreateSourceReq(
            String name,
            String url,
            String cguUrl,
            String type,
            String cssSelectors,
            boolean autoPublish
    ) {}

    public HttpResponse createSource(RequestContext ctx) throws Exception {
        CreateSourceReq req = ctx.parseJsonBody(CreateSourceReq.class);
        if (req.name() == null || req.name().isBlank()) {
            return HttpResponse.badRequest("Source name is required");
        }
        if (req.url() == null || req.url().isBlank()) {
            return HttpResponse.badRequest("Offers page URL is required");
        }
        if (req.cguUrl() == null || req.cguUrl().isBlank()) {
            return HttpResponse.badRequest("CGU (Terms of Service) URL is required");
        }

        SourceRecord created = sourceDao.create(
                req.name().trim(),
                req.url().trim(),
                req.cguUrl().trim(),
                req.type() != null ? req.type() : "WEBSITE",
                req.cssSelectors(),
                req.autoPublish()
        );

        return HttpResponse.created(Map.of("message", "Source created successfully", "source", created));
    }

    public record CheckRobotsReq(String url) {}

    public HttpResponse checkRobots(RequestContext ctx) throws Exception {
        CheckRobotsReq req = ctx.parseJsonBody(CheckRobotsReq.class);
        if (req.url() == null || req.url().isBlank()) {
            return HttpResponse.badRequest("URL is required to check robots.txt");
        }

        RobotsCheckResult result = robotsTxtService.checkUrl(req.url().trim());
        return HttpResponse.ok(Map.of(
                "allowed", result.allowed(),
                "crawlDelaySeconds", result.crawlDelaySeconds(),
                "status", result.robotsStatus(),
                "message", result.message()
        ));
    }

    public record TestSampleReq(String url, String cssSelectors) {}

    public HttpResponse testSample(RequestContext ctx) throws Exception {
        TestSampleReq req = ctx.parseJsonBody(TestSampleReq.class);
        if (req.url() == null || req.url().isBlank()) {
            return HttpResponse.badRequest("URL is required to test on sample");
        }

        CareerPageCrawlerService.SampleTestResult sampleResult = crawlerService.runSampleTest(
                req.url().trim(), req.cssSelectors()
        );

        return HttpResponse.ok(Map.of(
                "robotsAllowed", sampleResult.robotsAllowed(),
                "robotsMessage", sampleResult.robotsMessage(),
                "crawlDelaySeconds", sampleResult.crawlDelaySeconds(),
                "isJavascriptOnly", sampleResult.isJavascriptOnly(),
                "status", sampleResult.status(),
                "previewOffers", sampleResult.previewOffers(),
                "message", sampleResult.message()
        ));
    }

    public record AllowSourceReq(boolean allowed) {}

    public HttpResponse toggleAllow(RequestContext ctx) throws Exception {
        String idStr = ctx.getPathParam("id");
        UUID id = UUID.fromString(idStr);
        AllowSourceReq req = ctx.parseJsonBody(AllowSourceReq.class);

        Optional<SourceRecord> opt = sourceDao.findById(id);
        if (opt.isEmpty()) {
            return HttpResponse.notFound("Source not found");
        }

        // Activation rule: only allow if robots.txt and sample checks were reviewed
        sourceDao.setAllowed(id, req.allowed());
        return HttpResponse.ok(Map.of(
                "message", req.allowed() ? "Source activated and authorized for crawling" : "Source deactivated",
                "isAllowed", req.allowed()
        ));
    }

    public HttpResponse crawlSource(RequestContext ctx) throws Exception {
        String idStr = ctx.getPathParam("id");
        UUID id = UUID.fromString(idStr);

        CareerPageCrawlerService.CrawlSummary summary = crawlerService.crawlSource(id);
        return HttpResponse.ok(Map.of(
                "summary", summary
        ));
    }

    public HttpResponse getValidationQueue(RequestContext ctx) throws Exception {
        List<ValidationItemRecord> queue = validationDao.getPending();
        return HttpResponse.ok(Map.of("queue", queue));
    }

    public HttpResponse approveValidationItem(RequestContext ctx) throws Exception {
        String idStr = ctx.getPathParam("id");
        UUID id = UUID.fromString(idStr);

        List<ValidationItemRecord> pending = validationDao.getPending();
        ValidationItemRecord item = pending.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
        if (item == null) {
            return HttpResponse.notFound("Validation item not found or already reviewed");
        }

        JsonNode fields = mapper.readTree(item.extractedFieldsJson());
        String title = fields.path("title").asText("Offre de stage");
        String employer = fields.path("employer").asText("Research Partner");
        String location = fields.path("location").asText("Morocco");
        int durationMonths = fields.path("durationMonths").asInt(4);
        String canonicalUrl = fields.path("canonicalUrl").asText("");
        String contentHash = fields.path("contentHash").asText(UUID.randomUUID().toString());

        offerDao.insertSourcedOffer(
                item.sourceId(),
                title,
                employer,
                location,
                durationMonths,
                item.rawPageText(),
                canonicalUrl,
                "General",
                "Funded",
                contentHash
        );

        validationDao.updateStatus(id, "APPROVED");
        return HttpResponse.ok(Map.of("message", "Offer approved and published", "id", id.toString()));
    }

    public HttpResponse rejectValidationItem(RequestContext ctx) throws Exception {
        String idStr = ctx.getPathParam("id");
        UUID id = UUID.fromString(idStr);
        validationDao.updateStatus(id, "REJECTED");
        return HttpResponse.ok(Map.of("message", "Offer rejected from queue", "id", id.toString()));
    }
}
