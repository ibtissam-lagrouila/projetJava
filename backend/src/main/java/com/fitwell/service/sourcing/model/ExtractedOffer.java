package com.fitwell.service.sourcing.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;

public record ExtractedOffer(
        String title,
        String employer,
        String location,
        int durationMonths,
        String excerpt,
        LocalDate postedDate,
        String canonicalUrl,
        String domain,
        String compensationType,
        String extractionLevel,
        int confidenceScore,
        String contentHash
) {
    public static String computeHash(String canonicalUrl, String title, String employer) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = (canonicalUrl != null ? canonicalUrl.trim().toLowerCase() : "") + "|"
                    + (title != null ? title.trim().toLowerCase() : "") + "|"
                    + (employer != null ? employer.trim().toLowerCase() : "");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return Integer.toHexString((canonicalUrl + title + employer).hashCode());
        }
    }
}
