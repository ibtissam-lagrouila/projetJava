package com.fitwell.api.student;

import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.HttpStatus;
import com.fitwell.core.http.RequestContext;
import com.fitwell.core.middleware.AuthMiddleware;
import com.fitwell.core.security.AuthContext;
import com.fitwell.dao.OfferDao;
import com.fitwell.dao.OfferDao.OfferRecord;
import com.fitwell.dao.StudentProfileDao;
import com.fitwell.dao.StudentProfileDao.StudentFullProfile;
import com.fitwell.model.dto.OfferDtos.*;
import com.fitwell.service.MatchingService;
import com.fitwell.service.OfferLinkVerifierService;
import com.fitwell.service.QueryParserService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public final class OfferHandler {

    private final OfferDao offerDao;
    private final StudentProfileDao studentProfileDao;
    private final MatchingService matchingService;
    private final QueryParserService queryParserService;
    private final OfferLinkVerifierService linkVerifier;

    public OfferHandler(OfferDao offerDao,
            StudentProfileDao studentProfileDao,
            MatchingService matchingService,
            QueryParserService queryParserService) {
        this(offerDao, studentProfileDao, matchingService, queryParserService, new OfferLinkVerifierService());
    }

    public OfferHandler(OfferDao offerDao,
            StudentProfileDao studentProfileDao,
            MatchingService matchingService,
            QueryParserService queryParserService,
            OfferLinkVerifierService linkVerifier) {
        this.offerDao = offerDao;
        this.studentProfileDao = studentProfileDao;
        this.matchingService = matchingService;
        this.queryParserService = queryParserService;
        this.linkVerifier = linkVerifier != null ? linkVerifier : new OfferLinkVerifierService();
    }

    public HttpResponse getOffers(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        StudentFullProfile profile = null;
        if (auth != null && auth.user() != null) {
            profile = studentProfileDao.findFullProfileByUserId(auth.getUserId()).orElse(null);
        }

        String freeQuery = ctx.getQueryParam("query");
        String cityFilter = ctx.getQueryParam("city");
        String domainFilter = ctx.getQueryParam("domain");
        String durationParam = ctx.getQueryParam("duration");
        String minScoreParam = ctx.getQueryParam("minScore");
        String sortBy = ctx.getQueryParam("sort"); // "bestMatch" or "closesSoon"

        ParsedQueryResponse parsedQuery = null;
        if (freeQuery != null && !freeQuery.isBlank()) {
            parsedQuery = queryParserService.parse(freeQuery);
        }

        List<OfferRecord> rawOffers = offerDao.findPublishedOffers();
        List<OfferCardResponse> scoredOffers = new ArrayList<>();

        int minScore = 0;
        if (minScoreParam != null && !minScoreParam.isBlank()) {
            try {
                minScore = Integer.parseInt(minScoreParam);
            } catch (NumberFormatException ignored) {
            }
        }

        for (OfferRecord offer : rawOffers) {
            ScoreBreakdown score = matchingService.computeScore(profile, offer, parsedQuery);

            // Filter by minimum score
            if (score.overallScore() < minScore) {
                continue;
            }

            // Filter by city if explicit dropdown filter is set (not "all")
            if (cityFilter != null && !cityFilter.isBlank() && !"all".equalsIgnoreCase(cityFilter)) {
                String loc = (offer.location() != null ? offer.location() : "").toLowerCase(Locale.ROOT);
                String cFilter = cityFilter.toLowerCase(Locale.ROOT);
                if (!loc.contains(cFilter)) {
                    continue;
                }
            }

            // Filter by domain if explicit dropdown filter is set (not "all")
            if (domainFilter != null && !domainFilter.isBlank() && !"all".equalsIgnoreCase(domainFilter)) {
                String dOffer = (offer.domain() != null ? offer.domain() : "").toLowerCase(Locale.ROOT);
                String dFilter = domainFilter.toLowerCase(Locale.ROOT);
                if (!dOffer.contains(dFilter)) {
                    continue;
                }
            }

            // Filter by duration if explicit dropdown filter is set (not "all")
            if (durationParam != null && !durationParam.isBlank() && !"all".equalsIgnoreCase(durationParam)) {
                try {
                    int d = Integer.parseInt(durationParam);
                    if (offer.durationMonths() != d) {
                        continue;
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            scoredOffers.add(mapRecordToCardResponse(offer, score));
        }

        // Sort: "MAY_BE_CLOSED" always sinks to the bottom
        java.util.Comparator<OfferCardResponse> statusComparator = (a, b) -> {
            boolean aMay = a.isMayBeClosed();
            boolean bMay = b.isMayBeClosed();
            return Boolean.compare(aMay, bMay);
        };

        if ("closesSoon".equalsIgnoreCase(sortBy)) {
            scoredOffers.sort(statusComparator.thenComparing((a, b) -> {
                int cmp = Long.compare(a.daysRemaining(), b.daysRemaining());
                if (cmp != 0)
                    return cmp;
                return Integer.compare(b.score().overallScore(), a.score().overallScore());
            }));
        } else {
            // Default: "MAY_BE_CLOSED" at bottom, then overall match score desc, then
            // remaining days asc
            scoredOffers.sort(statusComparator.thenComparing((a, b) -> {
                int scoreCmp = Integer.compare(b.score().overallScore(), a.score().overallScore());
                if (scoreCmp != 0)
                    return scoreCmp;
                return Long.compare(a.daysRemaining(), b.daysRemaining());
            }));
        }

        String joobleKey = System.getenv("JOOBLE_API_KEY");
        boolean hasJooble = joobleKey != null && !joobleKey.isBlank();
        boolean hasActiveCareerSources = offerDao.hasActiveExternalSources();
        boolean externalSourcesConfigured = hasJooble || hasActiveCareerSources;
        String externalSourcesMissingReason = externalSourcesConfigured
                ? null
                : "No live external source connected. JOOBLE_API_KEY is not configured and no active approved CareerPageSource is registered.";

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("offers", scoredOffers);
        resp.put("totalCount", scoredOffers.size());
        resp.put("externalSourcesConfigured", externalSourcesConfigured);
        if (externalSourcesMissingReason != null) {
            resp.put("externalSourcesMissingReason", externalSourcesMissingReason);
        }
        if (parsedQuery != null) {
            resp.put("parsedQuery", parsedQuery);
        }

        return HttpResponse.ok(resp);
    }

    public HttpResponse getOfferById(RequestContext ctx) throws Exception {
        String idStr = ctx.getPathParam("id");
        UUID offerId;
        try {
            offerId = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid offer ID format");
        }

        Optional<OfferRecord> offerOpt = offerDao.findOfferById(offerId);
        if (offerOpt.isEmpty()) {
            return HttpResponse.notFound("Offer not found");
        }

        OfferRecord offer = offerOpt.get();

        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        StudentFullProfile profile = null;
        UUID profileId = null;
        if (auth != null && auth.user() != null) {
            profile = studentProfileDao.findFullProfileByUserId(auth.getUserId()).orElse(null);
            if (profile != null) {
                profileId = profile.id();
            }
        }

        // Record offer view with IP hash for honest tracking
        String ipHash = hashIp(ctx.getClientIp());
        offerDao.recordOfferView(offerId, profileId, ipHash);

        ScoreBreakdown score = matchingService.computeScore(profile, offer);
        OfferCardResponse card = mapRecordToCardResponse(offer, score);

        return HttpResponse.ok(card);
    }

    public HttpResponse parseQuery(RequestContext ctx) throws Exception {
        record ParseReq(String query) {
        }
        ParseReq req = ctx.parseJsonBody(ParseReq.class);
        ParsedQueryResponse parsed = queryParserService.parse(req.query());
        return HttpResponse.ok(parsed);
    }

    public HttpResponse getStudentHome(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        if (auth == null || auth.user() == null) {
            return HttpResponse.unauthorized("Authentication required");
        }

        StudentFullProfile profile = studentProfileDao.findFullProfileByUserId(auth.getUserId()).orElse(null);
        UUID profileId = profile != null ? profile.id() : null;

        Map<String, Integer> appCounts = offerDao.getApplicationCountsForStudent(profileId);

        List<OfferRecord> rawOffers = offerDao.findPublishedOffers();
        List<OfferCardResponse> scoredOffers = new ArrayList<>();

        for (OfferRecord o : rawOffers) {
            ScoreBreakdown score = matchingService.computeScore(profile, o);
            scoredOffers.add(mapRecordToCardResponse(o, score));
        }

        // Sort by match score desc
        scoredOffers.sort((a, b) -> Integer.compare(b.score().overallScore(), a.score().overallScore()));

        OfferCardResponse bestMatchToday = !scoredOffers.isEmpty() ? scoredOffers.get(0) : null;
        List<OfferCardResponse> recommended = scoredOffers.stream().skip(1).limit(4).collect(Collectors.toList());
        List<WeeklyActionItem> weeklyActions = offerDao.getWeeklyActionsForStudent(profileId);

        StudentHomeResponse home = new StudentHomeResponse(
                profile != null ? profile.fullName() : auth.user().email(),
                profile != null ? profile.university() : "Moroccan University",
                profile != null ? profile.level() : "Master 1",
                appCounts.getOrDefault("total", 0),
                appCounts.getOrDefault("inReview", 0),
                appCounts.getOrDefault("interview", 0),
                bestMatchToday,
                recommended,
                weeklyActions);

        return HttpResponse.ok(home);
    }

    public HttpResponse applyToOffer(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        if (auth == null || auth.user() == null) {
            return HttpResponse.unauthorized("Authentication required");
        }

        StudentFullProfile profile = studentProfileDao.findFullProfileByUserId(auth.getUserId()).orElse(null);
        if (profile == null) {
            return HttpResponse.badRequest("Student profile not found. Please complete profile first.");
        }

        ApplyOfferRequest req = ctx.parseJsonBody(ApplyOfferRequest.class);
        if (req == null || req.offerId() == null) {
            return HttpResponse.badRequest("offerId is required");
        }

        UUID offerId;
        try {
            offerId = UUID.fromString(req.offerId());
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid offer ID format");
        }

        boolean isManual = Boolean.TRUE.equals(req.isManualExternal());
        boolean confirmed = Boolean.TRUE.equals(req.confirmed());

        // For internal offers: explicit confirmation is required
        if (!isManual && !confirmed) {
            return HttpResponse.badRequest("Explicit confirmation is required before applying with Fitwell.");
        }

        var application = offerDao.submitApplication(profile.id(), offerId, req.coverLetter(), isManual, confirmed);

        String msg = isManual
                ? "External application tracking recorded in Applications"
                : "Application submitted successfully via Fitwell";

        return HttpResponse.created(Map.of(
                "message", msg,
                "application", application));
    }

    public HttpResponse verifyOfferLink(RequestContext ctx) throws Exception {
        String idStr = ctx.getPathParam("id");
        UUID offerId;
        try {
            offerId = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid offer ID format");
        }

        Optional<OfferRecord> offerOpt = offerDao.findOfferById(offerId);
        if (offerOpt.isEmpty()) {
            return HttpResponse.notFound("Offer not found");
        }

        OfferRecord offer = offerOpt.get();
        if (!"SOURCED".equalsIgnoreCase(offer.origin()) || offer.sourceUrl() == null || offer.sourceUrl().isBlank()) {
            return HttpResponse.ok(Map.of(
                    "alive", true,
                    "status", offer.status(),
                    "sourceUrl", offer.sourceUrl() != null ? offer.sourceUrl() : ""));
        }

        var verification = linkVerifier.verify(offer.sourceUrl());
        if (!verification.alive()) {
            offerDao.markOfferDead(offerId, LocalDate.now(offerDao.getClock()));
            return HttpResponse.ok(Map.of(
                    "alive", false,
                    "status", "MAY_BE_CLOSED",
                    "reason", verification.reason(),
                    "sourceUrl", offer.sourceUrl()));
        }

        return HttpResponse.ok(Map.of(
                "alive", true,
                "status", offer.status(),
                "sourceUrl", offer.sourceUrl()));
    }

    public HttpResponse getMyApplications(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        if (auth == null || auth.user() == null) {
            return HttpResponse.unauthorized("Authentication required");
        }

        StudentFullProfile profile = studentProfileDao.findFullProfileByUserId(auth.getUserId()).orElse(null);
        if (profile == null) {
            return HttpResponse.ok(Map.of("applications", List.of()));
        }

        List<OfferDao.StudentApplicationItem> apps = offerDao.getStudentApplicationsList(profile.id());
        return HttpResponse.ok(Map.of("applications", apps));
    }

    private OfferCardResponse mapRecordToCardResponse(OfferRecord o, ScoreBreakdown score) {
        boolean isMayBeClosed = "MAY_BE_CLOSED".equalsIgnoreCase(o.status());
        return new OfferCardResponse(
                o.id(),
                o.labId(),
                o.labName(),
                o.labUniversity(),
                o.labCity(),
                o.title(),
                o.domain(),
                o.durationMonths(),
                o.startDate(),
                o.location(),
                o.compensationType(),
                o.compensationAmount(),
                o.description(),
                o.tasks(),
                o.places(),
                o.status(),
                o.origin(),
                o.sourceName(),
                o.sourceUrl(),
                o.closesAt(),
                o.daysRemaining(),
                o.skills(),
                score,
                o.displayDate(),
                isMayBeClosed);
    }

    private String hashIp(String ip) {
        if (ip == null)
            return "unknown";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "hash-err";
        }
    }
}
