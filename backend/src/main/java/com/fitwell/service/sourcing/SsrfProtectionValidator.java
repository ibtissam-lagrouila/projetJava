package com.fitwell.service.sourcing;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

public final class SsrfProtectionValidator {

    private static final Set<String> FORBIDDEN_DOMAINS = Set.of(
            "google.com", "google.fr", "google.ma",
            "linkedin.com", "linkedin.fr"
    );

    private static boolean allowLocalhostForTesting = false;

    public static void setAllowLocalhostForTesting(boolean allow) {
        allowLocalhostForTesting = allow;
    }

    public static boolean isAllowLocalhostForTesting() {
        return allowLocalhostForTesting;
    }

    /**
     * Validates that a URL is safe from SSRF attacks and does not target forbidden scraping platforms.
     * Throws IllegalArgumentException or SecurityException if the URL is unsafe.
     */
    public static void validateSafeUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }

        URI uri;
        try {
            uri = URI.create(urlString.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL syntax: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new SecurityException("SSRF Block: Only HTTP and HTTPS protocols are allowed (got: " + scheme + ")");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL host is missing");
        }
        host = host.toLowerCase(Locale.ROOT);

        // Forbidden domains check (Google, LinkedIn)
        for (String forbidden : FORBIDDEN_DOMAINS) {
            if (host.equals(forbidden) || host.endsWith("." + forbidden)) {
                throw new SecurityException("Scraping forbidden: Accessing " + forbidden + " is strictly prohibited by platform rules.");
            }
        }

        if (allowLocalhostForTesting) {
            return;
        }

        // DNS resolution check to prevent private / loopback / cloud metadata IP access
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isPrivateOrLoopback(addr)) {
                    throw new SecurityException("SSRF Block: Host " + host + " resolves to private or loopback IP (" + addr.getHostAddress() + ")");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Host cannot be resolved: " + host);
        }
    }

    public static boolean isPrivateOrLoopback(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
            return true;
        }

        byte[] bytes = addr.getAddress();
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            // 127.0.0.0/8
            if (b0 == 127) return true;
            // 10.0.0.0/8
            if (b0 == 10) return true;
            // 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true;
            // 192.168.0.0/16
            if (b0 == 192 && b1 == 168) return true;
            // 169.254.0.0/16 (Link Local / Cloud Metadata)
            if (b0 == 169 && b1 == 254) return true;
            // 0.0.0.0/8
            if (b0 == 0) return true;
        } else if (bytes.length == 16) {
            // IPv6 unique local (fc00::/7)
            int firstByte = bytes[0] & 0xFF;
            if ((firstByte & 0xFE) == 0xFC) {
                return true;
            }
        }
        return false;
    }
}
