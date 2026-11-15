package com.fitwell.model.entity;

import java.time.Instant;
import java.util.UUID;

public record Session(
        UUID id,
        UUID userId,
        String tokenHash,
        Instant expiresAt,
        Instant createdAt
) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}

