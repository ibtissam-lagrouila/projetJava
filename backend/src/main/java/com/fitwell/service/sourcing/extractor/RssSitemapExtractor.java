package com.fitwell.service.sourcing.extractor;

import com.fitwell.service.sourcing.DataSanitizerService;
import com.fitwell.service.sourcing.model.ExtractedOffer;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RssSitemapExtractor {

    public List<ExtractedOffer> extract(Document doc, String pageUrl) {
        List<ExtractedOffer> results = new ArrayList<>();

        // Check RSS <item> tags
        Elements items = doc.select("channel > item, item");
        if (!items.isEmpty()) {
            for (Element item : items) {
                String title = item.selectFirst("title") != null ? item.selectFirst("title").text() : null;
                String link = item.selectFirst("link") != null ? item.selectFirst("link").text() : pageUrl;
                String desc = item.selectFirst("description") != null ? item.selectFirst("description").text() : "";

                if (title != null && !title.isBlank()) {
                    String excerpt = DataSanitizerService.sanitizeExcerpt(desc);
                    String employer = "Career Portal";
                    String hash = ExtractedOffer.computeHash(link, title, employer);
                    results.add(new ExtractedOffer(
                            title.trim(),
                            employer,
                            "Morocco",
                            4,
                            excerpt,
                            LocalDate.now(),
                            link.trim(),
                            "General",
                            "Funded",
                            "RSS_SITEMAP",
                            90,
                            hash
                    ));
                }
            }
            return results;
        }

        // Check Atom <entry> tags
        Elements entries = doc.select("feed > entry, entry");
        if (!entries.isEmpty()) {
            for (Element entry : entries) {
                String title = entry.selectFirst("title") != null ? entry.selectFirst("title").text() : null;
                Element linkElem = entry.selectFirst("link");
                String link = linkElem != null ? (linkElem.hasAttr("href") ? linkElem.attr("href") : linkElem.text()) : pageUrl;
                String summary = entry.selectFirst("summary") != null ? entry.selectFirst("summary").text() : "";

                if (title != null && !title.isBlank()) {
                    String excerpt = DataSanitizerService.sanitizeExcerpt(summary);
                    String employer = "Career Portal";
                    String hash = ExtractedOffer.computeHash(link, title, employer);
                    results.add(new ExtractedOffer(
                            title.trim(),
                            employer,
                            "Morocco",
                            4,
                            excerpt,
                            LocalDate.now(),
                            link.trim(),
                            "General",
                            "Funded",
                            "RSS_SITEMAP",
                            90,
                            hash
                    ));
                }
            }
        }

        return results;
    }
}
