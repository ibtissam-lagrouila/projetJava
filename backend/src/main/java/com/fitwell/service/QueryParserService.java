package com.fitwell.service;

import com.fitwell.model.dto.OfferDtos.ParsedQueryResponse;
import com.fitwell.model.dto.OfferDtos.QueryPill;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QueryParserService {

    // Moroccan cities recognition map
    private static final Map<String, String> CITY_MAP = new LinkedHashMap<>();
    static {
        CITY_MAP.put("rabat", "Rabat");
        CITY_MAP.put("casablanca", "Casablanca");
        CITY_MAP.put("casa", "Casablanca");
        CITY_MAP.put("fès", "Fès");
        CITY_MAP.put("fes", "Fès");
        CITY_MAP.put("fez", "Fès");
        CITY_MAP.put("marrakech", "Marrakech");
        CITY_MAP.put("marrakesh", "Marrakech");
        CITY_MAP.put("tangier", "Tangier");
        CITY_MAP.put("tanger", "Tangier");
        CITY_MAP.put("oujda", "Oujda");
        CITY_MAP.put("agadir", "Agadir");
        CITY_MAP.put("tétouan", "Tétouan");
        CITY_MAP.put("tetouan", "Tétouan");
        CITY_MAP.put("kenitra", "Kenitra");
        CITY_MAP.put("kénitra", "Kenitra");
        CITY_MAP.put("meknès", "Meknès");
        CITY_MAP.put("meknes", "Meknès");
    }

    // Number word mapping
    private static final Map<String, Integer> NUMBER_WORDS = new HashMap<>();
    static {
        NUMBER_WORDS.put("one", 1); NUMBER_WORDS.put("un", 1); NUMBER_WORDS.put("une", 1);
        NUMBER_WORDS.put("two", 2); NUMBER_WORDS.put("deux", 2);
        NUMBER_WORDS.put("three", 3); NUMBER_WORDS.put("trois", 3);
        NUMBER_WORDS.put("four", 4); NUMBER_WORDS.put("quatre", 4);
        NUMBER_WORDS.put("five", 5); NUMBER_WORDS.put("cinq", 5);
        NUMBER_WORDS.put("six", 6);
        NUMBER_WORDS.put("seven", 7); NUMBER_WORDS.put("sept", 7);
        NUMBER_WORDS.put("eight", 8); NUMBER_WORDS.put("huit", 8);
        NUMBER_WORDS.put("nine", 9); NUMBER_WORDS.put("neuf", 9);
        NUMBER_WORDS.put("ten", 10); NUMBER_WORDS.put("dix", 10);
        NUMBER_WORDS.put("eleven", 11); NUMBER_WORDS.put("onze", 11);
        NUMBER_WORDS.put("twelve", 12); NUMBER_WORDS.put("douze", 12);
    }

    // Domain keywords
    private static final Map<String, String> DOMAIN_PATTERNS = new LinkedHashMap<>();
    static {
        DOMAIN_PATTERNS.put("intelligence artificielle", "Machine Learning");
        DOMAIN_PATTERNS.put("ia", "Machine Learning");
        DOMAIN_PATTERNS.put("ai", "Machine Learning");
        DOMAIN_PATTERNS.put("machine learning", "Machine Learning");
        DOMAIN_PATTERNS.put("deep learning", "Machine Learning");
        DOMAIN_PATTERNS.put("ml", "Machine Learning");
        DOMAIN_PATTERNS.put("computer vision", "Computer Vision");
        DOMAIN_PATTERNS.put("vision par ordinateur", "Computer Vision");
        DOMAIN_PATTERNS.put("vision", "Computer Vision");
        DOMAIN_PATTERNS.put("data analysis", "Data Analysis");
        DOMAIN_PATTERNS.put("analyse de donn[ée]es", "Data Analysis");
        DOMAIN_PATTERNS.put("analyse des donn[ée]es", "Data Analysis");
        DOMAIN_PATTERNS.put("data science", "Data Science");
        DOMAIN_PATTERNS.put("science des donn[ée]es", "Data Science");
        DOMAIN_PATTERNS.put("medical image", "Medical imaging");
        DOMAIN_PATTERNS.put("medical images", "Medical imaging");
        DOMAIN_PATTERNS.put("medical imaging", "Medical imaging");
        DOMAIN_PATTERNS.put("imagerie m[ée]dicale", "Medical imaging");
        DOMAIN_PATTERNS.put("d[ée]veloppement web", "Web Development");
        DOMAIN_PATTERNS.put("stage web", "Web Development");
        DOMAIN_PATTERNS.put("fullstack", "Web Development");
        DOMAIN_PATTERNS.put("full-stack", "Web Development");
        DOMAIN_PATTERNS.put("react", "Web Development");
        DOMAIN_PATTERNS.put("laravel", "Web Development");
        DOMAIN_PATTERNS.put("web", "Web Development");
        DOMAIN_PATTERNS.put("robotique", "Robotics");
        DOMAIN_PATTERNS.put("robotics", "Robotics");
        DOMAIN_PATTERNS.put("drone", "Robotics");
        DOMAIN_PATTERNS.put("mat[ée]riaux", "Materials Science");
        DOMAIN_PATTERNS.put("materials", "Materials Science");
        DOMAIN_PATTERNS.put("solar", "Materials Science");
        DOMAIN_PATTERNS.put("solaire", "Materials Science");
        DOMAIN_PATTERNS.put("climat", "Climate & Earth Science");
        DOMAIN_PATTERNS.put("climate", "Climate & Earth Science");
        DOMAIN_PATTERNS.put("erosion", "Climate & Earth Science");
        DOMAIN_PATTERNS.put("[ée]rosion", "Climate & Earth Science");
        DOMAIN_PATTERNS.put("ocean", "Climate & Earth Science");
        DOMAIN_PATTERNS.put("mobile", "Mobile Development");
        DOMAIN_PATTERNS.put("flutter", "Mobile Development");
        DOMAIN_PATTERNS.put("react native", "Mobile Development");
        DOMAIN_PATTERNS.put("devops", "DevOps & Cloud");
        DOMAIN_PATTERNS.put("cloud", "DevOps & Cloud");
        DOMAIN_PATTERNS.put("docker", "DevOps & Cloud");
        DOMAIN_PATTERNS.put("aws", "DevOps & Cloud");
        DOMAIN_PATTERNS.put("business intelligence", "Business Intelligence");
        DOMAIN_PATTERNS.put("power bi", "Business Intelligence");
        DOMAIN_PATTERNS.put("bi", "Business Intelligence");
        DOMAIN_PATTERNS.put("s[ée]curit[ée]", "Cyber Security");
        DOMAIN_PATTERNS.put("security", "Cyber Security");
        DOMAIN_PATTERNS.put("cyber", "Cyber Security");
        DOMAIN_PATTERNS.put("bioinformatique", "Bioinformatics");
        DOMAIN_PATTERNS.put("bioinformatics", "Bioinformatics");
        DOMAIN_PATTERNS.put("g[ée]nomique", "Genomics");
        DOMAIN_PATTERNS.put("genomics", "Genomics");
    }

    public ParsedQueryResponse parse(String query) {
        if (query == null || query.isBlank()) {
            return new ParsedQueryResponse(
                    "", null, null, null, null,
                    List.of(), List.of(), "Any funding",
                    List.of(new QueryPill("Any funding", "compensation"))
            );
        }

        String lower = query.toLowerCase(Locale.ROOT);

        // 1. Extract Moroccan City
        String detectedCity = null;
        for (Map.Entry<String, String> entry : CITY_MAP.entrySet()) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
            if (p.matcher(lower).find()) {
                detectedCity = entry.getValue();
                break;
            }
        }

        // 2. Extract Duration Range
        Integer minDuration = null;
        Integer maxDuration = null;
        String durationLabel = null;
        boolean isFrench = lower.contains("mois") || lower.contains("cherche") || lower.contains("stage") || lower.contains("pendant");

        // Pattern: "3 to 5 months" / "3 à 5 mois"
        Pattern rangePattern = Pattern.compile("(\\d+|[a-z]+)\\s*(?:to|-|à|au|a)\\s*(\\d+|[a-z]+)\\s*(?:months?|mois)", Pattern.CASE_INSENSITIVE);
        Matcher rangeMatcher = rangePattern.matcher(lower);
        if (rangeMatcher.find()) {
            Integer min = parseNumberOrWord(rangeMatcher.group(1));
            Integer max = parseNumberOrWord(rangeMatcher.group(2));
            if (min != null && max != null) {
                minDuration = min;
                maxDuration = max;
                durationLabel = min + " to " + max + " months";
            }
        }

        // Pattern: "about four months" / "environ 4 mois" / "pendant quatre mois" / "4 months" / "6 mois"
        if (minDuration == null) {
            Pattern singlePattern = Pattern.compile("(?:about|environ|approx(?:imately)?|around|pendant|pour|durant)?\\s*(\\d+|[a-z]+)\\s*(?:months?|mois)", Pattern.CASE_INSENSITIVE);
            Matcher singleMatcher = singlePattern.matcher(lower);
            if (singleMatcher.find()) {
                Integer months = parseNumberOrWord(singleMatcher.group(1));
                if (months != null) {
                    minDuration = Math.max(1, months - 1);
                    maxDuration = months + 1;
                    durationLabel = minDuration + " to " + maxDuration + " months";
                }
            }
        }

        // Pattern: "PFE" defaults to 5 to 6 months in Moroccan universities
        if (minDuration == null && lower.contains("pfe")) {
            minDuration = 5;
            maxDuration = 6;
            durationLabel = "5 to 6 months";
        }

        // 3. Extract Domains
        Set<String> matchedDomains = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : DOMAIN_PATTERNS.entrySet()) {
            Pattern p = Pattern.compile("\\b" + entry.getKey() + "\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
            if (p.matcher(lower).find()) {
                matchedDomains.add(entry.getValue());
            }
        }
        List<String> domainsList = new ArrayList<>(matchedDomains);

        // 4. Extract Compensation
        String compensation = "Any funding";
        boolean isFundedRequested = lower.contains("funded") || lower.contains("rémunéré") || lower.contains("remunere") ||
            lower.contains("rémunération") || lower.contains("remuneration") ||
            lower.contains("paid") || lower.contains("gratification") || lower.contains("indemnité");
        if (isFundedRequested) {
            compensation = "Funded";
        }

        // 5. Build visual pills for the UI
        List<QueryPill> pills = new ArrayList<>();
        for (String d : domainsList) {
            pills.add(new QueryPill(d, "domain"));
        }
        if (detectedCity != null) {
            pills.add(new QueryPill(detectedCity, "city"));
        }
        if (durationLabel != null) {
            pills.add(new QueryPill(durationLabel, "duration"));
        }
        String compensationPillLabel = isFundedRequested ? "Funded" : "Any funding";
        pills.add(new QueryPill(compensationPillLabel, "compensation"));

        return new ParsedQueryResponse(
                query,
                detectedCity,
                minDuration,
                maxDuration,
                durationLabel,
                domainsList,
                domainsList,
                compensation,
                pills
        );
    }

    private Integer parseNumberOrWord(String token) {
        if (token == null) return null;
        token = token.trim().toLowerCase(Locale.ROOT);
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return NUMBER_WORDS.get(token);
        }
    }
}

