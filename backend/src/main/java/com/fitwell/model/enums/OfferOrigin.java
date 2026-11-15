package com.fitwell.model.enums;

public enum OfferOrigin {
    LAB_POSTED,
    SOURCED;

    public static OfferOrigin fromString(String origin) {
        if (origin == null) return null;
        try {
            return OfferOrigin.valueOf(origin.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

