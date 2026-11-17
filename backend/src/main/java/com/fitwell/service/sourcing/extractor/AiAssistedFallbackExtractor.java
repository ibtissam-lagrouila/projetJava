package com.fitwell.service.sourcing.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitwell.service.sourcing.DataSanitizerService;
import com.fitwell.service.sourcing.model.ExtractedOffer;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AiAssistedFallbackExtractor {

    private static final Logger log = LoggerFactory.getLogger(AiAssistedFallbackExtractor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Validates an AI structured JSON payload against the required schema.
     * Required fields:
     * - "title": non-empty String
     * - "employer": non-empty String
     * - "location": non-empty String
     * Returns true if valid, false if invalid/hallucinated.
     */
    public boolean validateAiSchema(String aiJsonOutput) {
        if (aiJsonOutput == null || aiJsonOutput.isBlank()) {
            return false;
        }

        try {
            JsonNode root = mapper.readTree(aiJsonOutput);
            if (!root.isObject() && !root.isArray()) {
                return false;
            }

            JsonNode list = root.isArray() ? root : (root.has("offers") ? root.get("offers") : null);
            if (list == null || !list.isArray() || list.isEmpty()) {
                if (root.isObject() && root.has("title")) {
                    return isValidOfferNode(root);
                }
                return false;
            }

            for (JsonNode item : list) {
                if (!isValidOfferNode(item)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidOfferNode(JsonNode node) {
        if (!node.isObject()) return false;

        JsonNode title = node.get("title");
        if (title == null || !title.isTextual() || title.asText().trim().isEmpty()) {
            return false;
        }

        JsonNode employer = node.get("employer");
        if (employer == null || !employer.isTextual() || employer.asText().trim().isEmpty()) {
            return false;
        }

        JsonNode location = node.get("location");
        if (location == null || !location.isTextual() || location.asText().trim().isEmpty()) {
            return false;
        }

        return true;
    }

    /**
     * Parses validated AI structured output into ExtractedOffer objects.
     */
    public List<ExtractedOffer> parseValidatedAiOutput(String aiJsonOutput, String pageUrl) {
        List<ExtractedOffer> results = new ArrayList<>();
        if (!validateAiSchema(aiJsonOutput)) {
            log.warn("AI extraction rejected: output failed schema validation");
            return results;
        }

        try {
            JsonNode root = mapper.readTree(aiJsonOutput);
            List<JsonNode> nodes = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(nodes::add);
            } else if (root.has("offers") && root.get("offers").isArray()) {
                root.get("offers").forEach(nodes::add);
            } else if (root.isObject()) {
                nodes.add(root);
            }

            for (JsonNode item : nodes) {
                String title = item.get("title").asText().trim();
                String employer = item.get("employer").asText().trim();
                String location = item.get("location").asText().trim();
                int duration = item.path("durationMonths").asInt(4);
                String desc = item.path("description").asText("");
                String excerpt = DataSanitizerService.sanitizeExcerpt(desc.isBlank() ? title : desc);
                String link = item.path("canonicalUrl").asText(pageUrl);
                String domain = item.path("domain").asText("General");
                String comp = item.path("compensationType").asText("Funded");

                String hash = ExtractedOffer.computeHash(link, title, employer);
                results.add(new ExtractedOffer(
                        title,
                        employer,
                        location,
                        duration,
                        excerpt,
                        LocalDate.now(),
                        link,
                        domain,
                        comp,
                        "AI_FALLBACK",
                        75, // AI confidence
                        hash
                ));
            }
        } catch (Exception e) {
            log.error("Failed to parse validated AI output: {}", e.getMessage());
        }

        return results;
    }
}
