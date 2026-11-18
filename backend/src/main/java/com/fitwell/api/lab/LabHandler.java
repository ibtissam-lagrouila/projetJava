package com.fitwell.api.lab;

import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.RequestContext;
import com.fitwell.core.middleware.AuthMiddleware;
import com.fitwell.core.security.AuthContext;
import com.fitwell.dao.CandidateDao;
import com.fitwell.dao.CandidateDao.CandidateApplicationRecord;
import com.fitwell.dao.CandidateDao.OfferSummary;
import com.fitwell.dao.LabDao;
import com.fitwell.dao.LabMemberDao;
import com.fitwell.dao.OfferDao;
import com.fitwell.model.dto.LabDashboardDtos.*;
import com.fitwell.model.dto.OfferDtos.CreateLabOfferRequest;
import com.fitwell.model.enums.ApplicationStatus;
import com.fitwell.service.LabDashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public final class LabHandler {

    private static final Logger log = LoggerFactory.getLogger(LabHandler.class);

    private final LabDashboardService labDashboardService;
    private final LabMemberDao labMemberDao;
    private final LabDao labDao;
    private final OfferDao offerDao;
    private final CandidateDao candidateDao;

    public LabHandler(LabDashboardService labDashboardService, LabMemberDao labMemberDao, LabDao labDao, OfferDao offerDao) {
        this(labDashboardService, labMemberDao, labDao, offerDao, null);
    }

    public LabHandler(LabDashboardService labDashboardService, LabMemberDao labMemberDao, LabDao labDao, OfferDao offerDao, CandidateDao candidateDao) {
        this.labDashboardService = labDashboardService;
        this.labMemberDao = labMemberDao;
        this.labDao = labDao;
        this.offerDao = offerDao;
        this.candidateDao = candidateDao;
    }

    public HttpResponse getDashboard(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        if (auth == null || auth.user() == null) {
            return HttpResponse.unauthorized("Authentication required");
        }

        // Check if a specific lab was requested via header (never query param)
        String headerLabId = ctx.getHeader("X-Lab-Id");
        UUID requestedLabId = null;
        if (headerLabId != null && !headerLabId.isBlank()) {
            try {
                requestedLabId = UUID.fromString(headerLabId.trim());
            } catch (IllegalArgumentException e) {
                return HttpResponse.badRequest("Invalid X-Lab-Id header format");
            }
        } else if (auth.labId() != null) {
            requestedLabId = auth.labId();
        }

        String fullName = (auth.user().email() != null && !auth.user().email().isBlank())
                ? auth.user().email().split("@")[0]
                : "Recruiter";

        try {
            LabDashboardResponse resp = labDashboardService.getDashboard(auth.getUserId(), fullName, requestedLabId);
            return HttpResponse.ok(resp);
        } catch (NoSuchElementException e) {
            return HttpResponse.notFound("Lab not found or access not permitted");
        } catch (IllegalArgumentException e) {
            if ("USER_NOT_LAB_MEMBER".equals(e.getMessage())) {
                return HttpResponse.forbidden("User is not a member of any laboratory");
            }
            return HttpResponse.badRequest(e.getMessage());
        }
    }

    public HttpResponse shortlistCandidates(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        if (auth == null || auth.user() == null) {
            return HttpResponse.unauthorized("Authentication required");
        }

        ShortlistCandidatesRequest req = ctx.parseJsonBody(ShortlistCandidatesRequest.class);
        if (req == null || req.applicationIds() == null || req.applicationIds().isEmpty()) {
            return HttpResponse.badRequest("applicationIds list is required");
        }

        if (!Boolean.TRUE.equals(req.confirmed())) {
            return HttpResponse.badRequest("Explicit confirmation is required to shortlist candidates");
        }

        UUID labId = resolveLabId(auth, ctx.getHeader("X-Lab-Id"));
        if (labId == null) {
            return HttpResponse.forbidden("User is not a member of any laboratory");
        }

        if (labMemberDao.findByUserAndLab(auth.getUserId(), labId).isEmpty()) {
            return HttpResponse.notFound("Lab not found");
        }

        List<UUID> appUuids = new ArrayList<>();
        for (String idStr : req.applicationIds()) {
            try {
                appUuids.add(UUID.fromString(idStr));
            } catch (IllegalArgumentException ignored) {}
        }

        int count = labDashboardService.shortlistCandidates(labId, appUuids, auth.getUserId());
        return HttpResponse.ok(Map.of(
                "message", "Successfully shortlisted " + count + " candidates",
                "shortlistedCount", count
        ));
    }

    public HttpResponse createOffer(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        if (auth == null || auth.user() == null) {
            return HttpResponse.unauthorized("Authentication required");
        }

        UUID labId = resolveLabId(auth, ctx.getHeader("X-Lab-Id"));
        if (labId == null) {
            return HttpResponse.forbidden("User is not a member of any laboratory");
        }

        var membershipOpt = labMemberDao.findByUserAndLab(auth.getUserId(), labId);
        if (membershipOpt.isEmpty()) {
            return HttpResponse.notFound("Lab not found");
        }
        if (!labDao.isLabApproved(labId)) {
            return HttpResponse.forbidden("Your lab is pending approval. You cannot publish offers until verified.");
        }

        CreateLabOfferRequest req = ctx.parseJsonBody(CreateLabOfferRequest.class);
        if (req == null || req.title() == null || req.title().isBlank()) {
            return HttpResponse.badRequest("Offer title is required");
        }

        UUID offerId = offerDao.createLabOffer(labId, req);
        log.info("Lab {} created offer {} (title: '{}')", labId, offerId, req.title());
        return HttpResponse.created(Map.of(
                "id", offerId.toString(),
                "message", "Offer published successfully"
        ));
    }

    public HttpResponse getOffers(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        if (auth == null || auth.user() == null) {
            return HttpResponse.unauthorized("Authentication required");
        }

        UUID labId = resolveLabId(auth, ctx.getHeader("X-Lab-Id"));
        if (labId == null) {
            return HttpResponse.forbidden("User is not a member of any laboratory");
        }

        var list = offerDao.findOffersByLab(labId);
        return HttpResponse.ok(list);
    }

    public HttpResponse getCandidates(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        if (auth == null || auth.user() == null) {
            return HttpResponse.unauthorized("Authentication required");
        }

        UUID labId = resolveLabId(auth, ctx.getHeader("X-Lab-Id"));
        if (labId == null) {
            return HttpResponse.forbidden("User is not a member of any laboratory");
        }

        if (!labDao.isLabApproved(labId)) {
            return HttpResponse.forbidden("Your lab request is pending approval. You cannot view candidates until verified.");
        }

        if (candidateDao == null) {
            return HttpResponse.ok(Map.of("columns", Map.of("new", List.of(), "shortlisted", List.of(), "interview", List.of(), "decision", List.of())));
        }

        String offerIdParam = ctx.getQueryParam("offerId");
        UUID requestedOfferId = null;
        if (offerIdParam != null && !offerIdParam.isBlank() && !"all".equalsIgnoreCase(offerIdParam)) {
            try {
                requestedOfferId = UUID.fromString(offerIdParam.trim());
            } catch (IllegalArgumentException ignored) {}
        }

        List<OfferSummary> offers = candidateDao.findLabOffersWithCounts(labId);
        UUID activeOfferId = requestedOfferId;
        String activeOfferTitle = "All offers";

        if (activeOfferId == null && !offers.isEmpty()) {
            activeOfferId = offers.get(0).offerId();
            activeOfferTitle = offers.get(0).title();
        } else if (activeOfferId != null) {
            for (OfferSummary o : offers) {
                if (o.offerId().equals(activeOfferId)) {
                    activeOfferTitle = o.title();
                    break;
                }
            }
        }

        List<CandidateApplicationRecord> rawList = candidateDao.findCandidates(labId, activeOfferId);

        List<Map<String, Object>> newCol = new ArrayList<>();
        List<Map<String, Object>> shortlistedCol = new ArrayList<>();
        List<Map<String, Object>> interviewCol = new ArrayList<>();
        List<Map<String, Object>> decisionCol = new ArrayList<>();

        for (CandidateApplicationRecord r : rawList) {
            int score = calculateFitScore(r.skills(), r.offerSkills(), r.level(), r.fieldOfStudy());
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", r.applicationId().toString());
            card.put("studentId", r.studentId().toString());
            card.put("offerId", r.offerId().toString());
            card.put("offerTitle", r.offerTitle());
            card.put("studentName", r.studentName());
            card.put("level", r.level());
            card.put("fieldOfStudy", r.fieldOfStudy());
            card.put("university", r.university());
            card.put("skills", r.skills() != null ? r.skills() : List.of());
            card.put("matchScore", score);
            card.put("status", r.status());
            card.put("appliedAt", r.sentAt().toString());
            card.put("lastActivityAt", r.lastActivityAt().toString());
            card.put("coverLetter", r.coverLetter());

            String st = r.status() != null ? r.status().toUpperCase() : "SENT";
            switch (st) {
                case "SHORTLISTED" -> shortlistedCol.add(card);
                case "INTERVIEW" -> interviewCol.add(card);
                case "DECISION_ACCEPTED", "DECISION_REJECTED" -> decisionCol.add(card);
                default -> newCol.add(card); // SENT, REVIEWED, DRAFT
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("selectedOfferId", activeOfferId != null ? activeOfferId.toString() : null);
        resp.put("selectedOfferTitle", activeOfferTitle);
        resp.put("offers", offers);
        resp.put("columns", Map.of(
                "new", newCol,
                "shortlisted", shortlistedCol,
                "interview", interviewCol,
                "decision", decisionCol
        ));
        resp.put("counts", Map.of(
                "new", newCol.size(),
                "shortlisted", shortlistedCol.size(),
                "interview", interviewCol.size(),
                "decision", decisionCol.size(),
                "total", rawList.size()
        ));

        return HttpResponse.ok(resp);
    }

    public HttpResponse updateApplicationStatus(RequestContext ctx) throws Exception {
        AuthContext auth = ctx.getAttribute(AuthMiddleware.AUTH_ATTRIBUTE);
        if (auth == null || auth.user() == null) {
            return HttpResponse.unauthorized("Authentication required");
        }

        UUID labId = resolveLabId(auth, ctx.getHeader("X-Lab-Id"));
        if (labId == null) {
            return HttpResponse.forbidden("User is not a member of any laboratory");
        }

        String appIdStr = ctx.getPathParam("id");
        UUID appId;
        try {
            appId = UUID.fromString(appIdStr);
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest("Invalid application ID");
        }

        record StatusUpdateReq(String status) {}
        StatusUpdateReq req = ctx.parseJsonBody(StatusUpdateReq.class);
        if (req == null || req.status() == null || req.status().isBlank()) {
            return HttpResponse.badRequest("status is required");
        }

        ApplicationStatus newStatus = ApplicationStatus.fromString(req.status());
        if (newStatus == null) {
            return HttpResponse.badRequest("Invalid application status: " + req.status());
        }

        if (candidateDao == null) {
            return HttpResponse.badRequest("Candidate DAO not configured");
        }

        boolean updated = candidateDao.updateApplicationStatus(labId, appId, newStatus, auth.getUserId());
        if (!updated) {
            // Rule: "Réponds 404 (pas 403) pour les ressources d'autrui"
            return HttpResponse.notFound("Candidate application not found");
        }

        return HttpResponse.ok(Map.of(
                "id", appId.toString(),
                "status", newStatus.name(),
                "message", "Status updated successfully to " + newStatus.name()
        ));
    }

    private UUID resolveLabId(AuthContext auth, String headerLabId) throws Exception {
        if (headerLabId != null && !headerLabId.isBlank()) {
            try {
                return UUID.fromString(headerLabId.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        if (auth.labId() != null) {
            return auth.labId();
        }
        var memberships = labMemberDao.findAllByUserId(auth.getUserId());
        return memberships.isEmpty() ? null : memberships.get(0).labId();
    }

    private int calculateFitScore(List<String> studentSkills, List<String> offerSkills, String level, String field) {
        if (offerSkills == null || offerSkills.isEmpty()) {
            return 82;
        }
        Set<String> sSkills = new HashSet<>();
        if (studentSkills != null) {
            for (String s : studentSkills) {
                if (s != null) sSkills.add(s.toLowerCase().trim());
            }
        }
        int matched = 0;
        for (String os : offerSkills) {
            if (os != null && sSkills.contains(os.toLowerCase().trim())) {
                matched++;
            }
        }
        double ratio = (double) matched / Math.max(1, offerSkills.size());
        int base = 70 + (int) Math.round(ratio * 24.0);
        if (field != null) {
            String fLower = field.toLowerCase();
            if (fLower.contains("genom") || fLower.contains("bio") || fLower.contains("data") || fLower.contains("stat")) {
                base += 4;
            }
        }
        return Math.max(71, Math.min(96, base));
    }
}
