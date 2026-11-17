package com.fitwell.service.sourcing;

import com.fitwell.service.sourcing.model.RobotsCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RobotsTxtService {

    private static final Logger log = LoggerFactory.getLogger(RobotsTxtService.class);

    public static final String USER_AGENT = "FitwellBot/1.0 (+https://fitwell.ma/bot; bot@fitwell.ma)";
    public static final int DEFAULT_MIN_CRAWL_DELAY_SECONDS = 5;

    /**
     * Fetches robots.txt from the host of the given URL and evaluates if the target path is allowed.
     */
    public RobotsCheckResult checkUrl(String targetUrl) {
        try {
            SsrfProtectionValidator.validateSafeUrl(targetUrl);
            URI uri = URI.create(targetUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            String robotsUrl = scheme + "://" + host + (port != -1 ? ":" + port : "") + "/robots.txt";
            SsrfProtectionValidator.validateSafeUrl(robotsUrl);

            HttpURLConnection conn = (HttpURLConnection) URI.create(robotsUrl).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setInstanceFollowRedirects(false); // No automatic redirect

            int status = conn.getResponseCode();
            if (status == 404) {
                // No robots.txt found -> by convention, allowed
                return RobotsCheckResult.allow(DEFAULT_MIN_CRAWL_DELAY_SECONDS, "No robots.txt present (HTTP 404) - access allowed");
            }
            if (status == 403 || status == 429) {
                return RobotsCheckResult.disallow("Access to robots.txt refused (HTTP " + status + ")");
            }
            if (status >= 300 && status < 400) {
                // Redirect on robots.txt - verify target
                String loc = conn.getHeaderField("Location");
                if (loc != null && !loc.contains(host)) {
                    return RobotsCheckResult.disallow("Cross-domain redirect blocked on robots.txt");
                }
            }
            if (status != 200) {
                return RobotsCheckResult.disallow("Unable to read robots.txt (HTTP " + status + ")");
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                    if (sb.length() > 500_000) break; // 500KB max for robots.txt
                }
            }

            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            return evaluateRobotsTxt(sb.toString(), path);

        } catch (SecurityException se) {
            log.warn("SSRF or security violation while checking robots.txt for {}: {}", targetUrl, se.getMessage());
            return RobotsCheckResult.disallow(se.getMessage());
        } catch (Exception e) {
            log.error("Error fetching robots.txt for {}: {}", targetUrl, e.getMessage());
            return RobotsCheckResult.error("Failed to check robots.txt: " + e.getMessage());
        }
    }

    /**
     * Pure evaluator for robots.txt content against a target path.
     */
    public RobotsCheckResult evaluateRobotsTxt(String content, String targetPath) {
        if (content == null || content.isBlank()) {
            return RobotsCheckResult.allow(DEFAULT_MIN_CRAWL_DELAY_SECONDS, "Empty robots.txt - crawling allowed");
        }

        String path = (targetPath == null || targetPath.isBlank()) ? "/" : targetPath;

        String[] lines = content.split("\\r?\\n");
        boolean inFitwellSection = false;
        boolean inGlobalSection = false;

        List<String> fitwellDisallow = new ArrayList<>();
        List<String> fitwellAllow = new ArrayList<>();
        int fitwellCrawlDelay = -1;

        List<String> globalDisallow = new ArrayList<>();
        List<String> globalAllow = new ArrayList<>();
        int globalCrawlDelay = -1;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            int commentIdx = line.indexOf('#');
            if (commentIdx != -1) {
                line = line.substring(0, commentIdx).trim();
            }
            if (line.isEmpty()) continue;

            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("user-agent:")) {
                String agent = line.substring("user-agent:".length()).trim().toLowerCase(Locale.ROOT);
                if (agent.equals("fitwellbot") || agent.equals("fitwell")) {
                    inFitwellSection = true;
                    inGlobalSection = false;
                } else if (agent.equals("*")) {
                    inGlobalSection = true;
                    inFitwellSection = false;
                } else {
                    inFitwellSection = false;
                    inGlobalSection = false;
                }
            } else if (lower.startsWith("disallow:")) {
                String pattern = line.substring("disallow:".length()).trim();
                if (!pattern.isEmpty()) {
                    if (inFitwellSection) fitwellDisallow.add(pattern);
                    else if (inGlobalSection) globalDisallow.add(pattern);
                }
            } else if (lower.startsWith("allow:")) {
                String pattern = line.substring("allow:".length()).trim();
                if (!pattern.isEmpty()) {
                    if (inFitwellSection) fitwellAllow.add(pattern);
                    else if (inGlobalSection) globalAllow.add(pattern);
                }
            } else if (lower.startsWith("crawl-delay:")) {
                String delayStr = line.substring("crawl-delay:".length()).trim();
                try {
                    int delay = (int) Math.ceil(Double.parseDouble(delayStr));
                    if (inFitwellSection) fitwellCrawlDelay = delay;
                    else if (inGlobalSection) globalCrawlDelay = delay;
                } catch (NumberFormatException ignored) {}
            }
        }

        // Prefer Fitwell specific section if found, otherwise global (*) section
        List<String> disallows = !fitwellDisallow.isEmpty() || fitwellCrawlDelay != -1 ? fitwellDisallow : globalDisallow;
        List<String> allows = !fitwellDisallow.isEmpty() || fitwellCrawlDelay != -1 ? fitwellAllow : globalAllow;
        int delay = fitwellCrawlDelay != -1 ? fitwellCrawlDelay : (globalCrawlDelay != -1 ? globalCrawlDelay : DEFAULT_MIN_CRAWL_DELAY_SECONDS);
        int finalDelay = Math.max(DEFAULT_MIN_CRAWL_DELAY_SECONDS, delay);

        // Check if disallowed
        for (String disallow : disallows) {
            if (matchesRobotsRule(disallow, path)) {
                // Check if more specific Allow overrides it
                boolean overriddenByAllow = false;
                for (String allow : allows) {
                    if (matchesRobotsRule(allow, path) && allow.length() >= disallow.length()) {
                        overriddenByAllow = true;
                        break;
                    }
                }
                if (!overriddenByAllow) {
                    return RobotsCheckResult.disallow("Path " + path + " is disallowed by rule: Disallow: " + disallow);
                }
            }
        }

        return RobotsCheckResult.allow(finalDelay, "Path " + path + " is allowed. Respecting Crawl-delay: " + finalDelay + "s");
    }

    private boolean matchesRobotsRule(String rule, String path) {
        if (rule.equals("/")) return true;
        if (rule.endsWith("/")) {
            return path.startsWith(rule);
        }
        return path.startsWith(rule);
    }
}
