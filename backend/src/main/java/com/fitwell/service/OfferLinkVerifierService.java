package com.fitwell.service;

import com.fitwell.service.sourcing.RobotsTxtService;
import com.fitwell.service.sourcing.SsrfProtectionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public class OfferLinkVerifierService {

    private static final Logger log = LoggerFactory.getLogger(OfferLinkVerifierService.class);

    private static final List<String> DEAD_MARKERS = List.of(
            "offre expirée",
            "offre expiree",
            "no longer accepting applications",
            "offre pourvue",
            "poste pourvu",
            "cette offre n'est plus disponible",
            "job has expired",
            "job is closed",
            "this position has been filled",
            "the job you are looking for is no longer available"
    );

    public record LinkVerificationResult(boolean isLikelyDead, String reason) {
        public boolean isAlive() {
            return !isLikelyDead;
        }

        public boolean alive() {
            return !isLikelyDead;
        }

        public static LinkVerificationResult active() {
            return new LinkVerificationResult(false, "Active");
        }

        public static LinkVerificationResult dead(String reason) {
            return new LinkVerificationResult(true, reason);
        }
    }

    public LinkVerificationResult verify(String urlString) {
        return verifyUrl(urlString);
    }

    /**
     * Checks an external offer URL for liveness:
     * - Strict 3s timeout
     * - Only allows redirects within the same domain
     * - Checks HTTP status (404, 410)
     * - Checks body content for expiration markers
     */
    public LinkVerificationResult verifyUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return LinkVerificationResult.dead("URL is missing or blank");
        }

        try {
            SsrfProtectionValidator.validateSafeUrl(urlString);
            URI uri = URI.create(urlString);
            String host = uri.getHost();

            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", RobotsTxtService.USER_AGENT);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setConnectTimeout(3000); // 3 seconds timeout
            conn.setReadTimeout(3000);
            conn.setInstanceFollowRedirects(false); // Manually enforce same-domain redirection

            int status = conn.getResponseCode();

            // Handle 3xx redirects (restricted to same domain)
            if (status >= 300 && status < 400) {
                String location = conn.getHeaderField("Location");
                if (location != null) {
                    URI targetUri = URI.create(location);
                    if (targetUri.getHost() != null && !targetUri.getHost().equalsIgnoreCase(host)) {
                        return LinkVerificationResult.dead("Cross-domain redirect blocked (" + host + " -> " + targetUri.getHost() + ")");
                    }
                    return verifyUrl(location);
                }
            }

            if (status == 404 || status == 410) {
                return LinkVerificationResult.dead("HTTP " + status + " Dead link");
            }

            if (status >= 400) {
                return LinkVerificationResult.dead("HTTP " + status);
            }

            // Read up to 50KB to check for expiry markers
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int read;
                while ((read = reader.read(buf)) != -1) {
                    sb.append(buf, 0, read);
                    if (sb.length() > 50_000) break; // 50KB sample is sufficient
                }
            }

            String bodyLower = sb.toString().toLowerCase(Locale.ROOT);
            for (String marker : DEAD_MARKERS) {
                if (bodyLower.contains(marker)) {
                    return LinkVerificationResult.dead("Page indicates expired/filled: '" + marker + "'");
                }
            }

            return LinkVerificationResult.active();

        } catch (SecurityException se) {
            return LinkVerificationResult.dead("Security block: " + se.getMessage());
        } catch (Exception e) {
            log.debug("Link verification unreachable for {}: {}", urlString, e.getMessage());
            return LinkVerificationResult.dead("Unreachable link: " + e.getMessage());
        }
    }
}
