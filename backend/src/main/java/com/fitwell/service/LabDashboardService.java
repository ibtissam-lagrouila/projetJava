package com.fitwell.service;

import com.fitwell.core.json.JsonCodec;
import com.fitwell.dao.LabDao;
import com.fitwell.dao.LabDashboardDao;
import com.fitwell.dao.LabDashboardDao.*;
import com.fitwell.dao.LabMemberDao;
import com.fitwell.dao.LabMemberDao.LabMembershipDetails;
import com.fitwell.dao.OfferDao.OfferRecord;
import com.fitwell.dao.StudentProfileDao.StudentFullProfile;
import com.fitwell.model.dto.LabDashboardDtos.*;
import com.fitwell.model.dto.OfferDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

public class LabDashboardService {

    private static final Logger log = LoggerFactory.getLogger(LabDashboardService.class);
    public static final ZoneId ZONE_ID = ZoneId.of("Africa/Casablanca");

    private final LabDashboardDao dashboardDao;
    private final LabMemberDao labMemberDao;
    private final LabDao labDao;
    private final MatchingService matchingService;
    private final Clock clock;

    public LabDashboardService(LabDashboardDao dashboardDao, LabMemberDao labMemberDao, LabDao labDao, MatchingService matchingService) {
        this(dashboardDao, labMemberDao, labDao, matchingService, Clock.system(ZONE_ID));
    }

    public LabDashboardService(LabDashboardDao dashboardDao, LabMemberDao labMemberDao, LabDao labDao,
                               MatchingService matchingService, Clock clock) {
        this.dashboardDao = dashboardDao;
        this.labMemberDao = labMemberDao;
        this.labDao = labDao;
        this.matchingService = matchingService;
        this.clock = clock;
    }

