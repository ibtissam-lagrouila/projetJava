package com.fitwell.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LabDashboardDtos {

    public record LabDashboardResponse(
            String greeting,
            String accountName,
            String labName,
            String city,
            String role,
            UUID activeLabId,
            String status,
            List<AvailableLabItem> availableLabs,
            DashboardCounters counters,
            List<TopCandidateItem> topCandidates,
            List<SuggestionItem> suggestions,
            ThisMonthActivity thisMonth
    ) {}

    public record AvailableLabItem(
            UUID labId,
            String labName,
            String city,
            String universityEntity,
            String role,
            String status
    ) {}

    public record DashboardCounters(
            int openOffers,
            int newApplicants,
            int toReview,
            int interviewsThisWeek,
            int awaitingConfirmation
    ) {}

    public record TopCandidateItem(
            UUID applicationId,
            UUID studentId,
            String studentName,
            String initials,
            String educationLevel,
            String fieldOfStudy,
            UUID offerId,
            String offerTitle,
            Integer matchPercentage,
            boolean isLimitedData,
            CandidateScoreBreakdown scoreBreakdown,
            Instant appliedAt,
            String status
    ) {}

    public record CandidateScoreBreakdown(
            List<String> matchedSkills,
            List<String> missingSkills,
            int skillsScore,
            int interestScore,
            int datesScore,
            String matchLabel,
            String explanation
    ) {}

    public record SuggestionItem(
            String id,
            String type,
            String title,
            String description,
            String actionLabel,
            String actionType,
            UUID targetOfferId,
            List<UUID> candidateIds,
            List<String> candidateNames,
            String targetUrl
    ) {}

    public record ThisMonthActivity(
            int views,
            int applied,
            int shortlisted,
            int interviews,
            boolean isAllZero,
            int viewsPct,
            int appliedPct,
            int shortlistedPct,
            int interviewsPct
    ) {}

    public record ShortlistCandidatesRequest(
            List<String> applicationIds,
            Boolean confirmed
    ) {}
}
