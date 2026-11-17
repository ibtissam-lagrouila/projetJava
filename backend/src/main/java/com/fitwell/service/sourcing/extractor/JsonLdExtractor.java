package com.fitwell.service.sourcing.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitwell.service.sourcing.DataSanitizerService;
import com.fitwell.service.sourcing.model.ExtractedOffer;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonLdExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsonLdExtractor.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s*(?:mois|months|month)", Pattern.CASE_INSENSITIVE);

    public List<ExtractedOffer> extract(Document doc, String pageUrl) {
        List<ExtractedOffer> results = new ArrayList<>();
        Elements scriptTags = doc.select("script[type=application/ld+json]");

        for (Element script : scriptTags) {
            String rawJson = script.data().trim();
            if (rawJson.isBlank()) continue;

            try {
                JsonNode root = mapper.readTree(rawJson);
                findAndExtractJobPostings(root, results, pageUrl);
            } catch (Exception e) {
                log.debug("Failed to parse JSON-LD script block: {}", e.getMessage());
            }
        }

        return results;
    }

    private void findAndExtractJobPostings(JsonNode node, List<ExtractedOffer> results, String pageUrl) {
        if (node == null) return;

        if (node.isArray()) {
            for (JsonNode item : node) {
                findAndExtractJobPostings(item, results, pageUrl);
            }
            return;
        }

        if (node.isObject()) {
            JsonNode typeNode = node.get("@type");
            if (typeNode == null) typeNode = node.get("type");

            if (typeNode != null && isJobPostingType(typeNode)) {
                ExtractedOffer offer = parseJobPosting(node, pageUrl);
                if (offer != null) {
                    results.add(offer);
                }
            }

            // Check @graph array
            JsonNode graph = node.get("@graph");
            if (graph != null && graph.isArray()) {
                for (JsonNode item : graph) {
                    findAndExtractJobPostings(item, results, pageUrl);
                }
            }
        }
    }

    private boolean isJobPostingType(JsonNode typeNode) {
        if (typeNode.isTextual()) {
            String text = typeNode.asText();
            return text.equalsIgnoreCase("JobPosting") || text.endsWith("/JobPosting");
        } else if (typeNode.isArray()) {
            for (JsonNode sub : typeNode) {
                if (sub.isTextual() && (sub.asText().equalsIgnoreCase("JobPosting") || sub.asText().endsWith("/JobPosting"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private ExtractedOffer parseJobPosting(JsonNode node, String pageUrl) {
        String title = node.path("title").asText(node.path("jobTitle").asText(null));
        if (title == null || title.isBlank()) {
            return null;
        }

        String employer = "Unknown Employer";
        JsonNode org = node.path("hiringOrganization");
        if (org.isObject()) {
            employer = org.path("name").asText(employer);
        } else if (org.isTextual()) {
            employer = org.asText();
        }

        String location = "Morocco";
        JsonNode locNode = node.path("jobLocation");
        if (locNode.isObject()) {
            JsonNode addr = locNode.path("address");
            if (addr.isObject()) {
                location = addr.path("addressLocality").asText(addr.path("addressCountry").asText(location));
            } else if (addr.isTextual()) {
                location = addr.asText();
            }
        }

        LocalDate postedDate = LocalDate.now();
        String dateStr = node.path("datePosted").asText(null);
        if (dateStr != null && !dateStr.isBlank()) {
            try {
                if (dateStr.length() >= 10) {
                    postedDate = LocalDate.parse(dateStr.substring(0, 10));
                }
            } catch (Exception ignored) {}
        }

        String rawDesc = node.path("description").asText("");
        String excerpt = DataSanitizerService.sanitizeExcerpt(rawDesc);

        String canonicalUrl = node.path("url").asText(pageUrl);
        if (canonicalUrl.isBlank() || canonicalUrl.startsWith("/")) {
            canonicalUrl = pageUrl;
        }

        int durationMonths = 4;
        Matcher m = DURATION_PATTERN.matcher(title + " " + rawDesc);
        if (m.find()) {
            try {
                durationMonths = Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }

        String comp = "Funded";
        if (node.has("baseSalary")) {
            comp = "Funded";
        }

        String hash = ExtractedOffer.computeHash(canonicalUrl, title, employer);

        return new ExtractedOffer(
                title.trim(),
                employer.trim(),
                location.trim(),
                durationMonths,
                excerpt,
                postedDate,
                canonicalUrl.trim(),
                "Computer Science",
                comp,
                "JSON_LD",
                95,
                hash
        );
    }
}