    public LabDashboardResponse getDashboard(UUID userId, String userFullName, UUID requestedLabId) throws SQLException {
        // 1. Verify user's lab memberships
        List<LabMembershipDetails> memberships = labMemberDao.findAllByUserId(userId);
        if (memberships.isEmpty()) {
            throw new IllegalArgumentException("USER_NOT_LAB_MEMBER");
        }

        // 2. Resolve active lab
        LabMembershipDetails activeMembership = null;
        if (requestedLabId != null) {
            for (LabMembershipDetails m : memberships) {
                if (m.labId().equals(requestedLabId)) {
                    activeMembership = m;
                    break;
                }
            }
            if (activeMembership == null) {
                // Security rule: Lab member accessing another lab they don't belong to receives 404
                throw new NoSuchElementException("LAB_NOT_FOUND_FOR_USER");
            }
        } else {
            activeMembership = memberships.get(0);
        }

        UUID labId = activeMembership.labId();
        String labStatus = labDao.getLabStatus(labId);

        List<AvailableLabItem> availableLabs = memberships.stream()
                .map(m -> new AvailableLabItem(
                        m.labId(),
                        m.labName(),
                        m.city(),
                        m.universityEntity(),
                        m.role().name(),
                        m.status()
                ))
                .toList();

        // 3. Lab status check (PENDING or REJECTED)
        if ("PENDING".equalsIgnoreCase(labStatus) || "REJECTED".equalsIgnoreCase(labStatus)) {
            return new LabDashboardResponse(
                    "Welcome, " + sanitizeName(userFullName),
                    sanitizeName(userFullName),
                    activeMembership.labName(),
                    activeMembership.city(),
                    activeMembership.role().name(),
                    labId,
                    labStatus.toUpperCase(),
                    availableLabs,
                    null,
                    List.of(),
                    List.of(),
                    null
            );
        }

        // 4. Time calculations with Africa/Casablanca clock
        ZonedDateTime zdt = clock.instant().atZone(ZONE_ID);
        LocalDate today = zdt.toLocalDate();
        Instant now = zdt.toInstant();

        // Greeting according to local hour (05:00-11:59 Morning, 12:00-17:59 Afternoon, 18:00-04:59 Evening)
        int hour = zdt.getHour();
        String greetingPrefix;
        if (hour >= 5 && hour < 12) {
            greetingPrefix = "Good morning";
        } else if (hour >= 12 && hour < 18) {
            greetingPrefix = "Good afternoon";
        } else {
            greetingPrefix = "Good evening";
        }
        String displayName = sanitizeName(userFullName);
        String greeting = greetingPrefix + ", " + displayName;

        // Week boundaries: Monday 00:00 to Sunday 23:59:59 in Africa/Casablanca
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        Instant weekStart = monday.atStartOfDay(ZONE_ID).toInstant();
        Instant weekEnd = sunday.plusDays(1).atStartOfDay(ZONE_ID).toInstant();

        // Month boundaries: 1st of month 00:00 to end of month in Africa/Casablanca
        LocalDate firstDayOfMonth = today.withDayOfMonth(1);
        LocalDate lastDayOfMonth = today.with(TemporalAdjusters.lastDayOfMonth());
        Instant monthStart = firstDayOfMonth.atStartOfDay(ZONE_ID).toInstant();
        Instant monthEnd = lastDayOfMonth.plusDays(1).atStartOfDay(ZONE_ID).toInstant();

        // 5. Counters
        Instant sevenDaysAgo = now.minus(Duration.ofDays(7));
        int openOffers = dashboardDao.countOpenOffers(labId, today);
        int newApplicants = dashboardDao.countNewApplicants(labId, sevenDaysAgo);
        int toReview = dashboardDao.countToReview(labId);
        int interviewsThisWeek = dashboardDao.countInterviewsBetween(labId, weekStart, weekEnd, "CONFIRMED");
        int awaitingConfirmation = dashboardDao.countAwaitingConfirmationInterviews(labId);

        DashboardCounters counters = new DashboardCounters(
                openOffers,
                newApplicants,
                toReview,
                interviewsThisWeek,
                awaitingConfirmation
        );

        // 6. Top Candidates to Review (Top 4 undecided across lab's open offers)
        List<RawApplicationCandidate> undecidedApps = dashboardDao.getUndecidedApplications(labId, today);
        List<ScoredCandidate> scoredCandidates = new ArrayList<>();

        for (RawApplicationCandidate app : undecidedApps) {
            StudentFullProfile profile = resolveStudentProfile(app);
            OfferRecord offer = createLightOffer(app);

            boolean isLimitedData = (offer.skills() == null || offer.skills().isEmpty())
                    || (profile == null || profile.skills() == null || profile.skills().size() < 2);

            ScoreBreakdown breakdown = matchingService.computeScore(profile, offer);
            Integer matchPct = isLimitedData ? null : breakdown.overallScore();

            scoredCandidates.add(new ScoredCandidate(app, profile, offer, breakdown, matchPct, isLimitedData));
        }

        // Rank by match percentage descending (limited data ranked last or nulls last)
        scoredCandidates.sort((a, b) -> {
            if (a.matchPercentage == null && b.matchPercentage == null) return 0;
            if (a.matchPercentage == null) return 1;
            if (b.matchPercentage == null) return -1;
            int cmp = Integer.compare(b.matchPercentage, a.matchPercentage);
            if (cmp != 0) return cmp;
            return b.raw.appliedAt().compareTo(a.raw.appliedAt());
        });

        List<TopCandidateItem> topCandidates = scoredCandidates.stream()
                .limit(4)
                .map(sc -> {
                    RawApplicationCandidate r = sc.raw;
                    List<String> missingSkills = sc.breakdown.gapToClose() != null
                            ? sc.breakdown.gapToClose().stream().map(GapSkillItem::name).toList()
                            : List.of();

                    CandidateScoreBreakdown scoreBreakdown = new CandidateScoreBreakdown(
                            sc.breakdown.matchedSkills(),
                            missingSkills,
                            sc.breakdown.skillsScore(),
                            sc.breakdown.interestScore(),
                            sc.breakdown.datesScore(),
                            sc.breakdown.matchLabel(),
                            sc.breakdown.whyPicked()
                    );

                    return new TopCandidateItem(
                            r.applicationId(),
                            r.studentId(),
                            r.studentName(),
                            getInitials(r.studentName()),
                            r.level(),
                            r.fieldOfStudy(),
                            r.offerId(),
                            r.offerTitle(),
                            sc.matchPercentage,
                            sc.isLimitedData,
                            scoreBreakdown,
                            r.appliedAt(),
                            r.status()
                    );
                })
                .toList();

        // 7. Fitwell Suggests Rules
        List<SuggestionItem> suggestions = computeSuggestions(labId, undecidedApps, scoredCandidates, now);

        // 8. This Month Activity
        MonthStats ms = dashboardDao.getMonthActivity(labId, monthStart, monthEnd);
        if (ms == null) {
            ms = new MonthStats(0, 0, 0, 0);
        }
        boolean isAllZero = (ms.views() == 0 && ms.applied() == 0 && ms.shortlisted() == 0 && ms.interviews() == 0);
        int maxVal = Math.max(1, Math.max(ms.views(), Math.max(ms.applied(), Math.max(ms.shortlisted(), ms.interviews()))));
        int viewsPct = isAllZero ? 0 : (int) Math.round(((double) ms.views() / maxVal) * 100);
        int appliedPct = isAllZero ? 0 : (int) Math.round(((double) ms.applied() / maxVal) * 100);
        int shortlistedPct = isAllZero ? 0 : (int) Math.round(((double) ms.shortlisted() / maxVal) * 100);
        int interviewsPct = isAllZero ? 0 : (int) Math.round(((double) ms.interviews() / maxVal) * 100);

        ThisMonthActivity thisMonth = new ThisMonthActivity(
                ms.views(),
                ms.applied(),
                ms.shortlisted(),
                ms.interviews(),
                isAllZero,
                viewsPct,
                appliedPct,
                shortlistedPct,
                interviewsPct
        );

        return new LabDashboardResponse(
                greeting,
                displayName,
                activeMembership.labName(),
                activeMembership.city(),
                activeMembership.role().name(),
                labId,
                "APPROVED",
                availableLabs,
                counters,
                topCandidates,
                suggestions,
                thisMonth
        );
    }

