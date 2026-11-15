package com.fitwell.core.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class TokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TokenGenerator() {}

    /**
     * Generates a cryptographically strong 256-bit (32 bytes) random token URL-safe string.
     */
    public static String generateToken() {
        byte[] bytes = new byte[32]; // 256 bits
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Computes the SHA-256 hash of a raw token for secure database storage.
     */
    public static String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Token cannot be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not supported", e);
        }
    }
}

