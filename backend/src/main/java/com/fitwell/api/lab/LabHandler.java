package com.fitwell.api.lab;

import com.fitwell.core.http.HttpResponse;
import com.fitwell.core.http.RequestContext;
import com.fitwell.core.middleware.AuthMiddleware;
import com.fitwell.core.security.AuthContext;
import com.fitwell.dao.LabMemberDao;
import com.fitwell.model.dto.LabDashboardDtos.*;
import com.fitwell.service.LabDashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public final class LabHandler {

    private static final Logger log = LoggerFactory.getLogger(LabHandler.class);

    private final LabDashboardService labDashboardService;
    private final LabMemberDao labMemberDao;

    public LabHandler(LabDashboardService labDashboardService, LabMemberDao labMemberDao) {
        this.labDashboardService = labDashboardService;
        this.labMemberDao = labMemberDao;
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
            // Rule: "Un autre labo reçoit 404"
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

        // Rule: "L'invitation n'est envoyée qu'après confirmation explicite, rien d'automatique."
        if (!Boolean.TRUE.equals(req.confirmed())) {
            return HttpResponse.badRequest("Explicit confirmation is required to shortlist candidates");
        }

        String headerLabId = ctx.getHeader("X-Lab-Id");
        UUID labId = (headerLabId != null && !headerLabId.isBlank())
                ? UUID.fromString(headerLabId.trim())
                : auth.labId();

        if (labId == null) {
            var memberships = labMemberDao.findAllByUserId(auth.getUserId());
            if (!memberships.isEmpty()) {
                labId = memberships.get(0).labId();
            } else {
                return HttpResponse.forbidden("User is not a member of any laboratory");
            }
        }

        // Verify membership
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
}
