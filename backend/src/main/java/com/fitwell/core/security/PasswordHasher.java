package com.fitwell.core.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public final class PasswordHasher {

    public static final String ALGORITHM_PREFIX = "pbkdf2";
    public static final int DEFAULT_ITERATIONS = 600_000; // >= 600,000 as strictly required
    public static final int SALT_BYTES = 16;
    public static final int KEY_LENGTH_BITS = 256;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordHasher() {}

    public static String hashPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, DEFAULT_ITERATIONS, KEY_LENGTH_BITS);

        String saltB64 = Base64.getEncoder().encodeToString(salt);
        String hashB64 = Base64.getEncoder().encodeToString(hash);
        return String.format("%s$%d$%s$%s", ALGORITHM_PREFIX, DEFAULT_ITERATIONS, saltB64, hashB64);
    }

    public static boolean verifyPassword(String password, String storedHash) {
        if (password == null || storedHash == null || !storedHash.startsWith(ALGORITHM_PREFIX + "$")) {
            return false;
        }

        String[] parts = storedHash.split("\\$");
        if (parts.length != 4) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);

            byte[] computedHash = pbkdf2(password.toCharArray(), salt, iterations, expectedHash.length * 8);

            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(computedHash, expectedHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is not supported on this JVM", e);
        }
    }
}

