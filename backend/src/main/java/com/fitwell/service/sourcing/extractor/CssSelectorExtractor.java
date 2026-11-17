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
import java.util.ArrayList;
import java.util.List;

public class CssSelectorExtractor {

    private static final Logger log = LoggerFactory.getLogger(CssSelectorExtractor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public List<ExtractedOffer> extract(Document doc, String pageUrl, String cssSelectorsJson) {
        List<ExtractedOffer> results = new ArrayList<>();
        if (cssSelectorsJson == null || cssSelectorsJson.isBlank()) {
            return results;
        }

        try {
            JsonNode config = mapper.readTree(cssSelectorsJson);
            String itemSelector = config.path("itemSelector").asText(null);
            String titleSelector = config.path("titleSelector").asText(null);
            String employerSelector = config.path("employerSelector").asText(null);
            String locationSelector = config.path("locationSelector").asText(null);
            String linkSelector = config.path("linkSelector").asText(null);
            String descSelector = config.path("descriptionSelector").asText(null);

            if (itemSelector == null || titleSelector == null) {
                return results;
            }

            Elements items = doc.select(itemSelector);
            for (Element item : items) {
                Element titleElem = item.selectFirst(titleSelector);
                if (titleElem == null) continue;
                String title = titleElem.text().trim();
                if (title.isBlank()) continue;

                String employer = "Career Portal";
                if (employerSelector != null) {
                    Element empElem = item.selectFirst(employerSelector);
                    if (empElem != null && !empElem.text().isBlank()) {
                        employer = empElem.text().trim();
                    }
                }

                String location = "Morocco";
                if (locationSelector != null) {
                    Element locElem = item.selectFirst(locationSelector);
                    if (locElem != null && !locElem.text().isBlank()) {
                        location = locElem.text().trim();
                    }
                }

                String link = pageUrl;
                if (linkSelector != null) {
                    Element aElem = item.selectFirst(linkSelector);
                    if (aElem != null && aElem.hasAttr("href")) {
                        link = aElem.absUrl("href");
                        if (link.isBlank()) link = aElem.attr("href");
                    }
                } else if (item.tagName().equalsIgnoreCase("a") && item.hasAttr("href")) {
                    link = item.absUrl("href");
                }

                String desc = "";
                if (descSelector != null) {
                    Element descElem = item.selectFirst(descSelector);
                    if (descElem != null) {
                        desc = descElem.text();
                    }
                }

                String excerpt = DataSanitizerService.sanitizeExcerpt(desc.isBlank() ? title : desc);
                String hash = ExtractedOffer.computeHash(link, title, employer);

                results.add(new ExtractedOffer(
                        title,
                        employer,
                        location,
                        4,
                        excerpt,
                        LocalDate.now(),
                        link,
                        "Technology",
                        "Funded",
                        "CSS_SELECTORS",
                        85,
                        hash
                ));
            }

        } catch (Exception e) {
            log.warn("Failed to apply CSS selectors: {}", e.getMessage());
        }

        return results;
    }
}