    private record ScoredCandidate(
            RawApplicationCandidate raw,
            StudentFullProfile profile,
            OfferRecord offer,
            ScoreBreakdown breakdown,
            Integer matchPercentage,
            boolean isLimitedData
    ) {}

    private List<SuggestionItem> computeSuggestions(UUID labId, List<RawApplicationCandidate> undecidedApps,
                                                   List<ScoredCandidate> scoredCandidates, Instant now) throws SQLException {
        List<SuggestionItem> suggestions = new ArrayList<>();

        // Rule 1: "Shortlist N candidates" — At least 3 NEW (status = SENT) candidates with score >= 80 on the same offer
        Map<UUID, List<ScoredCandidate>> candidatesByOffer = scoredCandidates.stream()
                .filter(c -> "SENT".equals(c.raw.status()) && c.matchPercentage != null && c.matchPercentage >= 80)
                .collect(Collectors.groupingBy(c -> c.raw.offerId()));

        for (Map.Entry<UUID, List<ScoredCandidate>> entry : candidatesByOffer.entrySet()) {
            List<ScoredCandidate> group = entry.getValue();
            if (group.size() >= 3) {
                UUID offerId = entry.getKey();
                String offerTitle = group.get(0).raw.offerTitle();
                List<String> names = group.stream().map(c -> c.raw.studentName().split(" ")[0]).toList();
                String formattedNames = formatNames(names);

                suggestions.add(new SuggestionItem(
                        "shortlist-" + offerId,
                        "SHORTLIST",
                        "Shortlist " + group.size() + " candidates",
                        formattedNames + " fit your " + offerTitle.toLowerCase() + " offer best. I can invite them in one step.",
                        "Review",
                        "MODAL_SHORTLIST",
                        offerId,
                        group.stream().map(c -> c.raw.applicationId()).toList(),
                        group.stream().map(c -> c.raw.studentName()).toList(),
                        "/lab/candidates?offerId=" + offerId + "&status=SENT"
                ));
                break; // One shortlist suggestion at a time
            }
        }

        // Rule 2: "N applicants are waiting" — applications in status SENT for > 5 days
        Instant fiveDaysAgo = now.minus(Duration.ofDays(5));
        int waitingCount = dashboardDao.countWaitingApplicantsOver5Days(labId, fiveDaysAgo);
        if (waitingCount > 0) {
            String title = waitingCount + (waitingCount > 1 ? " applicants are waiting" : " applicant is waiting");
            suggestions.add(new SuggestionItem(
                    "waiting-applicants",
                    "WAITING",
                    title,
                    "They applied over five days ago. A short reply keeps good candidates engaged.",
                    "Review applicants",
                    "NAVIGATE_CANDIDATES",
                    null,
                    List.of(),
                    List.of(),
                    "/lab/candidates?status=SENT"
            ));
        }

        // Rule 3: "Offer views are low" — offer published for >= 7 days with views < 0.5 * median of other offers
        // (or < 20 if only 1 offer), checking missing fields (start date, compensation, skills)
        List<LabOfferStats> offerStats = dashboardDao.getLabOfferStats(labId);
        Instant sevenDaysAgo = now.minus(Duration.ofDays(7));

        List<LabOfferStats> matureOffers = offerStats.stream()
                .filter(o -> o.createdAt().isBefore(sevenDaysAgo))
                .toList();

        if (!matureOffers.isEmpty()) {
            if (offerStats.size() > 1) {
                List<Integer> allViews = offerStats.stream().map(LabOfferStats::totalViews).sorted().toList();
                double medianViews = computeMedian(allViews);
                double threshold = 0.5 * medianViews;

                for (LabOfferStats o : matureOffers) {
                    if (o.totalViews() < threshold) {
                        String missingText = findMissingFieldFactualText(o);
                        if (missingText != null) {
                            suggestions.add(new SuggestionItem(
                                    "low-views-" + o.offerId(),
                                    "LOW_VIEWS",
                                    "Offer views are low",
                                    "Your " + o.title().toLowerCase() + " offer " + missingText,
                                    "Edit offer",
                                    "NAVIGATE_OFFER",
                                    o.offerId(),
                                    List.of(),
                                    List.of(),
                                    "/lab/post-offer?edit=" + o.offerId()
                            ));
                            break;
                        }
                    }
                }
            } else if (offerStats.size() == 1) {
                LabOfferStats singleOffer = matureOffers.get(0);
                if (singleOffer.totalViews() < 20) {
                    String missingText = findMissingFieldFactualText(singleOffer);
                    if (missingText != null) {
                        suggestions.add(new SuggestionItem(
                                "low-views-" + singleOffer.offerId(),
                                "LOW_VIEWS",
                                "Offer views are low",
                                "Your " + singleOffer.title().toLowerCase() + " offer " + missingText,
                                "Edit offer",
                                "NAVIGATE_OFFER",
                                singleOffer.offerId(),
                                List.of(),
                                List.of(),
                                "/lab/post-offer?edit=" + singleOffer.offerId()
                        ));
                    }
                }
            }
        }

        return suggestions;
    }

