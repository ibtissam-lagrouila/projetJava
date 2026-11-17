package com.fitwell.service.sourcing;

import java.util.regex.Pattern;

public final class DataSanitizerService {

    // Regex matching email addresses
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE
    );

    // Regex matching Moroccan and international phone numbers
    // e.g., +212 6 12 34 56 78, 0612345678, +1-555-123-4567, etc.
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:(?:\\+|00)212[\\s.-]?|0)[5-7][\\s.-]?(?:\\d{2}[\\s.-]?){4}|" +
            "\\b(?:\\+\\d{1,3}[-.\\s]?)?\\(?\\d{2,4}\\)?[-.\\s]?\\d{3,4}[-.\\s]?\\d{3,4}\\b"
    );

    // Regex matching HTML tags
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    /**
     * Sanitizes raw text into a safe short excerpt:
     * - Strips HTML tags
     * - Removes recruiter emails and phone numbers
     * - Truncates to max 300 characters
     */
    public static String sanitizeExcerpt(String rawText, int maxLength) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        // 1. Strip HTML tags
        String clean = HTML_TAG_PATTERN.matcher(rawText).replaceAll(" ");

        // 2. Strip emails
        clean = EMAIL_PATTERN.matcher(clean).replaceAll("[email protected]");

        // 3. Strip phone numbers
        clean = PHONE_PATTERN.matcher(clean).replaceAll("[phone protected]");

        // 4. Normalize multiple whitespaces
        clean = clean.replaceAll("\\s+", " ").trim();

        // 5. Truncate to maxLength without breaking words if possible
        if (clean.length() <= maxLength) {
            return clean;
        }

        int cutoff = clean.lastIndexOf(' ', maxLength);
        if (cutoff <= 0 || cutoff < maxLength / 2) {
            cutoff = maxLength;
        }
        return clean.substring(0, cutoff).trim() + "...";
    }

    public static String sanitizeExcerpt(String rawText) {
        return sanitizeExcerpt(rawText, 250);
    }
}
