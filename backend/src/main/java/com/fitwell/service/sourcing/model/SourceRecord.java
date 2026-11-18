package com.fitwell.service.sourcing.model;

import java.time.Instant;
import java.util.UUID;

public record SourceRecord(
        UUID id,
        String name,
        String domain,
        String accessType,
        String url,
        String cguUrl,
        String tosUrl,
        String type,
        String status,
        int accuracyPct,
        boolean isAllowed,
        Instant robotsCheckedAt,
        String robotsStatus,
        int crawlDelaySeconds,
        String cssSelectors,
        String searchUrlTemplate,
        String notes,
        boolean autoPublish,
        String failureReason,
        String alertMessage,
        Instant lastRunAt,
        Instant createdAt
) {
    // Backward-compatible constructor for 17-field callers
    public SourceRecord(
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
    ) {
        this(
                id,
                name,
                null,
                "CAREER_PAGE",
                url,
                cguUrl,
                cguUrl,
                type,
                status,
                accuracyPct,
                isAllowed,
                robotsCheckedAt,
                robotsStatus,
                crawlDelaySeconds,
                cssSelectors,
                null,
                null,
                autoPublish,
                failureReason,
                alertMessage,
                lastRunAt,
                createdAt
        );
    }
}
