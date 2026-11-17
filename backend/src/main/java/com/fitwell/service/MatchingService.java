package com.fitwell.service;

import com.fitwell.dao.OfferDao.OfferRecord;
import com.fitwell.dao.StudentProfileDao.StudentFullProfile;
import com.fitwell.dao.StudentProfileDao.StudentProjectRecord;
import com.fitwell.model.dto.OfferDtos.*;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class MatchingService {

    public static final double WEIGHT_REQUIRED = 3.0;
    public static final double WEIGHT_OPTIONAL = 1.0;

    private static final Pattern WORD_SPLIT = Pattern.compile("[\\s,\\-_/]+");

    public ScoreBreakdown computeScore(StudentFullProfile student, OfferRecord offer) {
        if (student == null) {
            return computeUnauthenticatedScore(offer);
        }

        // 1. Calculate Owned Skills
        Set<String> ownedSkills = extractAllOwnedSkills(student);

        // 2. Skills Coverage
        List<SkillItem> offerSkills = offer.skills() != null ? offer.skills() : List.of();
        List<String> matchedSkills = new ArrayList<>();
        List<GapSkillItem> gapToClose = new ArrayList<>();

        double earnedWeight = 0.0;
        double totalWeight = 0.0;

        for (SkillItem skill : offerSkills) {
            String norm = normalize(skill.normalizedName() != null ? skill.normalizedName() : skill.name());
            double weight = skill.isRequired() ? WEIGHT_REQUIRED : WEIGHT_OPTIONAL;
            totalWeight += weight;

            if (isSkillOwned(norm, ownedSkills)) {
                earnedWeight += weight;
                matchedSkills.add(skill.name());
            } else {
                gapToClose.add(new GapSkillItem(
                        skill.name(),
                        skill.isRequired(),
                        generateGapAdvice(skill, offer)
                ));
            }
        }

        int skillsScore;
        if (totalWeight <= 0.0) {
            skillsScore = 100;
        } else {
            double coverage = earnedWeight / totalWeight;
            skillsScore = (int) Math.round(coverage * 100.0);
            skillsScore = Math.min(100, Math.max(0, skillsScore));
        }

        // 3. Interest & Domain Match
        int interestScore = computeInterestScore(student, offer);

        // 4. Dates & Availability Match
        int datesScore = computeDatesScore(student, offer);

        // 5. Overall Weighted Composite Score: 50% Skills + 30% Interest + 20% Dates
        int overallScore = (int) Math.round(0.50 * skillsScore + 0.30 * interestScore + 0.20 * datesScore);
        overallScore = Math.min(100, Math.max(0, overallScore));

        // 6. Match Label
        String matchLabel = getMatchLabel(overallScore);

        // 7. Deterministic "Why Fitwell picked it" explanation
        String whyPicked = generateWhyPickedExplanation(student, offer, matchedSkills, gapToClose);

        return new ScoreBreakdown(
                overallScore,
                matchLabel,
                skillsScore,
                interestScore,
                datesScore,
                whyPicked,
                matchedSkills,
                gapToClose
        );
    }

    public ScoreBreakdown computeScore(StudentFullProfile student, OfferRecord offer, ParsedQueryResponse query) {
        ScoreBreakdown baseScore = computeScore(student, offer);
        if (query == null || (query.rawQuery() != null && query.rawQuery().isBlank())) {
            return baseScore;
        }

        // 1. City Relevance
        int cityRelevance = 70;
        if (query.city() != null && !query.city().isBlank()) {
            String loc = (offer.location() != null ? offer.location() : "").toLowerCase(Locale.ROOT);
            if (loc.contains(query.city().toLowerCase(Locale.ROOT))) {
                cityRelevance = 100;
            } else {
                cityRelevance = 35;
            }
        }

        // 2. Domain / Keyword Relevance
        int domainRelevance = 70;
        String offerAllText = (
                (offer.domain() != null ? offer.domain() : "") + " " +
                (offer.title() != null ? offer.title() : "") + " " +
                (offer.description() != null ? offer.description() : "")
        ).toLowerCase(Locale.ROOT);

        if (query.domains() != null && !query.domains().isEmpty()) {
            boolean hasDomainHit = false;
            for (String qd : query.domains()) {
                String normQd = qd.toLowerCase(Locale.ROOT);
                if (offerAllText.contains(normQd)) {
                    hasDomainHit = true;
                    break;
                }
                if (normQd.contains("machine learning") || normQd.contains("ai") || normQd.contains("ia")) {
                    if (offerAllText.contains("intelligence artificielle") || offerAllText.contains("ia") ||
                        offerAllText.contains("machine learning") || offerAllText.contains("deep learning") ||
                        offerAllText.contains("computer vision") || offerAllText.contains("vision") ||
                        offerAllText.contains("data science") || offerAllText.contains("data analysis") ||
                        offerAllText.contains("données")) {
                        hasDomainHit = true;
                        break;
                    }
                }
            }
            domainRelevance = hasDomainHit ? 100 : 40;
        }

        // 3. Duration Relevance
        int durationRelevance = 80;
        if (query.minDurationMonths() != null && query.maxDurationMonths() != null) {
            int d = offer.durationMonths();
            if (d >= query.minDurationMonths() && d <= query.maxDurationMonths()) {
                durationRelevance = 100;
            } else if (Math.abs(d - query.minDurationMonths()) <= 1 || Math.abs(d - query.maxDurationMonths()) <= 1) {
                durationRelevance = 85;
            } else {
                durationRelevance = 50;
            }
        }

        // 4. Compensation Relevance
        int fundingRelevance = 90;
        if ("Funded".equalsIgnoreCase(query.compensation())) {
            String comp = (offer.compensationType() != null ? offer.compensationType() : "").toLowerCase(Locale.ROOT);
            fundingRelevance = (comp.contains("fund") || comp.contains("remun") || comp.contains("paid")) ? 100 : 50;
        }

        // Weighted query relevance
        double queryFactor = (0.35 * domainRelevance + 0.35 * cityRelevance + 0.15 * durationRelevance + 0.15 * fundingRelevance);

        // Adaptive composite score: 50% profile fit + 50% query relevance
        int adaptedScore = (int) Math.round(0.50 * baseScore.overallScore() + 0.50 * queryFactor);

        // If an explicit city was requested, penalize offers in different cities
        if (query.city() != null && !query.city().isBlank()) {
            String loc = (offer.location() != null ? offer.location() : "").toLowerCase(Locale.ROOT);
            if (!loc.contains(query.city().toLowerCase(Locale.ROOT))) {
                adaptedScore = Math.min(55, (int) Math.round(adaptedScore * 0.65));
            }
        }

        adaptedScore = Math.min(99, Math.max(20, adaptedScore));

        String matchLabel = getMatchLabel(adaptedScore);

        return new ScoreBreakdown(
                adaptedScore,
                matchLabel,
                baseScore.skillsScore(),
                baseScore.interestScore(),
                baseScore.datesScore(),
                baseScore.whyPicked(),
                baseScore.matchedSkills(),
                baseScore.gapToClose()
        );
    }

    private ScoreBreakdown computeUnauthenticatedScore(OfferRecord offer) {
        return new ScoreBreakdown(
                0,
                "Sign in to calculate match",
                0,
                0,
                0,
                "Sign in with your student profile to view compatibility and custom recommendations.",
                List.of(),
                offer.skills().stream()
                        .map(s -> new GapSkillItem(s.name(), s.isRequired(), generateGapAdvice(s, offer)))
                        .collect(Collectors.toList())
        );
    }

    private Set<String> extractAllOwnedSkills(StudentFullProfile student) {
        Set<String> set = new HashSet<>();
        if (student.skills() != null) {
            for (String s : student.skills()) {
                if (s != null && !s.isBlank()) {
                    set.add(normalize(s));
                }
            }
        }
        if (student.projects() != null) {
            for (StudentProjectRecord p : student.projects()) {
                if (p.skills() != null) {
                    for (String s : p.skills()) {
                        if (s != null && !s.isBlank()) {
                            set.add(normalize(s));
                        }
                    }
                }
            }
        }
        return set;
    }

    private boolean isSkillOwned(String normalizedSkill, Set<String> ownedSkills) {
        if (ownedSkills.contains(normalizedSkill)) {
            return true;
        }
        if (("ml".equals(normalizedSkill) && (ownedSkills.contains("machinelearning") || ownedSkills.contains("ml"))) ||
            ("machinelearning".equals(normalizedSkill) && (ownedSkills.contains("ml") || ownedSkills.contains("machinelearning")))) {
            return true;
        }
        if (("ia".equals(normalizedSkill) || "ai".equals(normalizedSkill)) &&
            (ownedSkills.contains("machinelearning") || ownedSkills.contains("artificialintelligence") || ownedSkills.contains("ia") || ownedSkills.contains("intelligenceartificielle"))) {
            return true;
        }
        if (("artificialintelligence".equals(normalizedSkill) || "intelligenceartificielle".equals(normalizedSkill)) &&
            (ownedSkills.contains("ia") || ownedSkills.contains("machinelearning") || ownedSkills.contains("ai"))) {
            return true;
        }
        if (("analysededonnees".equals(normalizedSkill) || "dataanalysis".equals(normalizedSkill)) &&
            (ownedSkills.contains("machinelearning") || ownedSkills.contains("ml") || ownedSkills.contains("python") || ownedSkills.contains("datascience"))) {
            return true;
        }
        if (("computervision".equals(normalizedSkill) && (ownedSkills.contains("opencv") || ownedSkills.contains("computervision"))) ||
            ("opencv".equals(normalizedSkill) && ownedSkills.contains("computervision"))) {
            return true;
        }
        // Substring / token matching (e.g., "pytorch" inside "pytorch segmentation")
        for (String owned : ownedSkills) {
            if (owned.length() >= 4 && (owned.contains(normalizedSkill) || normalizedSkill.contains(owned))) {
                return true;
            }
        }
        return false;
    }

    private int computeInterestScore(StudentFullProfile student, OfferRecord offer) {
        List<String> interests = student.interests() != null ? student.interests() : List.of();
        if (interests.isEmpty()) {
            return 50; // Neutral baseline when no interests declared
        }

        String offerTargetText = (
                (offer.domain() != null ? offer.domain() : "") + " " +
                (offer.title() != null ? offer.title() : "") + " " +
                (offer.description() != null ? offer.description() : "")
        ).toLowerCase(Locale.ROOT);

        int bestInterestMatch = 20;

        for (String interest : interests) {
            if (interest == null || interest.isBlank()) continue;
            String normInterest = interest.trim().toLowerCase(Locale.ROOT);

            // Exact phrase hit in domain or title
            if (offerTargetText.contains(normInterest)) {
                bestInterestMatch = Math.max(bestInterestMatch, 95);
            } else {
                // Token overlap
                String[] tokens = WORD_SPLIT.split(normInterest);
                int matches = 0;
                for (String t : tokens) {
                    if (t.length() > 2 && offerTargetText.contains(t)) {
                        matches++;
                    }
                }
                if (tokens.length > 0 && matches > 0) {
                    int pct = (int) Math.round(((double) matches / tokens.length) * 80.0);
                    bestInterestMatch = Math.max(bestInterestMatch, Math.min(85, pct));
                }
            }
        }

        return Math.min(100, Math.max(0, bestInterestMatch));
    }

    private int computeDatesScore(StudentFullProfile student, OfferRecord offer) {
        int startScore = 100;
        if (student.availabilityStart() != null && offer.startDate() != null) {
            long diffDays = Math.abs(ChronoUnit.DAYS.between(student.availabilityStart(), offer.startDate()));
            if (diffDays <= 30) {
                startScore = 100;
            } else if (diffDays <= 60) {
                startScore = 80;
            } else if (diffDays <= 90) {
                startScore = 60;
            } else {
                startScore = 30;
            }
        } else if (student.availabilityStart() == null || offer.startDate() == null) {
            startScore = 90; // Flexible
        }

        int durationScore = 100;
        int studentDuration = student.availabilityDurationMonths() > 0 ? student.availabilityDurationMonths() : 4;
        int offerDuration = offer.durationMonths() > 0 ? offer.durationMonths() : 4;

        if (studentDuration >= offerDuration) {
            durationScore = 100;
        } else if (studentDuration == offerDuration - 1) {
            durationScore = 75;
        } else {
            durationScore = 50;
        }

        return (int) Math.round(0.60 * startScore + 0.40 * durationScore);
    }

    private String getMatchLabel(int score) {
        if (score >= 90) return "Excellent match";
        if (score >= 75) return "Strong match";
        if (score >= 60) return "Good match";
        return "Moderate match";
    }

    private String generateGapAdvice(SkillItem skill, OfferRecord offer) {
        String norm = normalize(skill.normalizedName() != null ? skill.normalizedName() : skill.name());
        if ("dicom".equals(norm)) {
            return "Familiarize yourself with DICOM viewer libraries (pydicom, SimpleITK) before interview.";
        }
        if ("nextflow".equals(norm)) {
            return "Core workflow pipeline tool required by this lab for bioinformatics execution.";
        }
        if ("deseq2".equals(norm) || "bioconductor".equals(norm)) {
            return "Statistical package used for RNA-seq differential expression in this unit.";
        }
        if (skill.isRequired()) {
            return "Core skill required by " + (offer.labName() != null ? offer.labName() : "the laboratory") + ".";
        }
        return "Recommended addition to strengthen your profile for this research position.";
    }

    private String generateWhyPickedExplanation(StudentFullProfile student, OfferRecord offer,
                                                List<String> matchedSkills, List<GapSkillItem> gapToClose) {
        // Look for student projects covering tasks
        StudentProjectRecord relevantProject = null;
        if (student.projects() != null) {
            for (StudentProjectRecord p : student.projects()) {
                if (p.skills() != null) {
                    for (String s : p.skills()) {
                        String norm = normalize(s);
                        for (String m : matchedSkills) {
                            if (normalize(m).equals(norm)) {
                                relevantProject = p;
                                break;
                            }
                        }
                        if (relevantProject != null) break;
                    }
                }
                if (relevantProject != null) break;
            }
        }

        if (relevantProject != null && !matchedSkills.isEmpty()) {
            String primarySkill = matchedSkills.get(0);
            String secondarySkill = matchedSkills.size() > 1 ? matchedSkills.get(1) : null;

            if (secondarySkill != null) {
                return "your " + primarySkill + " " + relevantProject.title().toLowerCase(Locale.ROOT) +
                        " project covers the main task, and you already use " + secondarySkill + ".";
            } else {
                return "your " + relevantProject.title() + " project directly demonstrates the " + primarySkill +
                        " capabilities demanded by this laboratory.";
            }
        }

        if (!matchedSkills.isEmpty()) {
            return "you cover " + matchedSkills.size() + " key skills including " +
                    String.join(", ", matchedSkills.subList(0, Math.min(3, matchedSkills.size()))) +
                    ", well aligned with your research interests.";
        }

        if (!gapToClose.isEmpty()) {
            return "matches your preferred location (" + offer.location() +
                    ") and duration, with room to develop " + gapToClose.get(0).name() + ".";
        }

        return "matches your academic schedule and duration preferences.";
    }

    private String normalize(String s) {
        if (s == null) return "";
        String norm = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        norm = norm.replaceAll("\\p{M}", "");
        return norm.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}

