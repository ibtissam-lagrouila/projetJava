package com.fitwell.model.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OfferDtos {

    private OfferDtos() {}

    public record SkillItem(
            UUID id,
            String name,
            String normalizedName,
            boolean isRequired
    ) {}

    public record GapSkillItem(
            String name,
            boolean isRequired,
            String advice
    ) {}

    public record ScoreBreakdown(
            int overallScore,
            String matchLabel, // "Excellent match", "Strong match", "Good match", "Moderate match"
            int skillsScore,
            int interestScore,
            int datesScore,
            String whyPicked, // "Why Fitwell picked it"
            List<String> matchedSkills,
            List<GapSkillItem> gapToClose
    ) {}

    public record OfferCardResponse(
            UUID id,
            UUID labId,
            String labName,
            String labUniversity,
            String labCity,
            String title,
            String domain,
            int durationMonths,
            LocalDate startDate,
            String location,
            String compensationType,
            String compensationAmount,
            String description,
            List<String> tasks,
            int places,
            String status,
            String origin,
            String sourceName,
            String sourceUrl,
            LocalDate closesAt,
            long daysRemaining,
            List<SkillItem> skills,
            ScoreBreakdown score,
            String displayDate,
            boolean isMayBeClosed
    ) {
        public OfferCardResponse(
                UUID id, UUID labId, String labName, String labUniversity, String labCity,
                String title, String domain, int durationMonths, LocalDate startDate,
                String location, String compensationType, String compensationAmount,
                String description, List<String> tasks, int places, String status,
                String origin, String sourceName, String sourceUrl, LocalDate closesAt,
                long daysRemaining, List<SkillItem> skills, ScoreBreakdown score
        ) {
            this(id, labId, labName, labUniversity, labCity, title, domain, durationMonths,
                 startDate, location, compensationType, compensationAmount, description,
                 tasks, places, status, origin, sourceName, sourceUrl, closesAt,
                 daysRemaining, skills, score, null, false);
        }
    }

    public record QueryPill(
            String label,
            String type // "city", "duration", "domain", "compensation"
    ) {}

    public record ParsedQueryResponse(
            String rawQuery,
            String city,
            Integer minDurationMonths,
            Integer maxDurationMonths,
            String durationLabel,
            List<String> domains,
            List<String> skills,
            String compensation,
            List<QueryPill> pills
    ) {}

    public record DeeplinkSourceItem(
            String id,
            String name,
            String domain,
            String searchUrl,
            String notes
    ) {}

    public record WeeklyActionItem(
            String id,
            String title,
            String deadline,
            String type,
            boolean hasAlertDot,
            String link
    ) {}

    public record StudentHomeResponse(
            String studentName,
            String studentUniversity,
            String studentLevel,
            int applicationsCount,
            int inReviewCount,
            int interviewsCount,
            OfferCardResponse bestMatchToday,
            List<OfferCardResponse> recommendedOffers,
            List<WeeklyActionItem> weeklyActions
    ) {}

    public record ApplyOfferRequest(
            String offerId,
            String coverLetter,
            Boolean isManualExternal,
            Boolean confirmed
    ) {}
}