    private String findMissingFieldFactualText(LabOfferStats o) {
        List<String> missing = new ArrayList<>();
        if (o.startDate() == null) missing.add("has no start date");
        if (o.compensation() == null || o.compensation().isBlank()) missing.add("has no compensation details");
        if (o.skillCount() == 0) missing.add("has no required skills listed");

        if (missing.isEmpty()) return null;
        return String.join(" and ", missing) + ".";
    }

    private double computeMedian(List<Integer> sortedList) {
        if (sortedList.isEmpty()) return 0;
        int n = sortedList.size();
        if (n % 2 == 1) {
            return sortedList.get(n / 2);
        } else {
            return (sortedList.get(n / 2 - 1) + sortedList.get(n / 2)) / 2.0;
        }
    }

    private String formatNames(List<String> names) {
        if (names.isEmpty()) return "";
        if (names.size() == 1) return names.get(0);
        if (names.size() == 2) return names.get(0) + " and " + names.get(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i == names.size() - 1) {
                sb.append(" and ").append(names.get(i));
            } else if (i == 0) {
                sb.append(names.get(i));
            } else {
                sb.append(", ").append(names.get(i));
            }
        }
        return sb.toString();
    }

    private StudentFullProfile resolveStudentProfile(RawApplicationCandidate app) {
        if (app.profileSnapshotJson() != null && !app.profileSnapshotJson().isBlank()) {
            try {
                return JsonCodec.fromJson(app.profileSnapshotJson(), StudentFullProfile.class);
            } catch (Exception e) {
                log.warn("Failed to parse profile_snapshot_json for application {}: {}", app.applicationId(), e.getMessage());
            }
        }
        return new StudentFullProfile(
                app.studentId(),
                null,
                app.studentName(),
                app.university(),
                app.level(),
                List.of(),
                List.of(app.fieldOfStudy()),
                LocalDate.now(),
                6,
                null,
                true,
                List.of(),
                List.of()
        );
    }

    private OfferRecord createLightOffer(RawApplicationCandidate app) {
        return new OfferRecord(
                app.offerId(),
                null,
                null,
                null,
                app.offerLocation(),
                app.offerTitle(),
                app.offerDomain(),
                app.offerDurationMonths(),
                app.offerStartDate(),
                app.offerLocation(),
                "Funded",
                "Gratification standard",
                "",
                List.of(),
                1,
                "PUBLISHED",
                "LAB_POSTED",
                null,
                null,
                null,
                30,
                app.offerSkills() != null ? app.offerSkills() : List.of(),
                "Active",
                Instant.now()
        );
    }

    public int shortlistCandidates(UUID labId, List<UUID> applicationIds, UUID actorUserId) throws SQLException {
        return dashboardDao.shortlistApplications(labId, applicationIds, actorUserId);
    }

    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) return "Recruiter";
        // Do not invent titles like "Dr." - use exact account name
        return name.trim();
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "ST";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }
}
