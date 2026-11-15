package com.fitwell.core.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    @Test
    @DisplayName("Should generate hash with pbkdf2 prefix, >=600000 iterations, salt and hash")
    void shouldGenerateCompliantHash() {
        String password = "StrongStudentPassword2026!";
        String hash = PasswordHasher.hashPassword(password);

        assertNotNull(hash);
        assertTrue(hash.startsWith("pbkdf2$"));

        String[] parts = hash.split("\\$");
        assertEquals(4, parts.length, "Hash format must be pbkdf2$iterations$salt$hash");
        assertEquals("pbkdf2", parts[0]);

        int iterations = Integer.parseInt(parts[1]);
        assertTrue(iterations >= 600_000, "Iterations must be at least 600,000");

        assertFalse(parts[2].isBlank(), "Salt must not be empty");
        assertFalse(parts[3].isBlank(), "Hash must not be empty");
    }

    @Test
    @DisplayName("Should successfully verify correct password and reject incorrect password")
    void shouldVerifyPasswordCorrectly() {
        String password = "SecretLabPassword!123";
        String hash = PasswordHasher.hashPassword(password);

        assertTrue(PasswordHasher.verifyPassword(password, hash), "Correct password must verify successfully");
        assertFalse(PasswordHasher.verifyPassword("WrongPassword!123", hash), "Wrong password must be rejected");
        assertFalse(PasswordHasher.verifyPassword("", hash), "Empty password must be rejected");
        assertFalse(PasswordHasher.verifyPassword(null, hash), "Null password must be rejected");
    }

    @Test
    @DisplayName("Should use unique cryptographic salts for identical passwords")
    void shouldGenerateUniqueSalts() {
        String password = "SamePasswordCommon123";
        String hash1 = PasswordHasher.hashPassword(password);
        String hash2 = PasswordHasher.hashPassword(password);

        assertNotEquals(hash1, hash2, "Two hashes of the same password must have different salts and outputs");
        assertTrue(PasswordHasher.verifyPassword(password, hash1));
        assertTrue(PasswordHasher.verifyPassword(password, hash2));
    }
}

