package com.fitwell.service.sourcing.model;

import java.time.Instant;
import java.util.UUID;

public record ValidationItemRecord(
        UUID id,
        UUID sourceId,
        String sourceName,
        String rawPageText,
        String extractedFieldsJson,
        int confidenceScore,
        UUID duplicateOfOfferId,
        String status,
        Instant createdAt
) {}
