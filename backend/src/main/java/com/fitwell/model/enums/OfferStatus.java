package com.fitwell.model.enums;

public enum OfferStatus {
    DRAFT,
    PUBLISHED,
    CLOSED;

    public static OfferStatus fromString(String status) {
        if (status == null) return null;
        try {
            return OfferStatus.valueOf(status.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

