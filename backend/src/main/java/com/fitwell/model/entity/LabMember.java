package com.fitwell.model.entity;

import com.fitwell.model.enums.LabRole;

import java.time.Instant;
import java.util.UUID;

public record LabMember(
        UUID id,
        UUID userId,
        UUID labId,
        LabRole role,
        Instant createdAt
) {}

