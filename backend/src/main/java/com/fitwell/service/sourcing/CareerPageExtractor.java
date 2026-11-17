package com.fitwell.service.sourcing;

import com.fitwell.service.sourcing.extractor.AiAssistedFallbackExtractor;
import com.fitwell.service.sourcing.extractor.CssSelectorExtractor;
import com.fitwell.service.sourcing.extractor.JsonLdExtractor;
import com.fitwell.service.sourcing.extractor.RssSitemapExtractor;
import com.fitwell.service.sourcing.model.ExtractedOffer;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

public class CareerPageExtractor {

    private final JsonLdExtractor jsonLdExtractor = new JsonLdExtractor();
    private final RssSitemapExtractor rssSitemapExtractor = new RssSitemapExtractor();
    private final CssSelectorExtractor cssSelectorExtractor = new CssSelectorExtractor();
    private final AiAssistedFallbackExtractor aiFallbackExtractor = new AiAssistedFallbackExtractor();

    public record ExtractionOutcome(
            List<ExtractedOffer> offers,
            boolean isJavascriptOnly,
            String status,
            String message
    ) {
        public static ExtractionOutcome success(List<ExtractedOffer> offers) {
            return new ExtractionOutcome(offers, false, "SUCCESS", "Extracted " + offers.size() + " offer(s)");
        }

        public static ExtractionOutcome javascriptOnly(String message) {
            return new ExtractionOutcome(List.of(), true, "UNSUPPORTED_JAVASCRIPT", message);
        }

        public static ExtractionOutcome empty(String message) {
            return new ExtractionOutcome(List.of(), false, "NO_OFFERS_FOUND", message);
        }
    }

    /**
     * Extracts offers following the strict 4-level hierarchy:
     * 1. JSON-LD schema.org/JobPosting
     * 2. RSS / Sitemap
     * 3. CSS Selectors (Jsoup)
     * 4. AI-assisted fallback (strictly schema-validated)
     * If static HTML contains no offers because it's rendered by client-side JS, flags as unsupported (JavaScript).
     */
    public ExtractionOutcome extractFromDocument(Document doc, String pageUrl, String cssSelectorsJson, String optionalAiJson) {
        if (doc == null) {
            return ExtractionOutcome.empty("Null HTML document");
        }

        // 1. Level 1: JSON-LD JobPosting
        List<ExtractedOffer> jsonLdOffers = jsonLdExtractor.extract(doc, pageUrl);
        if (!jsonLdOffers.isEmpty()) {
            return ExtractionOutcome.success(jsonLdOffers);
        }

        // 2. Level 2: RSS / Sitemap XML
        List<ExtractedOffer> rssOffers = rssSitemapExtractor.extract(doc, pageUrl);
        if (!rssOffers.isEmpty()) {
            return ExtractionOutcome.success(rssOffers);
        }

        // 3. Level 3: CSS Selectors (if configured)
        if (cssSelectorsJson != null && !cssSelectorsJson.isBlank()) {
            List<ExtractedOffer> cssOffers = cssSelectorExtractor.extract(doc, pageUrl, cssSelectorsJson);
            if (!cssOffers.isEmpty()) {
                return ExtractionOutcome.success(cssOffers);
            }
        }

        // 4. Check for client-side JavaScript rendering (CSR / SPA)
        if (isClientSideRendered(doc)) {
            return ExtractionOutcome.javascriptOnly("Offers not present in static HTML (page rendered dynamically via JavaScript). Status set to unsupported (JavaScript).");
        }

        // 5. Level 4: AI-assisted fallback (if AI JSON provided)
        if (optionalAiJson != null && !optionalAiJson.isBlank()) {
            List<ExtractedOffer> aiOffers = aiFallbackExtractor.parseValidatedAiOutput(optionalAiJson, pageUrl);
            if (!aiOffers.isEmpty()) {
                return ExtractionOutcome.success(aiOffers);
            }
        }

        // 6. Zero invented offers rule: If no JSON-LD, RSS, CSS match, return empty
        return ExtractionOutcome.empty("No structured offers found in static HTML");
    }

    public boolean isClientSideRendered(Document doc) {
        Element body = doc.body();
        if (body == null) return true;

        String text = body.text().trim();
        String html = body.html().toLowerCase();

        // Check classic SPA root containers with empty contents
        boolean hasEmptyRoot = doc.select("#root:empty, #app:empty, #__next:empty, app-root:empty").size() > 0;
        boolean hasJsWarning = html.contains("enable javascript") || html.contains("activer javascript");

        // If body has very little visible text (< 120 chars) and contains script tags or empty SPA root
        boolean textTooSparse = text.length() < 120 && doc.select("script").size() > 0;

        return hasEmptyRoot || (hasJsWarning && text.length() < 200) || textTooSparse;
    }

    public JsonLdExtractor getJsonLdExtractor() {
        return jsonLdExtractor;
    }

    public CssSelectorExtractor getCssSelectorExtractor() {
        return cssSelectorExtractor;
    }

    public AiAssistedFallbackExtractor getAiFallbackExtractor() {
        return aiFallbackExtractor;
    }
}
