package com.fitwell.model.entity;

import com.fitwell.model.enums.Role;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String email,
        String passwordHash,
        String salt,
        Role role,
        Instant emailVerifiedAt,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }
}

