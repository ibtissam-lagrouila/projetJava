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
import com.fitwell.service.QueryParserService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

public final class OfferHandler {

    private final OfferDao offerDao;
    private final StudentProfileDao studentProfileDao;
    private final MatchingService matchingService;
    private final QueryParserService queryParserService;

    public OfferHandler(OfferDao offerDao,
                        StudentProfileDao studentProfileDao,
                        MatchingService matchingService,
                        QueryParserService queryParserService) {
        this.offerDao = offerDao;
        this.studentProfileDao = studentProfileDao;
        this.matchingService = matchingService;
        this.queryParserService = queryParserService;
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

        ParsedQueryResponse parsedQuery = null;
        if (freeQuery != null && !freeQuery.isBlank()) {
            parsedQuery = queryParserService.parse(freeQuery);
            if (cityFilter == null && parsedQuery.city() != null) {
                cityFilter = parsedQuery.city();
            }
        }

        List<OfferRecord> rawOffers = offerDao.findPublishedOffers();
        List<OfferCardResponse> scoredOffers = new ArrayList<>();

        int minScore = 0;
        if (minScoreParam != null && !minScoreParam.isBlank()) {
            try {
                minScore = Integer.parseInt(minScoreParam);
            } catch (NumberFormatException ignored) {}
        }

        for (OfferRecord offer : rawOffers) {
            ScoreBreakdown score = matchingService.computeScore(profile, offer);

            // Filter by minimum score
            if (score.overallScore() < minScore) {
                continue;
            }

            // Filter by city
            if (cityFilter != null && !cityFilter.isBlank() && !"all".equalsIgnoreCase(cityFilter)) {
                String loc = (offer.location() != null ? offer.location() : "").toLowerCase(Locale.ROOT);
                String cFilter = cityFilter.toLowerCase(Locale.ROOT);
                if (!loc.contains(cFilter)) {
                    continue;
                }
            }

            // Filter by domain
            if (domainFilter != null && !domainFilter.isBlank() && !"all".equalsIgnoreCase(domainFilter)) {
                String dOffer = (offer.domain() != null ? offer.domain() : "").toLowerCase(Locale.ROOT);
                String dFilter = domainFilter.toLowerCase(Locale.ROOT);
                if (!dOffer.contains(dFilter)) {
                    continue;
                }
            }

            // Filter by query domains if freeQuery was parsed
            if (parsedQuery != null && !parsedQuery.domains().isEmpty()) {
                boolean matchDomain = false;
                String dOffer = (
                        (offer.domain() != null ? offer.domain() : "") + " " +
                        (offer.title() != null ? offer.title() : "") + " " +
                        (offer.description() != null ? offer.description() : "")
                ).toLowerCase(Locale.ROOT);

                for (String qd : parsedQuery.domains()) {
                    if (dOffer.contains(qd.toLowerCase(Locale.ROOT))) {
                        matchDomain = true;
                        break;
                    }
                }
                if (!matchDomain) {
                    continue;
                }
            }

            // Filter by duration
            if (durationParam != null && !durationParam.isBlank() && !"all".equalsIgnoreCase(durationParam)) {
                try {
                    int d = Integer.parseInt(durationParam);
                    if (offer.durationMonths() != d) {
                        continue;
                    }
                } catch (NumberFormatException ignored) {}
            } else if (parsedQuery != null && parsedQuery.minDurationMonths() != null && parsedQuery.maxDurationMonths() != null) {
                if (offer.durationMonths() < parsedQuery.minDurationMonths() || offer.durationMonths() > parsedQuery.maxDurationMonths()) {
                    continue;
                }
            }

            // Filter by funding if parsed
            if (parsedQuery != null && "Funded".equalsIgnoreCase(parsedQuery.compensation())) {
                String comp = (offer.compensationType() != null ? offer.compensationType() : "").toLowerCase(Locale.ROOT);
                if (!comp.contains("fund") && !comp.contains("remun") && !comp.contains("paid")) {
                    continue;
                }
            }

            scoredOffers.add(mapRecordToCardResponse(offer, score));
        }

        // Sort by overall match score descending, then by remaining days ascending
        scoredOffers.sort((a, b) -> {
            int scoreCmp = Integer.compare(b.score().overallScore(), a.score().overallScore());
            if (scoreCmp != 0) return scoreCmp;
            return Long.compare(a.daysRemaining(), b.daysRemaining());
        });

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("offers", scoredOffers);
        resp.put("totalCount", scoredOffers.size());
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
        record ParseReq(String query) {}
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

        StudentHomeResponse home = new StudentHomeResponse(
                profile != null ? profile.fullName() : auth.user().email(),
                profile != null ? profile.university() : "Moroccan University",
                profile != null ? profile.level() : "Master 1",
                appCounts.getOrDefault("total", 0),
                appCounts.getOrDefault("inReview", 0),
                appCounts.getOrDefault("interview", 0),
                bestMatchToday,
                recommended
        );

        return HttpResponse.ok(home);
    }

    private OfferCardResponse mapRecordToCardResponse(OfferRecord o, ScoreBreakdown score) {
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
                o.closesAt(),
                o.daysRemaining(),
                o.skills(),
                score
        );
    }

    private String hashIp(String ip) {
        if (ip == null) return "unknown";
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

