package com.fitwell.service.sourcing.model;

import java.time.Instant;
import java.util.UUID;

public record SourceRecord(
        UUID id,
        String name,
        String url,
        String cguUrl,
        String type,
        String status,
        int accuracyPct,
        boolean isAllowed,
        Instant robotsCheckedAt,
        String robotsStatus,
        int crawlDelaySeconds,
        String cssSelectors,
        boolean autoPublish,
        String failureReason,
        String alertMessage,
        Instant lastRunAt,
        Instant createdAt
) {}
